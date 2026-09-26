package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatmapArchiveImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void importsMultipleDifficultiesAndAssociatesSharedAssets() throws Exception {
        Path archive = tempDir.resolve("曲 集.osz");
        writeZip(archive, List.of(
                entry("Easy.osu", beatmap("Easy", 0, "audio.ogg", "images/背景.png")),
                entry("Hard.osu", beatmap("Hard", 3, "audio.ogg", "images/背景.png")),
                entry("audio.ogg", "audio bytes"),
                entry("images/背景.png", "image bytes"),
                entry("skin/extra.txt", "extra")));

        ImportResult imported = new BeatmapArchiveImporter(tempDir.resolve("library")).importFile(archive);
        BeatmapSet set = imported.beatmapSet();

        assertEquals("Song title", set.title());
        assertEquals("Artist", set.artist());
        assertEquals(2, set.difficulties().size());
        assertEquals(List.of("Easy", "Hard"), set.difficulties().stream().map(d -> d.version()).toList());
        assertEquals(0, set.difficulties().getFirst().mode());
        assertEquals(3, set.difficulties().get(1).mode());
        assertNotNull(set.audioPath());
        assertTrue(Files.exists(set.audioPath()));
        assertEquals(set.audioPath(), set.difficulties().get(0).audioPath());
        assertEquals(set.audioPath(), set.difficulties().get(1).audioPath());
        assertNotNull(set.backgroundPath());
        assertTrue(Files.exists(set.backgroundPath()));
        assertTrue(set.assets().stream().anyMatch(path -> path.toString().endsWith("extra.txt")));
        assertTrue(imported.warnings().isEmpty());
    }

    @Test
    void importsStandaloneOsuAndCopiesReferencedFiles() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("my maps/夜"));
        Path osu = sourceDir.resolve("single.osu");
        Files.writeString(osu, beatmap("Solo", 0, "audio folder/song.ogg", "background.png"));
        Files.createDirectories(sourceDir.resolve("audio folder"));
        Files.writeString(sourceDir.resolve("audio folder/song.ogg"), "audio");
        Files.writeString(sourceDir.resolve("background.png"), "background");

        BeatmapSet set = new BeatmapArchiveImporter(tempDir.resolve("library")).importFile(osu).beatmapSet();

        assertEquals(1, set.difficulties().size());
        assertTrue(Files.exists(set.audioPath()));
        assertTrue(Files.exists(set.backgroundPath()));
        assertTrue(set.audioPath().startsWith(tempDir.resolve("library").toAbsolutePath()));
    }

    @Test
    void resolvesAssetsRelativeToOsuFilesInsideArchiveSubdirectories() throws Exception {
        Path archive = tempDir.resolve("nested.osz");
        writeZip(archive, List.of(
                entry("Charts/Hard.osu", beatmap("Nested", 0, "song.ogg", "art/bg.png")),
                entry("Charts/song.ogg", "audio"),
                entry("Charts/art/bg.png", "background")));

        BeatmapSet set = new BeatmapArchiveImporter(tempDir.resolve("library")).importFile(archive).beatmapSet();

        assertTrue(set.audioPath().toString().contains("Charts"));
        assertTrue(set.backgroundPath().toString().contains("Charts"));
        assertTrue(Files.exists(set.audioPath()));
        assertTrue(Files.exists(set.backgroundPath()));
    }

    @Test
    void rejectsZipSlipPathsAndRemovesStagingFiles() throws Exception {
        Path archive = tempDir.resolve("unsafe.osz");
        writeZip(archive, List.of(entry("../outside.osu", beatmap("Bad", 0, "", ""))));
        Path libraryRoot = tempDir.resolve("library");

        assertThrows(BeatmapImportException.class,
                () -> new BeatmapArchiveImporter(libraryRoot).importFile(archive));
        assertFalse(Files.exists(tempDir.resolve("outside.osu")));
        try (var entries = Files.list(libraryRoot)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    @Test
    void reportsBrokenAndEmptyArchivesWithoutThrowingRuntimeErrors() throws Exception {
        Path broken = tempDir.resolve("broken.osz");
        Files.write(broken, "not a zip".getBytes(StandardCharsets.UTF_8));
        assertThrows(BeatmapImportException.class,
                () -> new BeatmapArchiveImporter(tempDir.resolve("broken-library")).importFile(broken));

        Path empty = tempDir.resolve("empty.osz");
        writeZip(empty, List.of(entry("readme.txt", "no charts")));
        assertThrows(BeatmapImportException.class,
                () -> new BeatmapArchiveImporter(tempDir.resolve("empty-library")).importFile(empty));
    }

    @Test
    void skipsOneMalformedDifficultyWhenAnotherIsUsable() throws Exception {
        Path archive = tempDir.resolve("partial.osz");
        writeZip(archive, List.of(
                entry("good.osu", beatmap("Good", 0, "", "")),
                entry("bad.osu", "[General]\nMode:0\n")));

        ImportResult result = new BeatmapArchiveImporter(tempDir.resolve("library")).importFile(archive);
        assertEquals(1, result.beatmapSet().difficulties().size());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void reimportingTheSameSetReplacesItsStorageWithoutCreatingAnotherDirectory() throws Exception {
        Path archive = tempDir.resolve("same-set.osz");
        String firstChart = beatmap("Normal", 0, "", "")
                .replace("Creator: Mapper", "Creator: Mapper\nBeatmapSetID: 2468");
        writeZip(archive, List.of(entry("chart.osu", firstChart)));
        Path libraryRoot = tempDir.resolve("library");
        BeatmapArchiveImporter importer = new BeatmapArchiveImporter(libraryRoot);

        ImportResult first = importer.importFile(archive);
        String updatedChart = firstChart.replace("256,192,1000,1,0", "256,192,1000,1,0\n128,192,1500,1,0");
        writeZip(archive, List.of(entry("chart.osu", updatedChart)));
        ImportResult second = importer.importFile(archive);

        assertEquals("osu-set-2468", first.beatmapSet().id());
        assertEquals(first.beatmapSet().id(), second.beatmapSet().id());
        assertEquals(2, second.beatmapSet().difficulties().getFirst().hitObjects().size());
        assertTrue(Files.exists(second.beatmapSet().difficulties().getFirst().beatmapPath()));
        try (var entries = Files.list(libraryRoot)) {
            assertEquals(List.of(second.beatmapSet().id()), entries.map(path -> path.getFileName().toString()).toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/absolute.osu", "C:/outside.osu", "C:\\outside.osu", "..\\outside.osu", "nested/../outside.osu"})
    void sharedExtractorStillRejectsDangerousBeatmapPaths(String name) throws Exception {
        Path archive = tempDir.resolve("unsafe.osz");
        writeZip(archive, List.of(entry("good.osu", beatmap("Good", 0, "", "")), entry(name, "bad")));
        Path root = tempDir.resolve("library");
        assertThrows(BeatmapImportException.class, () -> new BeatmapArchiveImporter(root).importFile(archive));
        try (var entries = Files.list(root)) { assertTrue(entries.findAny().isEmpty()); }
        assertFalse(Files.exists(tempDir.resolve("outside.osu")));
    }

    @Test
    void rejectsDuplicateNormalizedPathsAndMissingCentralDirectory() throws Exception {
        Path duplicate = tempDir.resolve("duplicate.osz");
        writeZip(duplicate, List.of(entry("good.osu", beatmap("Good", 0, "", "")), entry("./good.osu", "bad")));
        Path root = tempDir.resolve("library");
        assertThrows(BeatmapImportException.class, () -> new BeatmapArchiveImporter(root).importFile(duplicate));
        Path truncated = tempDir.resolve("truncated.osz");
        writeZip(truncated, List.of(entry("good.osu", beatmap("Good", 0, "", ""))));
        byte[] bytes = Files.readAllBytes(truncated);
        Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length - 22));
        assertThrows(BeatmapImportException.class, () -> new BeatmapArchiveImporter(root).importFile(truncated));
        try (var entries = Files.list(root)) { assertTrue(entries.findAny().isEmpty()); }
    }

    private String beatmap(String version, int mode, String audio, String background) {
        String event = background.isBlank() ? "" : "0,0,\"" + background + "\",0,0\n";
        return """
                osu file format v14
                [General]
                AudioFilename: %s
                Mode: %d
                [Metadata]
                Title: Song title
                Artist: Artist
                Creator: Mapper
                Version: %s
                [Difficulty]
                CircleSize: 4
                OverallDifficulty: 5
                ApproachRate: 6
                [Events]
                %s[TimingPoints]
                [HitObjects]
                256,192,1000,1,0
                """.formatted(audio, mode, version, event);
    }

    private ZipContent entry(String name, String value) {
        return new ZipContent(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private void writeZip(Path output, List<ZipContent> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            for (ZipContent entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.bytes());
                zip.closeEntry();
            }
        }
    }

    private record ZipContent(String name, byte[] bytes) {
    }
}
