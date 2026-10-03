package dev.osujava.ui;

import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.gameplay.*;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.score.*;
import dev.osujava.support.MutableWallClock;
import java.time.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserActivityTest {
    private final MutableWallClock clock=new MutableWallClock("2026-10-02T12:00:00Z",ZoneOffset.UTC);
    private final BeatmapLibrary library=new BeatmapLibrary(null,clock);
    private final LocalPlayHistory history=new LocalPlayHistory();
    private final LocalScoreStore scores=new LocalScoreStore();
    private BeatmapSet set(String id,int x) throws Exception {
        var diff=new BeatmapFileParser().parse("osu file format v14\n[Metadata]\nTitle:"+id+"\nArtist:A\nCreator:C\nVersion:D\n[HitObjects]\n"+x+",100,1000,1,0",id+".osu").difficulty().withAssets(null,null,Path.of(id+".osu"));
        var set=new BeatmapSet(id,id,"A","C",null,null,List.of(diff),List.of()); library.add(set); return set;
    }
    private void attempt(BeatmapSet set,String date) {
        history.start(UUID.randomUUID(),DifficultyIdentity.of(set.id(),set.difficulties().getFirst()),
                BeatmapContentKey.of(set.difficulties().getFirst()),Instant.parse(date).toEpochMilli(),GameplayRunMode.MANUAL);
    }
    private List<String> ids(SongBrowserModel model) { return model.visibleSets().stream().map(BeatmapSet::id).toList(); }
    @Test void recentlyPlayedTabUsesActualAttemptDatesWithNeverPlayedLastAndKeepsSelection() throws Exception {
        var never=set("a-never",100); var old=set("b-old",101); var week=set("c-week",102);
        var yesterday=set("d-yesterday",103); var today=set("e-today",104);
        attempt(old,"2026-09-01T12:00:00Z"); attempt(week,"2026-09-29T12:00:00Z");
        attempt(yesterday,"2026-10-01T12:00:00Z"); attempt(today,"2026-10-02T11:00:00Z");
        var projection=new SongBrowserActivity(library,history,scores,clock); assertTrue(projection.refresh());
        var model=new SongBrowserModel(library.all()); model.activity(projection.facts(),clock); model.select(never.id(),0);
        var selection=model.selection(); var controls=new SongBrowserControls();
        var tab=SongBrowserControls.tabBounds(1280,720,SongBrowserControls.Tab.RECENT.ordinal());
        assertTrue(controls.click(tab.x()+1,tab.y()+1,1280,720,model));
        assertEquals(SongBrowserModel.Group.RECENT,model.group()); assertEquals(SongBrowserModel.Sort.RECENT,model.sort());
        assertEquals(List.of(today.id(),yesterday.id(),week.id(),old.id(),never.id()),ids(model)); assertEquals(selection,model.selection());
        assertEquals(List.of("Today","Yesterday","Last 7 Days","Older","Never Played"),model.rows().stream().filter(SongBrowserModel.Row::group).map(r -> r.label).toList());
        model.search("played<7"); assertEquals(List.of(today.id(),yesterday.id(),week.id()),ids(model));
        model.search("unplayed="); assertEquals(List.of(never.id()),ids(model));
        model.search(""); assertEquals(selection,model.selection());
    }
    @Test void queryUsesElapsedDaysAndLocalAddedCalendarDateWithoutMatchingUnknownDates() throws Exception {
        var recent=set("recent",100); attempt(recent,"2026-10-02T00:00:00Z");
        var diff=recent.difficulties().getFirst(); var facts=new SongBrowserActivity(library,history,scores,clock); facts.refresh();
        var model=new SongBrowserModel(library.all()); model.activity(facts.facts(),clock);
        model.search("played=0.5 added>=2026-10-02 added<2026-10-03"); assertEquals(List.of("recent"),ids(model));
        model.search("played!=0.5"); assertTrue(ids(model).isEmpty());
        model.activity(Map.of(diff,SongBrowserActivity.Facts.UNKNOWN),clock);
        model.search("added!=2026-10-01"); assertTrue(ids(model).isEmpty());
        model.search("unplayed!="); assertEquals(List.of("recent"),ids(model));
        model.search("added=invalid-date"); assertTrue(ids(model).isEmpty());
    }
    @Test void dateAddedSortUsesOldestKnownContentFirstAndKeepsSelectionAcrossFiltering() throws Exception {
        var first=set("first",100);
        clock.set("2026-10-03T12:00:00Z"); var second=set("second",101);
        clock.set("2026-10-04T12:00:00Z"); var unknown=set("unknown",102);
        var projection=new SongBrowserActivity(library,history,scores,clock); projection.refresh();
        var facts=new IdentityHashMap<>(projection.facts()); facts.put(unknown.difficulties().getFirst(),SongBrowserActivity.Facts.UNKNOWN);
        var model=new SongBrowserModel(library.all()); model.activity(facts,clock); model.select(first.id(),0);
        var selected=model.selection(); model.sort(SongBrowserModel.Sort.ADDED);
        assertEquals(List.of("first","second","unknown"),ids(model)); assertEquals(selected,model.selection());
        model.search("added>=2026-10-03"); assertEquals(List.of("second"),ids(model));
        model.search(""); assertEquals(selected,model.selection());
        var rows=model.rows(); model.activity(facts,clock); assertSame(rows,model.rows());
    }
    @Test void savedScoresProvideOnlyKnownContentTimeAndManualStartTakesPrecedenceOverCompletion() throws Exception {
        var map=set("map",100); var diff=map.difficulties().getFirst(); var location=DifficultyIdentity.of(map.id(),diff);
        var content=BeatmapContentKey.of(diff); var state=new ScoreState(100,0,1,1,0,0,0,1);
        scores.save(new LocalScore(UUID.randomUUID(),location,clock.millis(),state),GameplayRunMode.MANUAL);
        var projection=new SongBrowserActivity(library,history,scores,clock); projection.refresh();
        assertNull(projection.facts().get(diff).lastPlayedAt());
        var details=new ScoreDetails(ScoreDetails.SCORE_V1,content.sha256(),"",null,null,null,null,null,null,null,null);
        scores.save(new LocalScore(UUID.randomUUID(),location,clock.millis(),state,details),GameplayRunMode.MANUAL); assertTrue(projection.refresh());
        assertEquals(clock.millis(),projection.facts().get(diff).lastPlayedAt());
        attempt(map,"2026-10-02T11:55:00Z"); assertTrue(projection.refresh());
        assertEquals(Instant.parse("2026-10-02T11:55:00Z").toEpochMilli(),projection.facts().get(diff).lastPlayedAt());
        assertEquals(1,history.revision()); // Reading old scores never writes an invented attempt.
        assertFalse(projection.refresh()); var cached=projection.facts(); assertFalse(projection.refresh()); assertSame(cached,projection.facts());
    }
    @Test void localMidnightAndDstBoundaryRefreshGroupingOnceWithInjectedTimezone() throws Exception {
        var ny=clock.withZone(ZoneId.of("America/New_York")); clock.set("2026-11-01T04:30:00Z");
        var map=set("map",100); attempt(map,"2026-11-01T04:20:00Z");
        var projection=new SongBrowserActivity(library,history,scores,ny); var model=new SongBrowserModel(library.all());
        projection.refresh(); model.activity(projection.facts(),ny); model.group(SongBrowserModel.Group.RECENT);
        assertEquals("Today",model.rows().getFirst().label);
        clock.set("2026-11-02T04:59:59Z"); assertFalse(projection.refresh(),"DST day lasts 25 hours");
        clock.set("2026-11-02T05:00:00Z"); assertTrue(projection.refresh()); model.activity(projection.facts(),ny);
        assertEquals("Yesterday",model.rows().getFirst().label); assertFalse(projection.refresh());
        clock.set("2026-11-01T04:30:00Z"); assertTrue(projection.refresh(),"Clock rollback crosses the cached date range");
    }
}
