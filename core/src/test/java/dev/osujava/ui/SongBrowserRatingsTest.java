package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserRatingsTest {
    private BeatmapDifficulty chart(String name) { return new BeatmapDifficulty(name,"Artist","Mapper",name,0,"","",null,List.of(),List.of(),null,null,Path.of(name+".osu")); }
    private BeatmapSet set(String id,BeatmapDifficulty... charts) { return new BeatmapSet(id,id,"Artist","Mapper",null,null,List.of(charts),List.of()); }
    private List<String> labels(SongBrowserModel model) { return model.rows().stream().filter(SongBrowserModel.Row::group).map(r->r.label).toList(); }
    @Test void difficultyTabAndSortUseKnownRatingsWithUnknownLastAndWholeNumberGroups() {
        var low=chart("low");var high=chart("high");var mid=chart("mid");var unknown=chart("unknown");var zero=chart("zero");
        var values=new IdentityHashMap<BeatmapDifficulty,Double>();values.put(low,2.999);values.put(high,6.5);values.put(mid,3.0);values.put(zero,0.0);
        var model=new SongBrowserModel(List.of(set("A",low,high),set("B",mid),set("C",unknown),set("Z",zero)));
        model.ratings(d->values.containsKey(d)?OptionalDouble.of(values.get(d)):OptionalDouble.empty());model.select("A",1);var selection=model.selection();
        var tab=SongBrowserControls.tabBounds(1280,720,SongBrowserControls.Tab.DIFFICULTY.ordinal());
        assertTrue(new SongBrowserControls().click(tab.x()+1,tab.y()+1,1280,720,model));
        assertEquals(SongBrowserModel.Group.DIFFICULTY,model.group());assertEquals(SongBrowserModel.Sort.DIFFICULTY,model.sort());
        assertEquals(List.of("0–<1 stars","2–<3 stars","3–<4 stars","6–<7 stars","Unknown difficulty"),labels(model));assertEquals(selection,model.selection());
        model.group(SongBrowserModel.Group.NONE);
        assertEquals(List.of(zero,low,mid,high,unknown),model.rows().stream().filter(r->!r.group()).map(r->r.difficulty).toList());
        assertNotSame(model.row(SongBrowserModel.rowKey("A",SongBrowserModel.difficultyId(low))).representative,model.row(SongBrowserModel.rowKey("A",SongBrowserModel.difficultyId(high))).representative);
        assertEquals(selection,model.selection());
    }
    @Test void completedBatchesReclassifyOnceAndPreserveSelectedIdentityAndSearchRestore() {
        var a=chart("a");var b=chart("b");var c=chart("c");var values=new IdentityHashMap<BeatmapDifficulty,Double>();
        var model=new SongBrowserModel(List.of(set("A",a),set("B",b),set("C",c)));
        model.ratings(d->values.containsKey(d)?OptionalDouble.of(values.get(d)):OptionalDouble.empty());model.select("B",0);var selection=model.selection();
        model.group(SongBrowserModel.Group.DIFFICULTY);assertEquals("Unknown difficulty",model.row(model.selectedKey()).parent.label);
        values.put(b,4.0);values.put(a,2.0);assertTrue(model.ratingsChanged());assertEquals(selection,model.selection());
        assertEquals("4–<5 stars",model.row(model.selectedKey()).parent.label);assertTrue(model.row(model.selectedKey()).parent.expanded);
        var before=model.rows();values.put(c,6.0);assertTrue(model.ratingsChanged());assertNotSame(before,model.rows());assertEquals(selection,model.selection());
        model.search("stars>=4 stars<7");assertEquals(List.of("B","C"),model.visibleSets().stream().map(BeatmapSet::id).toList());
        values.put(a,5.0);assertTrue(model.ratingsChanged());assertEquals(selection,model.selection());assertEquals(3,model.visibleSets().size());
        model.search("stars>100");assertNull(model.selectedDifficulty());model.search("");assertEquals(selection,model.selection());
    }
    @Test void unrelatedStarCompletionsKeepExplicitGroupFocusAndAvoidOrdinaryBrowserRebuilds() {
        var a=chart("a");var b=chart("b");var c=chart("c");var values=new IdentityHashMap<BeatmapDifficulty,Double>();values.put(a,2.0);values.put(b,4.0);
        var model=new SongBrowserModel(List.of(set("A",a),set("B",b),set("C",c)));
        model.ratings(d->values.containsKey(d)?OptionalDouble.of(values.get(d)):OptionalDouble.empty());
        var rows=model.rows();values.put(c,6.0);assertFalse(model.ratingsChanged());assertSame(rows,model.rows());
        model.group(SongBrowserModel.Group.DIFFICULTY);var selected=model.selection();
        String group=model.rows().stream().filter(r->r.group() && r.label.equals("4–<5 stars")).findFirst().orElseThrow().key;
        model.toggleGroup(group);values.put(c,7.0);model.ratingsChanged();assertEquals(selected,model.selection());assertTrue(model.row(group).expanded);
        model.group(SongBrowserModel.Group.NONE);model.search("stars=invalid");rows=model.rows();assertFalse(model.ratingsChanged());assertSame(rows,model.rows());
    }
    @Test void numericStarSearchNeverMatchesUnknownInvalidOrNegativeValuesEvenWithNotEqual() {
        var chart=chart("fixture");var doc=SongBrowserQuery.Document.of(chart);
        for(var value:List.of(OptionalDouble.empty(),OptionalDouble.of(Double.NaN),OptionalDouble.of(Double.POSITIVE_INFINITY),OptionalDouble.of(-1)))
            for(String query:List.of("stars!=5","stars<5","stars>=0"))
                assertFalse(new SongBrowserQuery(query,d->SongBrowserActivity.Facts.UNKNOWN,Clock.systemUTC(),d->value).matches(doc));
        for(String query:List.of("stars=0","stars==0","stars>=0","stars<.1","stars!=5"))
            assertTrue(new SongBrowserQuery(query,d->SongBrowserActivity.Facts.UNKNOWN,Clock.systemUTC(),d->OptionalDouble.of(0)).matches(doc));
    }
    @Test void sortingReadsTheProviderOncePerChartRatherThanForEveryComparison() {
        var charts=new ArrayList<BeatmapSet>();for(int i=0;i<100;i++)charts.add(set("s"+i,chart("chart"+i)));
        var calls=new AtomicInteger();var model=new SongBrowserModel(charts);
        model.ratings(d->{calls.incrementAndGet();return OptionalDouble.of(d.title().hashCode()&7);});
        model.sort(SongBrowserModel.Sort.DIFFICULTY);assertEquals(100,calls.get());
        calls.set(0);model.ratingsChanged();assertEquals(100,calls.get());
    }
}
