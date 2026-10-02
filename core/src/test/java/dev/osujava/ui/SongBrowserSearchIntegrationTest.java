package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.library.BeatmapArchiveImporter;
import dev.osujava.library.PropertiesBeatmapLibraryStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserSearchIntegrationTest {
    @TempDir Path temporary;

    private BeatmapSet imported() throws Exception {
        Path archive = temporary.resolve("search.osz");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            for (int i=0; i<3; i++) {
                String version = List.of("Easy", "Hard", "Insane").get(i);
                String text = """
                        osu file format v14
                        [General]
                        AudioFilename: song.ogg
                        Mode: %d
                        [Metadata]
                        Title: Evening Sky
                        TitleUnicode: 夜空
                        Artist: Hoshino
                        ArtistUnicode: 星野
                        Creator: Mapper
                        Version: %s
                        Source: Moon Game
                        Tags: %s piano
                        BeatmapID: %d
                        BeatmapSetID: 456
                        [Difficulty]
                        ApproachRate: %d
                        [Events]
                        2,5000,15000
                        [TimingPoints]
                        0,%s,4,0,0,100,1,0
                        [HitObjects]
                        256,192,1000,1,0
                        256,192,%d,1,0
                        """.formatted(i == 2 ? 3 : 0, version, i==0 ? "calm" : "fast", 123+i, i==0 ? 5 : 9,
                                Double.toString(60000.0 / (120+60*i)),66999+60000*i);
                zip.putNextEntry(new ZipEntry(version+".osu")); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("song.ogg")); zip.write(new byte[]{0}); zip.closeEntry();
        }
        return new BeatmapArchiveImporter(temporary.resolve("library")).importFile(archive).beatmapSet();
    }
    private PropertiesBeatmapLibraryStorage storage() { return new PropertiesBeatmapLibraryStorage(temporary.resolve("library")); }
    private List<String> matches(SongBrowserModel browser) {
        return browser.rows().stream().filter(r -> !r.group() && !r.excluded).map(r -> r.difficulty.version()).toList();
    }
    private Properties properties(BeatmapSet set) throws Exception {
        var result = new Properties();
        try (var input = Files.newInputStream(index(set))) { result.load(input); }
        return result;
    }
    private Path index(BeatmapSet set) { return temporary.resolve("library/index/"+set.id()+".properties"); }

    @Test void importPersistenceAndBrowserRetainPerDifficultyMetadataAndIds() throws Exception {
        var set = imported(); storage().save(set);
        assertEquals("3",properties(set).getProperty("schemaVersion"));
        var restored = storage().load().getFirst();
        assertEquals(set.difficulties().stream().map(d -> d.metadata()).toList(),
                restored.difficulties().stream().map(d -> d.metadata()).toList());
        var browser = new SongBrowserModel(List.of(restored));
        for (String query : List.of("moon piano 夜空 星野", "title=evening", "456")) {
            browser.search(query); assertEquals(3,matches(browser).size(),query);
        }
        browser.search("124"); assertEquals(List.of("Hard"),matches(browser));
        browser.search("calm fast"); assertTrue(matches(browser).isEmpty(), "Terms may not match different difficulties in the same Set");
        browser.search("difficulty=easy ar>=9"); assertTrue(matches(browser).isEmpty());
        browser.search("fast ar>=9 difficulty!=insane"); assertEquals(List.of("Hard"),matches(browser));
        assertEquals(1,restored.difficulties().indexOf(browser.selectedDifficulty()), "Gameplay keeps the original difficulty index");
    }

    @Test void versionOneIndexRecoversMetadataFromOsuWithoutReimportOrLoadTimeRewrite() throws Exception {
        var set = imported(); storage().save(set);
        var properties = properties(set); properties.setProperty("schemaVersion", "1");
        for (int i=0;i<3;i++) for (String key : List.of("titleUnicode","artistUnicode","source","tags","beatmapId","beatmapSetId"))
            properties.remove("difficulty."+i+"."+key);
        try (var output = Files.newOutputStream(index(set))) { properties.store(output,"v1 fixture"); }
        byte[] before = Files.readAllBytes(index(set));
        var restored = storage().load().getFirst();
        assertArrayEquals(before,Files.readAllBytes(index(set)));
        assertEquals(set.difficulties().stream().map(d -> d.timingStatistics()).toList(),
                restored.difficulties().stream().map(d -> d.timingStatistics()).toList());
        assertEquals(set.difficulties().stream().map(d -> d.metadata()).toList(),restored.difficulties().stream().map(d -> d.metadata()).toList());
        var browser = new SongBrowserModel(List.of(restored)); browser.search("夜空 fast 125");
        assertEquals(List.of("Insane"),matches(browser));
        storage().save(restored); assertEquals("3",properties(set).getProperty("schemaVersion"));
    }

    @Test void persistedTimelineFieldsFilterDifficultiesAndRepairSelectionWithoutIndexRewrite() throws Exception {
        var set = imported(); storage().save(set);
        byte[] before = Files.readAllBytes(index(set));
        var restored = storage().load().getFirst();
        assertArrayEquals(before,Files.readAllBytes(index(set)));
        assertEquals(set.difficulties().stream().map(d -> d.timingStatistics()).toList(),
                restored.difficulties().stream().map(d -> d.timingStatistics()).toList());
        var browser = new SongBrowserModel(List.of(restored));
        browser.group(SongBrowserModel.Group.ARTIST);
        browser.search("bpm=180 length>=126 drain=115 mode=o");
        assertEquals(List.of("Hard"),matches(browser));
        assertEquals(1,restored.difficulties().indexOf(browser.selectedDifficulty()));
        assertEquals(1,browser.rows().stream().filter(r -> r.group()).mapToInt(r -> r.matchingChildren).sum());
        browser.search("bpm=120 length>100");
        assertTrue(matches(browser).isEmpty(), "Numeric terms must match the same difficulty");
        assertNull(browser.selectedDifficulty());
        browser.search("mode=m drain=175");
        assertEquals(List.of("Insane"),matches(browser));
        assertEquals(2,restored.difficulties().indexOf(browser.selectedDifficulty()));
        browser.search("");
        assertEquals(3,matches(browser).size());
        assertEquals("Easy",browser.selectedDifficulty().version());
    }

    @Test void versionTwoPersistsMetadataAndIgnoresFutureUnsupportedSchemas() throws Exception {
        var set = imported(); storage().save(set);
        for (var difficulty : set.difficulties()) {
            Path path = difficulty.beatmapPath();
            Files.writeString(path, Files.readString(path).replace("Tags:","IgnoredTags:").replace("TitleUnicode:","IgnoredTitleUnicode:"));
        }
        var restored = storage().load().getFirst();
        assertEquals(set.difficulties().stream().map(d -> d.metadata()).toList(),restored.difficulties().stream().map(d -> d.metadata()).toList());
        var properties = properties(set); properties.setProperty("schemaVersion", "999");
        try (var output = Files.newOutputStream(index(set))) { properties.store(output,"future fixture"); }
        assertTrue(storage().load().isEmpty());
    }

    @Test void filteringRebuildsRepresentativesSingletonStateAndGroupCountsWithoutReplacingRows() throws Exception {
        var first = imported();
        var second = new BeatmapSet("second","Other","Other","Mapper",null,null,first.difficulties(),List.of());
        var browser = new SongBrowserModel(List.of(first,second)); browser.selectSet(second.id());
        var easy = browser.rows().stream().filter(r -> r.set==first && r.difficulty.version().equals("Easy")).findFirst().orElseThrow();
        var hard = browser.rows().stream().filter(r -> r.set==first && r.difficulty.version().equals("Hard")).findFirst().orElseThrow();
        var insane = browser.rows().stream().filter(r -> r.set==first && r.difficulty.version().equals("Insane")).findFirst().orElseThrow();
        browser.search("fast");
        assertSame(hard,browser.row(hard.key)); assertSame(hard,insane.representative);
        assertTrue(easy.excluded); assertNull(easy.representative);
        assertEquals(SongBrowserModel.RowState.COLLAPSED,hard.state);
        assertEquals(SongBrowserModel.RowState.HIDDEN,insane.state);
        browser.search("difficulty=hard"); assertEquals(SongBrowserModel.RowState.SINGLETON,hard.state);
        browser.group(SongBrowserModel.Group.ARTIST);
        assertEquals(List.of(2),browser.rows().stream().filter(r -> r.group()).map(r -> r.matchingChildren).toList());
        browser.selectSet(first.id()); assertSame(first.difficulties().get(1),browser.selectedDifficulty());
        browser.select(first.id(),0); assertSame(first.difficulties().get(1),browser.selectedDifficulty(),"Excluded rows cannot be selected");
        browser.moveSet(1); assertEquals(second.id(),browser.selectedSet().id());
        assertEquals("Hard",browser.selectedDifficulty().version());
        browser.search(""); assertSame(easy,browser.row(easy.key)); assertSame(easy,hard.representative);
        assertEquals(List.of(6),browser.rows().stream().filter(r -> r.group()).map(r -> r.matchingChildren).toList());
    }

    @Test void randomAndHistoryNeverSelectAnExcludedDifficultyAndClearRestoresSelection() throws Exception {
        var first = imported();
        var second = new BeatmapSet("second","Other","Other","Mapper",null,null,first.difficulties(),List.of());
        var browser = new SongBrowserModel(List.of(first,second),new Random(1)); browser.select(first.id(),0);
        var original = browser.selection(); browser.search("difficulty=hard");
        assertEquals("Hard",browser.selectedDifficulty().version());
        browser.search(""); assertEquals(original,browser.selection());
        browser.random(); assertEquals(second.id(),browser.selectedSet().id());
        browser.search("difficulty=hard"); browser.previousRandom();
        assertEquals(second.id(),browser.selectedSet().id(),"Filtered history entries remain pending");
        browser.random(); assertEquals(first.id(),browser.selectedSet().id());
        assertEquals("Hard",browser.selectedDifficulty().version());
        browser.previousRandom(); assertEquals(second.id(),browser.selectedSet().id());
        assertEquals("Hard",browser.selectedDifficulty().version());
    }
}
