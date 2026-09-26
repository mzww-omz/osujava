package dev.osujava.library;

import dev.osujava.archive.SafeArchiveExtractor;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapFile;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.beatmap.parse.BeatmapParseException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
        try {
            return SafeArchiveExtractor.extract(archive, staging, MAX_ARCHIVE_BYTES, 10_000).stream()
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osu"))
                    .sorted(Comparator.comparing(path -> staging.relativize(path).toString()))
                    .toList();
        } catch (IOException e) {
            throw new BeatmapImportException("Invalid or damaged .osz archive: " + safeMessage(e), e);
        }
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
                parsed.add(new ParsedEntry(beatmap, staging.relativize(osuFile),
                        safeExistingAsset(staging, osuFile.getParent(), beatmap.difficulty().audioFilename()),
                        safeExistingAsset(staging, osuFile.getParent(), beatmap.difficulty().backgroundFilename())));
            } catch (IOException | BeatmapParseException e) {
                warnings.add("Skipped " + staging.relativize(osuFile) + ": " + safeMessage(e));
            }
        }
        if (parsed.isEmpty()) {
            throw new BeatmapImportException("No valid .osu difficulties were found");
        }

        String id = stableSetId(parsed);
        Path destination = libraryRoot.resolve(id);
        replaceStorage(staging, destination, id);

        List<BeatmapDifficulty> difficulties = new ArrayList<>();
        for (ParsedEntry item : parsed) {
            BeatmapDifficulty difficulty = item.file().difficulty();
            difficulties.add(difficulty.withAssets(
                    item.audioRelative() == null ? null : destination.resolve(item.audioRelative()),
                    item.backgroundRelative() == null ? null : destination.resolve(item.backgroundRelative()),
                    destination.resolve(item.beatmapRelative())));
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

    private String stableSetId(List<ParsedEntry> parsed) {
        return BeatmapSetIdentity.from(parsed.stream().map(ParsedEntry::file).toList());
    }

    private void replaceStorage(Path staging, Path destination, String id) throws IOException {
        Path backup = null;
        if (Files.exists(destination)) {
            backup = libraryRoot.resolve(".replace-" + id + "-" + System.nanoTime());
            moveDirectory(destination, backup);
        }
        try {
            moveDirectory(staging, destination);
        } catch (IOException e) {
            if (backup != null && Files.exists(backup)) {
                try {
                    moveDirectory(backup, destination);
                } catch (IOException restoreError) {
                    e.addSuppressed(restoreError);
                }
            }
            throw e;
        }
        deleteTreeQuietly(backup);
    }

    private void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
        }
    }

    private Path safeExistingAsset(Path storageRoot, Path relativeBase, String reference) {
        Path relative = safeAssetReference(reference);
        if (relative == null) return null;
        Path candidate = relativeBase.resolve(relative).normalize();
        return candidate.startsWith(storageRoot) && Files.isRegularFile(candidate)
                ? storageRoot.relativize(candidate) : null;
    }

    private Path safeAssetReference(String reference) {
        if (reference == null || reference.isBlank() || reference.indexOf('\0') >= 0) return null;
        String portable = reference.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) return null;
        try {
            Path relative = Path.of(portable).normalize();
            if (relative.isAbsolute()) return null;
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
        SafeArchiveExtractor.deleteTreeQuietly(root);
    }

    private record ParsedEntry(BeatmapFile file, Path beatmapRelative, Path audioRelative, Path backgroundRelative) {
    }
}
