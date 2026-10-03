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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesBeatmapLibraryStorageTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parserOmissionsSurviveImportAndRestartAndAreRecomputedAfterRepair(boolean archive) throws Exception {
        Path root = tempDir.resolve("partial-library");
        String valid = "osu file format v14\n[Metadata]\nTitle:Partial\nBeatmapSetID:123\n[HitObjects]\n100,100,1000,1,0\n";
        Path source = tempDir.resolve(archive ? "partial.osz" : "partial.osu");
        if (archive) {
            try (var zip = new ZipOutputStream(Files.newOutputStream(source), StandardCharsets.UTF_8)) {
                put(zip, "nested/partial.osu", valid + "damaged\n");
                put(zip, "valid.osu", valid.replace("Title:Partial", "Title:Valid"));
            }
        } else Files.writeString(source, valid + "damaged\n");
        var imported = new BeatmapArchiveImporter(root).importFile(source);
        assertEquals(1, imported.warnings().size());
        assertTrue(imported.warnings().getFirst().contains("Skipped 1 invalid HitObject"));
        var damaged = imported.beatmapSet().difficulties().stream().filter(d -> d.title().equals("Partial")).findFirst().orElseThrow();
        assertEquals(1, damaged.hitObjects().size());
        assertEquals(1, damaged.skippedHitObjectCount());
        var storage = new PropertiesBeatmapLibraryStorage(root);
        storage.save(imported.beatmapSet());
        var restored = new PropertiesBeatmapLibraryStorage(root).load().getFirst().difficulties().stream()
                .filter(d -> d.title().equals("Partial")).findFirst().orElseThrow();
        assertEquals(1, restored.skippedHitObjectCount());
        var calculator = new dev.osujava.difficulty.StandardDifficultyCalculator();
        assertEquals(dev.osujava.difficulty.DifficultyResult.Status.FAILED, calculator.calculate(restored).status());
        Files.writeString(restored.beatmapPath(), valid);
        var repaired = new PropertiesBeatmapLibraryStorage(root).load().getFirst().difficulties().stream()
                .filter(d -> d.title().equals("Partial")).findFirst().orElseThrow();
        assertEquals(0, repaired.skippedHitObjectCount());
        assertEquals(dev.osujava.difficulty.DifficultyResult.Status.SUCCESS, calculator.calculate(repaired).status());
        assertNotEquals(restored.playData().sha256(), repaired.playData().sha256());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sourceIssuesSurviveImportRestartAndRepairWithoutChangingLibrarySchema(boolean archive) throws Exception {
        Path root=tempDir.resolve("source-quality-library");
        String valid="osu file format v14\n[Metadata]\nTitle:SourceQuality\nBeatmapSetID:124\n[Difficulty]\nCircleSize:5\n[TimingPoints]\n0,500\n[HitObjects]\n256,192,1000,8,0,1500\n";
        String damaged=valid.replace("CircleSize:5","CircleSize:broken").replace("0,500","short\n0,500").replace("8,0,1500","8,0,500");
        Path source=tempDir.resolve(archive?"source.osz":"source.osu");
        if(archive) try(var zip=new ZipOutputStream(Files.newOutputStream(source),StandardCharsets.UTF_8)) {put(zip,"nested/source.osu",damaged);}
        else Files.writeString(source,damaged);
        var imported=new BeatmapArchiveImporter(root).importFile(source);
        assertEquals(1,imported.warnings().size());assertTrue(imported.warnings().getFirst().contains("Source issues"));
        var expected=new dev.osujava.beatmap.BeatmapParseIssues(1,1,1);
        assertEquals(expected,imported.beatmapSet().difficulties().getFirst().parseIssues());
        new PropertiesBeatmapLibraryStorage(root).save(imported.beatmapSet());
        var restored=new PropertiesBeatmapLibraryStorage(root).load().getFirst().difficulties().getFirst();
        assertEquals(expected,restored.parseIssues());
        assertEquals(dev.osujava.difficulty.DifficultyResult.Status.FAILED,new dev.osujava.difficulty.StandardDifficultyCalculator().calculate(restored).status());
        Files.writeString(restored.beatmapPath(),valid);
        var repaired=new PropertiesBeatmapLibraryStorage(root).load().getFirst().difficulties().getFirst();
        assertEquals(dev.osujava.beatmap.BeatmapParseIssues.NONE,repaired.parseIssues());
        assertNotEquals(restored.playData().sha256(),repaired.playData().sha256());
        assertEquals(dev.osujava.difficulty.DifficultyResult.Status.SUCCESS,new dev.osujava.difficulty.StandardDifficultyCalculator().calculate(repaired).status());
    }
    @TempDir
    Path tempDir;

    @Test
    void savesAndRestoresSetDifficultiesModesAndAssetPaths() throws Exception {
        Path libraryRoot = tempDir.resolve("library");
        BeatmapSet imported = importFixture(libraryRoot);
        PropertiesBeatmapLibraryStorage storage = new PropertiesBeatmapLibraryStorage(libraryRoot);
        storage.save(imported);

        BeatmapSet restored = storage.load().getFirst();
        assertEquals(12345, restored.difficulties().getFirst().previewTimeMs());

        for (int i = 0; i < imported.difficulties().size(); i++) {
            var original = imported.difficulties().get(i);
            var loaded = restored.difficulties().get(i);
            assertEquals(original.playData(), loaded.playData());
            assertEquals(original.metadata(), loaded.metadata());
            assertEquals(original.timingStatistics(), loaded.timingStatistics());
        }

        assertEquals(imported.id(), restored.id());
        assertEquals("Stored song", restored.title());
        assertEquals("Performer", restored.artist());
        assertEquals("Mapper", restored.creator());
        assertEquals(2, restored.difficulties().size());
        assertEquals(List.of("Easy", "Hard"), restored.difficulties().stream().map(d -> d.version()).toList());
        assertEquals(List.of(0, 3), restored.difficulties().stream().map(d -> d.mode()).toList());
        assertEquals("Easy.osu", restored.difficulties().getFirst().beatmapPath().getFileName().toString());
        assertEquals("Hard.osu", restored.difficulties().get(1).beatmapPath().getFileName().toString());
        assertTrue(Files.isRegularFile(restored.difficulties().getFirst().beatmapPath()));
        assertTrue(Files.isRegularFile(restored.audioPath()));
        assertTrue(Files.isRegularFile(restored.backgroundPath()));
        assertEquals(restored.audioPath(), restored.difficulties().getFirst().audioPath());
        assertEquals(restored.backgroundPath(), restored.difficulties().getFirst().backgroundPath());
        assertTrue(restored.assets().stream().anyMatch(path -> path.getFileName().toString().equals("Easy.osu")));
    }

    @Test
    void backgroundlessImportedMapRemainsInLibraryAfterRestart() throws Exception {
        Path root = tempDir.resolve("library");
        var library = new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root));
        var imported = new BeatmapArchiveImporter(root).importFile(writeFixtureArchive(false)).beatmapSet();
        library.add(imported);
        assertNull(imported.backgroundPath());

        var reopened = new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root));
        assertEquals(1, reopened.size());
        var restored = reopened.all().getFirst();
        assertEquals(2, restored.difficulties().size());
        assertNull(restored.backgroundPath());
        for (var difficulty : restored.difficulties()) {
            assertEquals("", difficulty.backgroundFilename());
            assertNull(difficulty.backgroundPath());
            assertTrue(Files.isRegularFile(difficulty.beatmapPath()));
        }
    }

    @Test
    void reimportingSetUpdatesItsIndexInsteadOfAddingAnotherEntry() throws Exception {
        Path libraryRoot = tempDir.resolve("library");
        Path archive = writeFixtureArchive();
        BeatmapLibrary library = new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(libraryRoot));
        BeatmapSet first = new BeatmapArchiveImporter(libraryRoot).importFile(archive).beatmapSet();
        library.add(first);

        BeatmapSet importedAgain = new BeatmapArchiveImporter(libraryRoot).importFile(archive).beatmapSet();
        library.add(importedAgain);

        assertEquals(first.id(), importedAgain.id());
        assertEquals(1, library.size());
        assertEquals(1, new PropertiesBeatmapLibraryStorage(libraryRoot).load().size());
    }

    @Test
    void skipsOneDamagedIndexEntryAndKeepsOtherSetsAvailable() throws Exception {
        Path libraryRoot = tempDir.resolve("library");
        BeatmapSet imported = importFixture(libraryRoot);
        PropertiesBeatmapLibraryStorage storage = new PropertiesBeatmapLibraryStorage(libraryRoot);
        storage.save(imported);
        Path indexDirectory = Files.createDirectories(libraryRoot.resolve("index"));
        Files.writeString(indexDirectory.resolve("damaged.properties"), "schemaVersion=broken\nid=damaged\n");

        BeatmapLibrary library = new BeatmapLibrary(storage);

        assertEquals(1, library.size());
        assertEquals(imported.id(), library.all().getFirst().id());
    }

    @Test
    void missingAssetReferencesBecomeNullAndMissingDifficultyIsSkipped() throws Exception {
        Path libraryRoot = tempDir.resolve("library");
        BeatmapSet imported = importFixture(libraryRoot);
        PropertiesBeatmapLibraryStorage storage = new PropertiesBeatmapLibraryStorage(libraryRoot);
        storage.save(imported);
        Files.delete(imported.audioPath());
        Files.delete(imported.backgroundPath());
        Files.delete(imported.difficulties().getFirst().beatmapPath());

        BeatmapSet restored = storage.load().getFirst();
        assertEquals(12345, restored.difficulties().getFirst().previewTimeMs());

        assertNull(restored.audioPath());
        assertNull(restored.backgroundPath());
        assertEquals(1, restored.difficulties().size());
        assertEquals("Hard", restored.difficulties().getFirst().version());
        assertNull(restored.difficulties().getFirst().audioPath());
        assertNull(restored.difficulties().getFirst().backgroundPath());
        assertTrue(Files.exists(libraryRoot.resolve("index").resolve(imported.id() + ".properties")));
    }

    @Test
    void indexesBeatmapsLeftInLegacyUuidStorageOnFirstLoad() throws Exception {
        Path libraryRoot = tempDir.resolve("library");
        BeatmapSet imported = importFixture(libraryRoot);
        Path legacyDirectory = libraryRoot.resolve("4e0ca041-f300-4449-a433-2a63e32a3efc");
        Files.move(imported.difficulties().getFirst().beatmapPath().getParent(), legacyDirectory);
        PropertiesBeatmapLibraryStorage storage = new PropertiesBeatmapLibraryStorage(libraryRoot);

        List<BeatmapSet> recovered = storage.load();
        List<BeatmapSet> loadedAgain = storage.load();

        assertEquals(1, recovered.size());
        assertEquals(imported.id(), recovered.getFirst().id());
        assertEquals(2, recovered.getFirst().difficulties().size());
        assertTrue(recovered.getFirst().difficulties().getFirst().beatmapPath().startsWith(legacyDirectory));
        assertEquals(1, loadedAgain.size());
        assertEquals(imported.id(), loadedAgain.getFirst().id());
    }

    private BeatmapSet importFixture(Path libraryRoot) throws Exception {
        return new BeatmapArchiveImporter(libraryRoot).importFile(writeFixtureArchive()).beatmapSet();
    }

    private Path writeFixtureArchive() throws IOException { return writeFixtureArchive(true); }

    private Path writeFixtureArchive(boolean background) throws IOException {
        Path archive = tempDir.resolve("stored-set.osz");
        String common = """
                osu file format v14
                [General]
                AudioFilename: song.ogg
                PreviewTime: 12345
                Mode: %d
                [Metadata]
                Title: Stored song
                Artist: Performer
                Creator: Mapper
                Version: %s
                BeatmapSetID: 5210
                [Difficulty]
                CircleSize: 4
                OverallDifficulty: 5
                ApproachRate: 6
                [Events]
                0,0,"background.png",0,0
                [TimingPoints]
                0,500,4,1,0,80,1,0
                [HitObjects]
                256,192,1000,1,0
                """;
        if (!background) common = common.replace("0,0,\"background.png\",0,0\n", "");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            put(zip, "Easy.osu", common.formatted(0, "Easy"));
            put(zip, "Hard.osu", common.formatted(3, "Hard"));
            put(zip, "song.ogg", "audio data");
            if (background) put(zip, "background.png", "background data");
        }
        return archive;
    }

    private void put(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
