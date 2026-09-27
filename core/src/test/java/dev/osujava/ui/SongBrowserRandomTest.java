package dev.osujava.ui;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserRandomTest {
    private SongBrowserModel model() {
        return new SongBrowserModel(List.of(SongBrowserModelTest.set("a","A","Artist","Mapper",120,60000),
                SongBrowserModelTest.set("b","B","Artist","Mapper",120,60000),
                SongBrowserModelTest.set("c","C","Other","Mapper",120,60000)),new Random(42));
    }
    @Test void excludesCurrentWhenAlternativesExist() {
        var m = model();
        for (int i=0;i<100;i++) { var before = m.selection(); m.random(); assertNotEquals(before.setId(),m.selection().setId()); }
    }
    @Test void singleResultAndEmptyResultsAreNoOps() {
        var m = model(); m.search("Other"); var before = m.selection(); m.random(); assertEquals(before,m.selection()); assertEquals(0,m.historySize());
        m.search("does not exist"); m.random(); m.previousRandom(); assertNull(m.selectedSet()); assertEquals(0,m.historySize());
    }
    @Test void randomAndPreviousRespectSearchResults() {
        var m = model(); m.search("Artist");
        for (int i=0;i<20;i++) { m.random(); assertNotEquals("c",m.selectedSet().id()); }
        m.previousRandom(); assertNotEquals("c",m.selectedSet().id());
    }
    @Test void multipleRandomsCanBeWalkedBackToInitialSelection() {
        var m = model(); var selections = new ArrayList<SongBrowserModel.Selection>();
        for (int i=0;i<12;i++) { selections.add(m.selection()); m.random(); }
        for (int i=selections.size()-1;i>=0;i--) { m.previousRandom(); assertEquals(selections.get(i),m.selection()); }
        var before = m.selection(); m.previousRandom(); assertEquals(before,m.selection()); assertEquals(0,m.historySize());
    }
    @Test void historyIsBoundedAndUnchangedRandomDoesNotGrowIt() {
        var m = model(); for(int i=0;i<500;i++)m.random(); assertEquals(SongBrowserModel.HISTORY_LIMIT,m.historySize());
        m.search(m.selectedSet().title() + " " + m.selectedSet().artist());
        int size = m.historySize(); for(int i=0;i<100;i++)m.random(); assertEquals(size,m.historySize());
    }
    @Test void sortAndGroupDoNotInvalidateHistory() {
        var m = model(); var before = m.selection(); m.random(); m.sort(SongBrowserModel.Sort.ARTIST); m.group(SongBrowserModel.Group.BPM);
        m.previousRandom(); assertEquals(before,m.selection());
    }
    @Test void hiddenHistoryCanBeUsedAfterClearingSearch() {
        var m = model(); var before = m.selection(); m.random(); var current = m.selection();
        m.search(m.selectedSet().title() + " " + m.selectedSet().artist()); m.previousRandom(); assertEquals(current,m.selection()); assertEquals(1,m.historySize());
        m.search(""); m.previousRandom(); assertEquals(before,m.selection());
    }
    @Test void libraryRemovalLeavesSafeHistory() {
        var m = model(); m.random(); m.library(List.of()); m.previousRandom(); assertNull(m.selectedSet());
    }
    @Test void previousRandomFallsBackWhenTheRecordedDifficultyWasRemoved() {
        var a = SongBrowserModelTest.set("a","A","Artist","Mapper",120,60000);
        var old = a.difficulties().getFirst();
        var hard = new dev.osujava.beatmap.BeatmapDifficulty(old.title(),old.artist(),old.creator(),"Hard",0,"","",null,List.of(),List.of(),null,null,java.nio.file.Path.of("a/hard.osu"));
        var multi = new dev.osujava.beatmap.BeatmapSet(a.id(),a.title(),a.artist(),a.creator(),null,null,List.of(old,hard),List.of());
        var m = new SongBrowserModel(List.of(multi,SongBrowserModelTest.set("b","B","Artist","Mapper",120,60000)),new Random(1));
        m.select("a",1); m.random();
        m.library(List.of(a,SongBrowserModelTest.set("b","B","Artist","Mapper",120,60000))); m.previousRandom();
        assertEquals("a",m.selectedSet().id()); assertEquals(SongBrowserModel.difficultyId(old),m.selection().difficultyId());
    }
    @Test void emptyFilterDoesNotConsumeHistory() {
        var m = model(); m.random(); int size = m.historySize();
        m.search("missing"); m.previousRandom(); assertEquals(size,m.historySize());
    }

}
