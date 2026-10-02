package dev.osujava.ui;
import dev.osujava.score.*;
import dev.osujava.gameplay.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScoreBrowserModelTest {
    private final LocalScoreStore store=new LocalScoreStore();
    private final ScoreBrowserModel scores=new ScoreBrowserModel(store);
    private void save(DifficultyIdentity target,long value) {
        store.save(new LocalScore(UUID.randomUUID(),target,0,new ScoreState(value,0,10,10,0,0,0,1)),GameplayRunMode.MANUAL);
    }
    private void sync(SongBrowserModel browser) {
        scores.target(browser.selectedSet()==null ? null : DifficultyIdentity.of(browser.selectedSet().id(),browser.selectedDifficulty()));
    }
    @ParameterizedTest @EnumSource(SongBrowserModel.Sort.class)
    void everySortAndGroupKeepsDifficultyTarget(SongBrowserModel.Sort sort) {
        var a=SongBrowserModelTest.set("a","A","A","A",120,1000);
        var b=SongBrowserModelTest.set("b","B","B","B",180,2000);
        var browser=new SongBrowserModel(List.of(a,b)); browser.select("b",0);
        var target=DifficultyIdentity.of("b",b.difficulties().getFirst()); save(target,123);
        sync(browser); scores.select(0);
        for(var group:SongBrowserModel.Group.values()) {
            browser.sort(sort); browser.group(group); sync(browser);
            assertEquals(target,scores.target()); assertEquals("123",scores.rows().getFirst().value()); assertNotNull(scores.selected());
        }
    }
    @Test void searchRandomPreviousAndLibraryImportHaveNoStaleScores() {
        var a=SongBrowserModelTest.set("a","A","A","A",120,1000);
        var b=SongBrowserModelTest.set("b","B","B","B",180,2000);
        var browser=new SongBrowserModel(List.of(a,b),new Random(1)); browser.select("a",0);
        var target=DifficultyIdentity.of("a",a.difficulties().getFirst()); save(target,123); sync(browser);
        browser.random(); sync(browser); assertTrue(scores.rows().isEmpty());
        browser.previousRandom(); sync(browser); assertEquals(target,scores.target());
        browser.search("B"); sync(browser); assertTrue(scores.rows().isEmpty());
        browser.search("impossible"); sync(browser); assertNull(scores.target()); assertTrue(scores.rows().isEmpty());
        browser.search(""); sync(browser); assertEquals(target,scores.target());
        browser.library(List.of(b,a,SongBrowserModelTest.set("new","New","X","Y",1,1))); sync(browser);
        assertEquals(target,scores.target()); assertEquals(1,scores.rows().size());
    }
    @Test void scrollingSelectionCachingAndSaveRefresh() {
        var target=new DifficultyIdentity("a","a.osu"); for(int i=0;i<100;i++) save(target,i);
        scores.target(target); scores.capacity(5); var rows=scores.rows(); scores.target(target); assertSame(rows,scores.rows());
        scores.scroll(50); assertEquals(50,scores.first()); scores.select(53); assertNotNull(scores.selected());
        scores.scroll(1000); assertEquals(95,scores.first()); scores.scroll(-1000); assertEquals(0,scores.first());
        scores.scroll(Float.NaN); assertEquals(0,scores.first()); scores.scroll(.5f); scores.scroll(.5f); assertEquals(1,scores.first());
        save(target,101); scores.target(target); assertEquals(101,scores.rows().size()); assertNotNull(scores.selected());
        scores.target(new DifficultyIdentity("a","b.osu")); assertTrue(scores.rows().isEmpty()); assertNull(scores.selected()); assertEquals(0,scores.first());
    }
    @Test void scoreBoundsExcludeCarouselChromeCookieAndGaps() {
        var l=new dev.osujava.ui.theme.UiLayout(1280,720,1); var b=ScoreBrowserBounds.of(l);
        assertTrue(b.contains(40,360)); assertFalse(b.contains(800,360)); assertFalse(b.contains(40,40));
        assertFalse(b.contains(40,600)); assertEquals(-1,b.slot(40,b.top()-b.rowHeight()-(b.rowPitch()-b.rowHeight())/2));
        assertTrue(b.rowY(b.capacity()-1)>=b.bottom());
    }
}
