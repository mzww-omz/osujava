package dev.osujava.library;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapFile;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.beatmap.parse.BeatmapParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BeatmapArchiveImporter {
    private static final long MAX_ARCHIVE_BYTES = 1_073_741_824L;

    private final Path libraryRoot;
    private final BeatmapFileParser parser;

    public BeatmapArchiveImporter(Path libraryRoot) {
        this(libraryRoot, new BeatmapFileParser());
    }

    public BeatmapArchiveImporter(Path libraryRoot, BeatmapFileParser parser) {
        this.libraryRoot = libraryRoot.toAbsolutePath().normalize();
        this.parser = parser;
    }

    public ImportResult importFile(Path source) throws BeatmapImportException {
        Path absoluteSource = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluteSource)) {
            throw new BeatmapImportException("File does not exist: " + source);
        }
        String name = absoluteSource.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".osz") && !name.endsWith(".osu")) {
            throw new BeatmapImportException("Choose an .osz or .osu file");
        }

        Path staging = null;
        try {
            Files.createDirectories(libraryRoot);
            staging = Files.createTempDirectory(libraryRoot, ".import-");
            List<Path> osuFiles = name.endsWith(".osz") ? extractArchive(absoluteSource, staging) : importStandalone(absoluteSource, staging);
            if (osuFiles.isEmpty()) {
                throw new BeatmapImportException("The archive does not contain any .osu files");
            }
            return assemble(osuFiles, staging);
        } catch (BeatmapImportException e) {
            deleteTreeQuietly(staging);
            throw e;
        } catch (IOException | RuntimeException e) {
            deleteTreeQuietly(staging);
            throw new BeatmapImportException("Could not import " + source.getFileName() + ": " + safeMessage(e), e);
        }
    }

    private List<Path> extractArchive(Path archive, Path staging) throws IOException, BeatmapImportException {
        List<Path> osuFiles = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        long extractedBytes = 0;
        try (InputStream fileInput = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(fileInput, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                Path relative = validatedEntryPath(entry.getName());
                if (!seen.add(relative)) {
                    throw new BeatmapImportException("Duplicate archive path: " + entry.getName());
                }
                Path destination = staging.resolve(relative).normalize();
                if (!destination.startsWith(staging)) {
                    throw new BeatmapImportException("Unsafe archive path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    try (OutputStream output = Files.newOutputStream(destination)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            extractedBytes += count;
                            if (extractedBytes > MAX_ARCHIVE_BYTES) {
                                throw new BeatmapImportException("Archive expands beyond the 1 GiB import limit");
                            }
                            output.write(buffer, 0, count);
                        }
                    }
                    if (relative.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osu")) {
                        osuFiles.add(destination);
                    }
                }
                zip.closeEntry();
            }
        } catch (BeatmapImportException e) {
            throw e;
        } catch (IOException e) {
            throw new BeatmapImportException("Invalid or damaged .osz archive: " + safeMessage(e), e);
        }
        osuFiles.sort(Comparator.comparing(path -> staging.relativize(path).toString()));
        return osuFiles;
    }

    private Path validatedEntryPath(String entryName) throws BeatmapImportException {
        if (entryName == null || entryName.isBlank() || entryName.indexOf('\0') >= 0 || entryName.contains("\\")
                || entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) {
            throw new BeatmapImportException("Unsafe archive path: " + entryName);
        }
        Path path;
        try {
            path = Path.of(entryName).normalize();
        } catch (RuntimeException e) {
            throw new BeatmapImportException("Invalid archive path: " + entryName, e);
        }
        if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")) {
            throw new BeatmapImportException("Unsafe archive path: " + entryName);
        }
        for (Path component : Path.of(entryName)) {
            if (component.toString().equals("..")) {
                throw new BeatmapImportException("Unsafe archive path: " + entryName);
            }
        }
        return path;
    }

    private List<Path> importStandalone(Path source, Path staging) throws IOException, BeatmapImportException {
        BeatmapFile file;
        try {
            file = parser.parse(source);
        } catch (BeatmapParseException e) {
            throw new BeatmapImportException("Could not parse .osu file: " + safeMessage(e), e);
        }
        Path osuCopy = staging.resolve(source.getFileName().toString());
        Files.copy(source, osuCopy, StandardCopyOption.REPLACE_EXISTING);
        Path sourceRoot = source.getParent();
        copyReferencedAsset(sourceRoot, staging, file.difficulty().audioFilename());
        copyReferencedAsset(sourceRoot, staging, file.difficulty().backgroundFilename());
        return List.of(osuCopy);
    }

    private void copyReferencedAsset(Path sourceRoot, Path staging, String reference) throws IOException {
        Path relative = safeAssetReference(reference);
        if (relative == null) return;
        Path from = sourceRoot.resolve(relative).normalize();
        if (!from.startsWith(sourceRoot) || !Files.isRegularFile(from)) return;
        Path to = staging.resolve(relative).normalize();
        if (!to.startsWith(staging)) return;
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private ImportResult assemble(List<Path> osuFiles, Path staging) throws BeatmapImportException, IOException {
        List<ParsedEntry> parsed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path osuFile : osuFiles) {
            try {
                BeatmapFile beatmap = parser.parse(osuFile);
                parsed.add(new ParsedEntry(beatmap,
                        safeExistingAsset(staging, beatmap.difficulty().audioFilename()),
                        safeExistingAsset(staging, beatmap.difficulty().backgroundFilename())));
            } catch (IOException | BeatmapParseException e) {
                warnings.add("Skipped " + staging.relativize(osuFile) + ": " + safeMessage(e));
            }
        }
        if (parsed.isEmpty()) {
            throw new BeatmapImportException("No valid .osu difficulties were found");
        }

        String id = UUID.randomUUID().toString();
        Path destination = libraryRoot.resolve(id);
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(staging, destination);
        }

        List<BeatmapDifficulty> difficulties = new ArrayList<>();
        for (ParsedEntry item : parsed) {
            BeatmapDifficulty difficulty = item.file().difficulty();
            difficulties.add(difficulty.withAssets(
                    item.audioRelative() == null ? null : destination.resolve(item.audioRelative()),
                    item.backgroundRelative() == null ? null : destination.resolve(item.backgroundRelative())));
        }
        List<Path> assets;
        try (var paths = Files.walk(destination)) {
            assets = paths.filter(Files::isRegularFile).sorted().toList();
        }
        Path audio = difficulties.stream().map(BeatmapDifficulty::audioPath).filter(path -> path != null).findFirst().orElse(null);
        Path background = difficulties.stream().map(BeatmapDifficulty::backgroundPath).filter(path -> path != null).findFirst().orElse(null);
        BeatmapFile first = parsed.getFirst().file();
        BeatmapSet set = new BeatmapSet(id, first.displayTitle(), first.displayArtist(), first.creator(), audio,
                background, difficulties, assets);
        return new ImportResult(set, warnings);
    }

    private Path safeExistingAsset(Path root, String reference) {
        Path relative = safeAssetReference(reference);
        if (relative == null) return null;
        Path candidate = root.resolve(relative).normalize();
        return candidate.startsWith(root) && Files.isRegularFile(candidate) ? relative : null;
    }

    private Path safeAssetReference(String reference) {
        if (reference == null || reference.isBlank() || reference.indexOf('\0') >= 0) return null;
        String portable = reference.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) return null;
        try {
            Path relative = Path.of(portable).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) return null;
            for (Path component : Path.of(portable)) {
                if (component.toString().equals("..")) return null;
            }
            return relative;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup after a failed import.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup after a failed import.
        }
    }

    private record ParsedEntry(BeatmapFile file, Path audioRelative, Path backgroundRelative) {
    }
}
