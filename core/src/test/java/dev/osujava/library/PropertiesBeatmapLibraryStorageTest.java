package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesBeatmapLibraryStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndRestoresSetDifficultiesModesAndAssetPaths() throws Exception {
        Path libraryRoot = tempDir.resolve("library");
        BeatmapSet imported = importFixture(libraryRoot);
        PropertiesBeatmapLibraryStorage storage = new PropertiesBeatmapLibraryStorage(libraryRoot);
        storage.save(imported);

        BeatmapSet restored = storage.load().getFirst();

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

    private Path writeFixtureArchive() throws IOException {
        Path archive = tempDir.resolve("stored-set.osz");
        String common = """
                osu file format v14
                [General]
                AudioFilename: song.ogg
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
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            put(zip, "Easy.osu", common.formatted(0, "Easy"));
            put(zip, "Hard.osu", common.formatted(3, "Hard"));
            put(zip, "song.ogg", "audio data");
            put(zip, "background.png", "background data");
        }
        return archive;
    }

    private void put(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
