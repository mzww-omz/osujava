package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserClassificationTest {
    private BeatmapDifficulty chart(String name, double bpm, int end) {
        return new BeatmapDifficulty("Title","Artist","Mapper",name,0,"","",null,
                List.of(new TimingPoint(0,60000/bpm,4,0,0,100,true,0)),
                List.of(new HitObject(0,0,40000,HitObject.Type.CIRCLE,1,0),
                        new HitObject(0,0,end,HitObject.Type.CIRCLE,1,0)),null,null,Path.of(name+".osu"));
    }
    private BeatmapSet set(String id, BeatmapDifficulty... charts) {
        return new BeatmapSet(id,id,"Artist","Mapper",null,null,List.of(charts),List.of());
    }
    @Test void numericSortAndRepresentativeUseMatchingChartsInsteadOfTheMaximumOfASet() {
        var slow = chart("slow",90,100000); var fast = chart("fast",240,180000);
        var browser = new SongBrowserModel(List.of(set("A",slow,fast),set("B",chart("middle",120,120000))));
        browser.sort(SongBrowserModel.Sort.BPM);
        assertEquals(List.of("A","B"),browser.visibleSets().stream().map(BeatmapSet::id).toList());
        browser.search("difficulty!=slow");
        assertEquals(List.of("B","A"),browser.visibleSets().stream().map(BeatmapSet::id).toList());
        assertSame(browser.row(SongBrowserModel.rowKey("A",SongBrowserModel.difficultyId(fast))),
                browser.row(SongBrowserModel.rowKey("A",SongBrowserModel.difficultyId(fast))).representative);
    }
    @Test void lengthUsesTimelineEndAndIntegerSecondsRatherThanObjectSpan() {
        var late = chart("late",120,100100);
        var early = new BeatmapDifficulty("Title","Artist","Mapper","early",0,"","",null,late.timingPoints(),
                List.of(new HitObject(0,0,0,HitObject.Type.CIRCLE,1,0),new HitObject(0,0,100900,HitObject.Type.CIRCLE,1,0)),
                null,null,Path.of("early.osu"));
        var browser = new SongBrowserModel(List.of(set("Z",late),set("A",early)));
        browser.sort(SongBrowserModel.Sort.LENGTH);
        assertEquals(List.of("A","Z"),browser.visibleSets().stream().map(BeatmapSet::id).toList(),
                "Both end in second 100: the existing textual tie-break applies");
    }
    @ParameterizedTest @CsvSource({"59.99,0–<60 BPM","60,60–<120 BPM","119.99,60–<120 BPM",
            "120,120–<180 BPM","179.99,120–<180 BPM","180,180–<240 BPM","240,240–<300 BPM","300,300+ BPM"})
    void bpmBoundariesUseRawUnroundedMaximum(double bpm,String label) {
        var browser = new SongBrowserModel(List.of(set("A",chart("chart",bpm,100000))));
        browser.group(SongBrowserModel.Group.BPM);
        assertEquals(label,browser.row(browser.selectedKey()).parent.label);
    }
    @ParameterizedTest @CsvSource({"59999,Under 1 minute","60000,1–<2 minutes","119999,1–<2 minutes",
            "120000,2–<3 minutes","180000,3–<4 minutes","240000,4–<5 minutes","300000,5–<10 minutes","600000,10+ minutes"})
    void lengthBoundariesUseLibraryMilliseconds(int end,String label) {
        var browser = new SongBrowserModel(List.of(set("A",chart("chart",120,end))));
        browser.group(SongBrowserModel.Group.LENGTH);
        assertEquals(label,browser.row(browser.selectedKey()).parent.label);
    }
    @Test void oneSetCanBelongToDifferentGroupsWithoutDuplicatingRowsOrLosingSelection() {
        var slow = chart("slow",90,100000); var fast = chart("fast",240,180000);
        var browser = new SongBrowserModel(List.of(set("A",slow,fast)));
        var slowRow = browser.row(browser.selectedKey()); browser.select("A",1);
        var selection = browser.selection(); var fastRow = browser.row(browser.selectedKey());
        browser.group(SongBrowserModel.Group.BPM);
        assertEquals(2,browser.rows().stream().filter(r -> r.group()).count());
        assertNotSame(slowRow.parent,fastRow.parent);
        assertSame(slowRow,slowRow.representative); assertSame(fastRow,fastRow.representative);
        assertFalse(slowRow.parent.expanded); assertTrue(fastRow.parent.expanded);
        browser.toggleGroup(slowRow.parent.key);
        assertEquals(selection,browser.selection());
        assertEquals(SongBrowserModel.RowState.EXPANDED,slowRow.state);
        browser.select("A",0); assertSame(slowRow,browser.row(browser.selectedKey()));
        browser.search("difficulty=fast"); assertEquals(0,slowRow.parent.matchingChildren);
        assertEquals(1,fastRow.parent.matchingChildren);
        browser.search(""); assertSame(slowRow,browser.row(browser.selectedKey()));
    }
    @Test void nonAdjacentRecordsStartSeparateFamiliesEvenInsideOneGroup() {
        var slow = chart("slow",90,100000); var fast = chart("fast",150,100000);
        var browser = new SongBrowserModel(List.of(set("A",slow,fast),set("B",chart("middle",120,100000))));
        browser.group(SongBrowserModel.Group.CREATOR); browser.sort(SongBrowserModel.Sort.BPM);
        var a = browser.rows().stream().filter(r -> !r.group() && r.set.id().equals("A")).toList();
        assertSame(a.get(0),a.get(0).representative); assertSame(a.get(1),a.get(1).representative);
    }
    @Test void collapsedRowActivationSelectsItsOwnGroupRepresentative() {
        var slow = chart("slow",90,100000); var fast = chart("fast",240,180000);
        var browser = new SongBrowserModel(List.of(set("A",slow,fast,chart("sibling",250,190000)),set("B",chart("other",310,100000))));
        browser.group(SongBrowserModel.Group.BPM); browser.selectSet("B");
        String key = SongBrowserModel.rowKey("A",SongBrowserModel.difficultyId(fast));
        browser.toggleGroup(browser.row(key).parent.key);
        assertEquals(SongBrowserModel.RowState.COLLAPSED,browser.row(key).state);
        browser.activateRow(key);
        assertSame(fast,browser.selectedDifficulty());
        assertEquals(key,browser.selectedKey());
    }
}
