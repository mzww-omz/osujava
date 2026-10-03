package dev.osujava.ui;
import dev.osujava.score.*;
import dev.osujava.gameplay.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScoreBrowserModelTest {
    @Test void contentChangeAtSameLocationResetsSelectionAndSeparatesUnverifiedLegacyRows() {
        var identity = new DifficultyIdentity("set","map.osu");
        var key = new dev.osujava.beatmap.BeatmapContentKey("a".repeat(64),0);
        var legacy = new LocalScore(UUID.randomUUID(),identity,0,new ScoreState(999,0,1,1,0,0,0,1));
        var confirmed = new LocalScore(UUID.randomUUID(),identity,0,new ScoreState(1,0,1,1,0,0,0,1),
                new ScoreDetails(ScoreDetails.SCORE_V1,key.sha256(),"",null,null,null,null,null,null,null,null));
        store.save(legacy,GameplayRunMode.MANUAL); store.save(confirmed,GameplayRunMode.MANUAL);
        scores.target(identity,key); assertEquals(confirmed,scores.rows().getFirst().score());
        assertTrue(scores.rows().getFirst().verified()); assertFalse(scores.rows().get(1).verified());
        scores.select(0); var rows = scores.rows(); scores.target(identity,key); assertSame(rows,scores.rows());
        scores.target(identity,new dev.osujava.beatmap.BeatmapContentKey("b".repeat(64),0));
        assertNull(scores.selected()); assertEquals(0,scores.first()); assertEquals(1,scores.rows().size());
        assertEquals(legacy.result(),scores.open(0).score());
        assertTrue(scores.rows().getFirst().date().contains("unverified"));
    }
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
            if(group==SongBrowserModel.Group.COLLECTIONS) continue; // This fixture has no collections; covered below with real membership.
            browser.sort(sort); browser.group(group); sync(browser);
            assertEquals(target,scores.target()); assertEquals("123",scores.rows().getFirst().value()); assertNotNull(scores.selected());
        }
    }
    @Test void collectionCopiesKeepTheSameDifficultyScoreSelectionAndCache() throws Exception {
        var map=dev.osujava.support.CollectionTestMaps.set("Map",1); var diff=map.difficulties().getFirst();
        var collections=new dev.osujava.collection.LocalCollectionStore(); var a=collections.create("A"); var b=collections.create("B");
        var target=DifficultyIdentity.of(map.id(),diff); var key=dev.osujava.beatmap.BeatmapContentKey.of(diff);
        var member=new dev.osujava.collection.LocalCollectionStore.Member(key,target);
        collections.add(a.id(),List.of(member)); collections.add(b.id(),List.of(member));
        store.save(new LocalScore(UUID.randomUUID(),target,0,new ScoreState(123,0,1,1,0,0,0,1),
                new ScoreDetails(ScoreDetails.SCORE_V1,key.sha256(),"",null,null,null,null,null,null,null,null)),GameplayRunMode.MANUAL);
        var browser=new SongBrowserModel(List.of(map)); browser.collections(collections.all()); browser.group(SongBrowserModel.Group.COLLECTIONS);
        scores.target(target,key); scores.select(0); var selected=scores.selected(); var rows=scores.rows();
        var copies=browser.rows().stream().filter(r -> !r.group()).toList();
        browser.toggleGroup(copies.get(1).parent.key); browser.activateRow(copies.get(1).key);
        scores.target(DifficultyIdentity.of(browser.selectedSet().id(),browser.selectedDifficulty()),dev.osujava.beatmap.BeatmapContentKey.of(browser.selectedDifficulty()));
        assertEquals(target,scores.target()); assertEquals(selected,scores.selected()); assertSame(rows,scores.rows()); assertEquals("123",scores.rows().getFirst().value());
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
