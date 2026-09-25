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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.HashSet;
import java.util.Set;

public final class PropertiesBeatmapLibraryStorage implements BeatmapLibraryStorage {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_DIFFICULTIES = 512;
    private static final int MAX_ASSETS = 100_000;
    private static final String LEGACY_MIGRATION_MARKER = ".legacy-recovery-complete";

    private final Path libraryRoot;
    private final Path indexDirectory;
    private final BeatmapFileParser parser;

    public PropertiesBeatmapLibraryStorage(Path libraryRoot) {
        this(libraryRoot, new BeatmapFileParser());
    }

    public PropertiesBeatmapLibraryStorage(Path libraryRoot, BeatmapFileParser parser) {
        this.libraryRoot = libraryRoot.toAbsolutePath().normalize();
        this.indexDirectory = this.libraryRoot.resolve("index");
        this.parser = parser;
    }

    @Override
    public List<BeatmapSet> load() throws IOException {
        List<Path> entries;
        if (Files.isDirectory(indexDirectory)) {
            try (var paths = Files.list(indexDirectory)) {
                entries = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".properties"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        } else {
            entries = List.of();
        }

        List<BeatmapSet> sets = new ArrayList<>();
        for (Path entry : entries) {
            try {
                BeatmapSet set = loadEntry(entry);
                if (set != null) sets.add(set);
            } catch (IOException | RuntimeException e) {
                System.err.println("Skipping damaged beatmap library entry " + entry.getFileName() + ": " + safeMessage(e));
            }
        }
        recoverLegacySets(sets);
        return List.copyOf(sets);
    }

    private void recoverLegacySets(List<BeatmapSet> sets) {
        if (Files.exists(indexDirectory.resolve(LEGACY_MIGRATION_MARKER))) return;
        Set<String> knownIds = new HashSet<>();
        sets.forEach(set -> knownIds.add(set.id()));
        List<Path> directories;
        try {
            if (!Files.isDirectory(libraryRoot)) directories = List.of();
            else try (var children = Files.list(libraryRoot)) {
                directories = children.filter(Files::isDirectory)
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> path.getFileName().toString().matches(
                                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        } catch (IOException e) {
            System.err.println("Could not scan old local beatmap folders: " + safeMessage(e));
            return;
        }

        boolean complete = true;
        for (Path directory : directories) {
            try {
                BeatmapSet recovered = recoverLegacyDirectory(directory);
                if (recovered == null || knownIds.contains(recovered.id())) continue;
                save(recovered);
                sets.add(recovered);
                knownIds.add(recovered.id());
            } catch (IOException | RuntimeException e) {
                complete = false;
                System.err.println("Could not recover old beatmap folder " + directory.getFileName()
                        + ": " + safeMessage(e));
            }
        }
        if (complete) {
            try {
                Files.createDirectories(indexDirectory);
                Files.writeString(indexDirectory.resolve(LEGACY_MIGRATION_MARKER), "1\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                System.err.println("Could not save old beatmap recovery marker: " + safeMessage(e));
            }
        }
    }

    private BeatmapSet recoverLegacyDirectory(Path directory) throws IOException {
        List<Path> osuFiles;
        try (var paths = Files.walk(directory)) {
            osuFiles = paths.filter(path -> !Files.isSymbolicLink(path))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".osu"))
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                    .limit(MAX_DIFFICULTIES + 1L)
                    .toList();
        }
        if (osuFiles.isEmpty() || osuFiles.size() > MAX_DIFFICULTIES) return null;

        List<LegacyDifficulty> parsed = new ArrayList<>();
        for (Path osuFile : osuFiles) {
            try {
                BeatmapFile file = parser.parse(osuFile);
                parsed.add(new LegacyDifficulty(file, osuFile,
                        resolveLegacyAsset(directory, osuFile.getParent(), file.difficulty().audioFilename()),
                        resolveLegacyAsset(directory, osuFile.getParent(), file.difficulty().backgroundFilename())));
            } catch (BeatmapParseException | IOException e) {
                System.err.println("Skipping old .osu file " + directory.relativize(osuFile)
                        + ": " + safeMessage(e));
            }
        }
        if (parsed.isEmpty()) return null;

        List<BeatmapDifficulty> difficulties = parsed.stream()
                .map(item -> item.file().difficulty().withAssets(item.audioPath(), item.backgroundPath(), item.osuPath()))
                .toList();
        List<Path> assets;
        try (var paths = Files.walk(directory)) {
            assets = paths.filter(path -> !Files.isSymbolicLink(path))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
        BeatmapFile first = parsed.getFirst().file();
        Path audio = difficulties.stream().map(BeatmapDifficulty::audioPath).filter(path -> path != null).findFirst().orElse(null);
        Path background = difficulties.stream().map(BeatmapDifficulty::backgroundPath).filter(path -> path != null).findFirst().orElse(null);
        return new BeatmapSet(BeatmapSetIdentity.from(parsed.stream().map(LegacyDifficulty::file).toList()),
                first.displayTitle(), first.displayArtist(), first.creator(), audio, background, difficulties, assets);
    }

    private Path resolveLegacyAsset(Path storageRoot, Path relativeBase, String reference) {
        if (reference == null || reference.isBlank() || reference.indexOf('\0') >= 0) return null;
        String portable = reference.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) return null;
        try {
            Path relative = Path.of(portable).normalize();
            if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) return null;
            Path candidate = relativeBase.resolve(relative).normalize();
            if (!candidate.startsWith(storageRoot) || !Files.isRegularFile(candidate)) return null;
            Path realRoot = storageRoot.toRealPath();
            if (!candidate.toRealPath().startsWith(realRoot)) return null;
            return candidate;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public void save(BeatmapSet beatmapSet) throws IOException {
        if (!beatmapSet.id().matches("[A-Za-z0-9_-]+")) {
            throw new IOException("Invalid beatmap set id: " + beatmapSet.id());
        }
        Files.createDirectories(indexDirectory);
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", Integer.toString(SCHEMA_VERSION));
        properties.setProperty("id", beatmapSet.id());
        properties.setProperty("title", beatmapSet.title());
        properties.setProperty("artist", beatmapSet.artist());
        properties.setProperty("creator", beatmapSet.creator());
        properties.setProperty("audioPath", encodePath(beatmapSet.audioPath()));
        properties.setProperty("backgroundPath", encodePath(beatmapSet.backgroundPath()));
        properties.setProperty("asset.count", Integer.toString(beatmapSet.assets().size()));
        for (int i = 0; i < beatmapSet.assets().size(); i++) {
            properties.setProperty("asset." + i, encodePath(beatmapSet.assets().get(i)));
        }
        properties.setProperty("difficulty.count", Integer.toString(beatmapSet.difficulties().size()));
        for (int i = 0; i < beatmapSet.difficulties().size(); i++) {
            BeatmapDifficulty difficulty = beatmapSet.difficulties().get(i);
            String prefix = "difficulty." + i + ".";
            properties.setProperty(prefix + "title", difficulty.title());
            properties.setProperty(prefix + "artist", difficulty.artist());
            properties.setProperty(prefix + "creator", difficulty.creator());
            properties.setProperty(prefix + "version", difficulty.version());
            properties.setProperty(prefix + "mode", Integer.toString(difficulty.mode()));
            properties.setProperty(prefix + "audioFilename", difficulty.audioFilename());
            properties.setProperty(prefix + "backgroundFilename", difficulty.backgroundFilename());
            properties.setProperty(prefix + "osuPath", encodePath(difficulty.beatmapPath()));
            properties.setProperty(prefix + "audioPath", encodePath(difficulty.audioPath()));
            properties.setProperty(prefix + "backgroundPath", encodePath(difficulty.backgroundPath()));
        }

        Path temporary = Files.createTempFile(indexDirectory, "." + beatmapSet.id() + "-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "osu!java local beatmap library");
            }
            moveReplacing(temporary, indexDirectory.resolve(beatmapSet.id() + ".properties"));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private BeatmapSet loadEntry(Path entryPath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(entryPath)) {
            properties.load(input);
        }

        if (integer(properties, "schemaVersion", -1) != SCHEMA_VERSION) {
            throw new IOException("Unsupported or missing index schema version");
        }
        String id = required(properties, "id");
        if (!id.matches("[A-Za-z0-9_-]+") || !entryPath.getFileName().toString().equals(id + ".properties")) {
            throw new IOException("Invalid set id or index filename");
        }
        String title = required(properties, "title");
        String artist = required(properties, "artist");
        String creator = required(properties, "creator");
        int difficultyCount = boundedCount(properties, "difficulty.count", MAX_DIFFICULTIES);
        List<BeatmapDifficulty> difficulties = new ArrayList<>();
        for (int i = 0; i < difficultyCount; i++) {
            try {
                BeatmapDifficulty difficulty = loadDifficulty(properties, i);
                if (difficulty != null) difficulties.add(difficulty);
            } catch (IOException | RuntimeException e) {
                System.err.println("Skipping damaged difficulty " + i + " in " + entryPath.getFileName()
                        + ": " + safeMessage(e));
            }
        }
        if (difficulties.isEmpty()) return null;

        List<Path> assets = new ArrayList<>();
        int assetCount = boundedCount(properties, "asset.count", MAX_ASSETS);
        for (int i = 0; i < assetCount; i++) {
            Path asset = resolveStoredPath(properties.getProperty("asset." + i));
            if (asset != null) assets.add(asset);
        }
        Path audio = resolveStoredPath(properties.getProperty("audioPath"));
        Path background = resolveStoredPath(properties.getProperty("backgroundPath"));
        if (audio == null) audio = firstPath(difficulties, true);
        if (background == null) background = firstPath(difficulties, false);
        return new BeatmapSet(id, title, artist, creator, audio, background, difficulties, assets);
    }

    private BeatmapDifficulty loadDifficulty(Properties properties, int index) throws IOException {
        String prefix = "difficulty." + index + ".";
        Path beatmapPath = resolveStoredPath(required(properties, prefix + "osuPath"));
        if (beatmapPath == null) throw new IOException(".osu file is missing or outside local storage");

        BeatmapFile parsed;
        try {
            parsed = parser.parse(beatmapPath);
        } catch (BeatmapParseException e) {
            throw new IOException("Could not parse stored .osu file: " + safeMessage(e), e);
        }
        BeatmapDifficulty chart = parsed.difficulty();
        return new BeatmapDifficulty(
                required(properties, prefix + "title"),
                required(properties, prefix + "artist"),
                required(properties, prefix + "creator"),
                required(properties, prefix + "version"),
                requiredInteger(properties, prefix + "mode"),
                required(properties, prefix + "audioFilename"),
                required(properties, prefix + "backgroundFilename"),
                chart.settings(), chart.timingPoints(), chart.hitObjects(),
                resolveStoredPath(properties.getProperty(prefix + "audioPath")),
                resolveStoredPath(properties.getProperty(prefix + "backgroundPath")),
                beatmapPath);
    }

    private Path firstPath(List<BeatmapDifficulty> difficulties, boolean audio) {
        return difficulties.stream()
                .map(difficulty -> audio ? difficulty.audioPath() : difficulty.backgroundPath())
                .filter(path -> path != null)
                .findFirst()
                .orElse(null);
    }

    private int boundedCount(Properties properties, String key, int maximum) throws IOException {
        int count = integer(properties, key, -1);
        if (count < 0 || count > maximum) throw new IOException("Invalid count for " + key);
        return count;
    }

    private int requiredInteger(Properties properties, String key) throws IOException {
        String value = required(properties, key);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid integer for " + key, e);
        }
    }

    private int integer(Properties properties, String key, int fallback) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            if (fallback == Integer.MIN_VALUE) throw new IOException("Invalid integer for " + key, e);
            return fallback;
        }
    }

    private String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing " + key);
        return value;
    }

    private String encodePath(Path path) throws IOException {
        if (path == null) return "";
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(libraryRoot)) throw new IOException("Asset path is outside local beatmap storage");
        Path relative = libraryRoot.relativize(absolute);
        if (relative.getNameCount() == 0 || relative.startsWith("..")) {
            throw new IOException("Invalid asset path");
        }
        return relative.toString();
    }

    private Path resolveStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank() || storedPath.contains("\\")
                || storedPath.matches("^[A-Za-z]:.*")) return null;
        try {
            Path relative = Path.of(storedPath).normalize();
            if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) return null;
            Path candidate = libraryRoot.resolve(relative).normalize();
            if (!candidate.startsWith(libraryRoot) || !Files.isRegularFile(candidate)) return null;
            Path realRoot = libraryRoot.toRealPath();
            Path realFile = candidate.toRealPath();
            return realFile.startsWith(realRoot) ? realFile : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private record LegacyDifficulty(BeatmapFile file, Path osuPath, Path audioPath, Path backgroundPath) {
    }
}
