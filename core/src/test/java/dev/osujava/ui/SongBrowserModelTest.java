package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserModelTest {
    static BeatmapSet set(String id, String title, String artist, String creator, double bpm, long length) {
        var diff = new BeatmapDifficulty(title, artist, creator, "Extra 星", 0, "", "", DifficultySettings.defaults(),
                bpm > 0 ? List.of(new TimingPoint(0,60000 / bpm,4,0,0,100,true,0)) : List.of(),
                length >= 0 ? List.of(new HitObject(0,0,1000,HitObject.Type.CIRCLE,1,0),
                        new HitObject(0,0,1000+length,HitObject.Type.CIRCLE,1,0)) : List.of(), null,null, Path.of(id,"extra.osu"));
        return new BeatmapSet(id,title,artist,creator,null,null,List.of(diff),List.of());
    }
    private List<BeatmapSet> fixture() {
        return List.of(set("a","Zulu","Alpha","Beta",220,300000), set("b","alpha","Zulu","Alpha",100,90000),
                set("c","Beta","Beta","Zulu",150,180000));
    }
    private List<String> ids(SongBrowserModel model) { return model.visibleSets().stream().map(BeatmapSet::id).toList(); }
    @ParameterizedTest @CsvSource({"TITLE,b,c,a", "ARTIST,a,c,b", "CREATOR,b,a,c", "BPM,b,c,a", "LENGTH,b,c,a"})
    void sorts(SongBrowserModel.Sort sort, String first, String second, String third) {
        var model = new SongBrowserModel(fixture()); model.sort(sort);
        assertEquals(List.of(first,second,third),ids(model));
    }
    @ParameterizedTest @EnumSource(SongBrowserModel.Sort.class)
    void tieBreakingIsIndependentOfInputOrder(SongBrowserModel.Sort sort) {
        List<BeatmapSet> input = new ArrayList<>(List.of(set("z","Alpha","Artist","Mapper",120,60000),
                set("a","ALPHA","ARTIST","MAPPER",120,60000),set("b","alpha","Artist","Mapper",120,60000)));
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(input,new Random(i)); var model = new SongBrowserModel(input); model.sort(sort);
            assertEquals(List.of("a","b","z"),ids(model));
        }
    }
    @Test void titleTieBreaksArtistThenCreatorThenIdentity() {
        var model = new SongBrowserModel(List.of(set("z","X","B","A",120,1000),set("a","x","A","B",120,1000),set("b","X","A","A",120,1000)));
        assertEquals(List.of("b","a","z"),ids(model));
    }
    @Test void numericSortUsesAllDifficultiesAndUnknownsLast() {
        var low = set("a","A","Artist","Mapper",90,60000);
        var high = set("b","B","Artist","Mapper",240,180000);
        var multi = new BeatmapSet("multi","Multi","Artist","Mapper",null,null,
                List.of(low.difficulties().getFirst(),high.difficulties().getFirst()),List.of());
        var model = new SongBrowserModel(List.of(multi, low, set("unknown","Unknown","","",0,-1)));
        model.sort(SongBrowserModel.Sort.BPM); assertEquals(List.of("a","multi","unknown"),ids(model));
        model.sort(SongBrowserModel.Sort.LENGTH); assertEquals(List.of("a","multi","unknown"),ids(model));
        model.select("multi",0); model.sort(SongBrowserModel.Sort.BPM); assertEquals(List.of("a","multi","unknown"),ids(model));
    }
    @ParameterizedTest @EnumSource(SongBrowserModel.Group.class)
    void groupEntriesHaveHeadersAndPreserveAllSets(SongBrowserModel.Group group) {
        var model = new SongBrowserModel(fixture()); model.select("c",0); model.group(group);
        long headers = model.entries().stream().filter(e -> e.kind() == SongBrowserModel.Kind.GROUP_HEADER).count();
        assertEquals(group == SongBrowserModel.Group.NONE ? 0 : 3,headers);
        assertEquals(3, model.rows().stream().filter(r -> !r.group()).count());
        assertEquals(group == SongBrowserModel.Group.NONE ? 3 : 1,
                model.entries().stream().filter(e -> e.set() != null).count());
        assertTrue(model.entries().stream().filter(e -> e.kind() == SongBrowserModel.Kind.GROUP_HEADER)
                .allMatch(e -> e.set() == null && e.difficulty() == null && !e.label().isBlank()));
    }
    @Test void unicodeInitialsNumbersSymbolsAndBlankMetadataAreSafe() {
        var model = new SongBrowserModel(List.of(set("a","A","夜の星","",0,-1),set("b","B","夜明け","",0,-1),
                set("c","C","123","",0,-1),set("d","D","✦","",0,-1),set("e","E"," ","",0,-1),
                set("f","F","𠮷野","",0,-1)));
        model.group(SongBrowserModel.Group.ARTIST);
        assertEquals(List.of("0–9","夜","𠮷","Symbols","Unknown"),model.entries().stream()
                .filter(e -> e.kind() == SongBrowserModel.Kind.GROUP_HEADER).map(SongBrowserModel.Entry::label).toList());
        model.group(SongBrowserModel.Group.CREATOR);
        assertEquals("Unknown",model.entries().getFirst().label());
    }
    @Test void bpmAndLengthBoundaryBucketsDoNotMakeExactValueGroups() {
        var model = new SongBrowserModel(List.of(set("a","A","","",99,119999),set("b","B","","",100,120000),
                set("c","C","","",149,239999),set("d","D","","",300,600000),set("e","E","","",0,-1)));
        model.group(SongBrowserModel.Group.BPM);
        assertEquals(List.of("50–<100 BPM","100–<150 BPM","300+ BPM","Unknown BPM"),headers(model));
        model.group(SongBrowserModel.Group.LENGTH);
        assertEquals(List.of("Under 2 minutes","2–<4 minutes","10+ minutes","Unknown length"),headers(model));
    }
    private List<String> headers(SongBrowserModel m) { return m.entries().stream().filter(e -> e.kind() == SongBrowserModel.Kind.GROUP_HEADER).map(SongBrowserModel.Entry::label).toList(); }
    @ParameterizedTest @ValueSource(strings={"Zulu", "alpha extra", "ALPHA EXTRA", "Beta Zulu 星", "  Beta\u3000Zulu  ", "", " \t "})
    void metadataTokensMatchAcrossFields(String query) {
        var model = new SongBrowserModel(fixture()); model.search(query); assertFalse(model.visibleSets().isEmpty());
    }
    @ParameterizedTest @CsvSource({"Sun,a", "Camellia,a", "MapperOne,a", "Extra,a", "camellia sun mapperone extra,a", "星,a"})
    void searchEachFieldAndMultipleTokens(String query, String expected) {
        var model = new SongBrowserModel(List.of(set("a","Sun","Camellia","MapperOne",120,60000),
                new BeatmapSet("b","Moon","Artist","Mapper",null,null,List.of(new BeatmapDifficulty("Moon","Artist","Mapper","Normal",0,"","",null,List.of(),List.of(),null,null)),List.of())));
        model.search(query); assertEquals(List.of(expected),ids(model));
    }
    @Test void searchNormalizesCanonicalUnicode() {
        var model = new SongBrowserModel(List.of(set("a","Café 夜空","星の旅人","Mapper",120,60000)));
        model.search("CAFE\u0301 夜空 星の旅人"); assertEquals(1,model.visibleSets().size());
    }
    @Test void noResultsExposeNoPlayableSelectionAndNoEmptyHeaders() {
        var model = new SongBrowserModel(fixture()); model.group(SongBrowserModel.Group.ARTIST); model.search("not present");
        assertTrue(model.entries().isEmpty()); assertNull(model.selectedSet()); assertNull(model.selectedDifficulty());
        model.search(""); assertNotNull(model.selectedDifficulty());
    }
    @Test void emptyLibraryIsSafeAndModelRejectsNoDifficultyAtLibraryBoundary() {
        var model = new SongBrowserModel(List.of()); model.random(); model.previousRandom(); model.search("x"); model.group(SongBrowserModel.Group.BPM);
        assertTrue(model.entries().isEmpty()); assertNull(model.selectedSet());
        assertThrows(IllegalArgumentException.class,() -> new BeatmapSet("empty",null,null,null,null,null,List.of(),List.of()));
    }
    @ParameterizedTest @EnumSource(SongBrowserModel.Sort.class)
    void sortKeepsSelectionAndExpandedSet(SongBrowserModel.Sort mode) {
        var model = new SongBrowserModel(fixture()); model.select("a",0); var selected = model.selection(); model.sort(mode);
        assertEquals(selected,model.selection()); assertEquals("a",model.selectedSet().id());
        assertEquals(1,model.rows().stream().filter(r -> r.state == SongBrowserModel.RowState.SELECTED).count());
    }
    @ParameterizedTest @EnumSource(SongBrowserModel.Group.class)
    void groupingKeepsSelection(SongBrowserModel.Group mode) {
        var model = new SongBrowserModel(fixture()); model.select("a",0); var selected = model.selection(); model.group(mode); assertEquals(selected,model.selection());
    }
    @Test void searchPreservesSelectionFallsBackAndRestoresOnClear() {
        var model = new SongBrowserModel(fixture()); model.select("c",0); var original = model.selection();
        model.search("extra"); assertEquals(original,model.selection());
        model.search("Alpha"); assertEquals("b",model.selectedSet().id());
        model.search(""); assertEquals(original,model.selection());
    }
    @Test void filteredOutSelectionRestoresOnClearAcrossSortAndGroup() {
        var model = new SongBrowserModel(fixture()); model.select("c",0); var original = model.selection();
        model.search("alpha"); assertEquals("b",model.selectedSet().id());
        model.sort(SongBrowserModel.Sort.BPM); model.group(SongBrowserModel.Group.CREATOR);
        model.search(""); assertEquals(original,model.selection());
    }
    @Test void libraryRefreshPreservesQuerySortGroupAndDifficultyPathAcrossReorder() {
        var base = fixture().getFirst(); var first = base.difficulties().getFirst();
        var second = new BeatmapDifficulty(first.title(),first.artist(),first.creator(),"Hard",0,"","",null,List.of(),List.of(),null,null,Path.of("a/hard.osu"));
        var model = new SongBrowserModel(List.of(new BeatmapSet("a",base.title(),base.artist(),base.creator(),null,null,List.of(first,second),List.of())));
        model.select("a",1); var original = model.selection(); model.search("Zulu"); model.sort(SongBrowserModel.Sort.BPM); model.group(SongBrowserModel.Group.LENGTH);
        model.library(List.of(new BeatmapSet("a",base.title(),base.artist(),base.creator(),null,null,List.of(second,first),List.of()),fixture().get(1)));
        assertEquals(original,model.selection()); assertSame(second,model.selectedDifficulty());
        assertEquals("Zulu",model.search()); assertEquals(SongBrowserModel.Sort.BPM,model.sort()); assertEquals(SongBrowserModel.Group.LENGTH,model.group());
    }
    @Test void removedSelectionFallsBackAndIdleDoesNotRebuild() {
        var model = new SongBrowserModel(fixture()); model.select("a",0); model.library(List.of(fixture().get(1)));
        assertEquals("b",model.selectedSet().id()); var entries = model.entries();
        for (int i=0;i<1000;i++) { model.selectedSet(); model.selectedDifficulty(); model.search(""); model.sort(model.sort()); model.group(model.group()); assertSame(entries,model.entries()); }
    }
    @Test void explicitChoiceInsideSearchRemainsSelectedWhenQueryIsCleared() {
        var model = new SongBrowserModel(fixture()); model.select("c",0); model.search("Alpha");
        model.select("a",0); var chosen = model.selection(); model.search(""); assertEquals(chosen,model.selection());
    }

    @Test void relativeNavigationUsesFilteredOrderAndSkipsGroupHeaders() {
        var model = new SongBrowserModel(fixture());
        model.group(SongBrowserModel.Group.ARTIST);
        model.select("a", 0);
        model.moveDifficulty(1);
        assertEquals("c", model.selectedSet().id());
        model.moveDifficulty(-1);
        assertEquals("a", model.selectedSet().id());
        model.search("Zulu Alpha");
        model.moveSet(1);
        assertEquals("b", model.selectedSet().id());
        model.moveSet(1);
        assertEquals("b", model.selectedSet().id());
        model.search("no matches");
        model.moveDifficulty(1); model.moveSet(-1);
        assertNull(model.selectedDifficulty());
        model.search("");
        assertNotNull(model.selectedDifficulty());
    }

    @Test void relativeNavigationCrossesSetBoundaryAtLastOrFirstDifficulty() {
        var first = fixture().getFirst();
        var extra = fixture().get(1).difficulties().getFirst();
        var multiple = new BeatmapSet(first.id(), first.title(), first.artist(), first.creator(),
                null, null, List.of(first.difficulties().getFirst(), extra), List.of());
        var model = new SongBrowserModel(List.of(multiple, fixture().get(2)));
        model.select("a", 1);
        model.moveDifficulty(1);
        assertSame(extra, model.selectedDifficulty()); // Last set, no wrapping.
        model.moveDifficulty(-1);
        assertSame(first.difficulties().getFirst(), model.selectedDifficulty());
        model.moveDifficulty(-1);
        assertEquals("c", model.selectedSet().id());
        model.moveDifficulty(1);
        assertEquals("a", model.selectedSet().id());
        assertSame(first.difficulties().getFirst(), model.selectedDifficulty());
    }

    @Test void pagingCountsVisibleRowsOnceAndNeverExpandsIntermediateSets() {
        var sets = new ArrayList<BeatmapSet>();
        for (int i = 0; i < 20; i++) sets.add(set("set" + i, String.format("Song %02d", i), "Artist", "Mapper", 120, 1000));
        var model = new SongBrowserModel(sets);
        model.group(SongBrowserModel.Group.ARTIST);
        model.movePage(1);
        assertEquals("set10", model.selectedSet().id());
        model.movePage(-1);
        assertEquals("set0", model.selectedSet().id());
        model.movePage(-1);
        assertEquals("set10", model.selectedSet().id());
        model.search("Song 00");
        var selected = model.selection();
        model.movePage(1); assertEquals(selected, model.selection());
        model.search("no match"); model.movePage(1); assertNull(model.selectedSet());
    }

}
