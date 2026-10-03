package dev.osujava.ui;

import dev.osujava.beatmap.*;
import dev.osujava.collection.LocalCollectionStore;
import dev.osujava.score.DifficultyIdentity;
import static dev.osujava.support.CollectionTestMaps.set;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserCollectionsTest {
    private final LocalCollectionStore store=new LocalCollectionStore();
    private void add(LocalCollectionStore.Collection c,BeatmapSet set,int index) {
        var diff=set.difficulties().get(index); store.add(c.id(),List.of(new LocalCollectionStore.Member(BeatmapContentKey.of(diff),DifficultyIdentity.of(set.id(),diff))));
    }
    @Test void duplicateMembershipHasDistinctRowsButSamePlayableIdentityAndScoreTarget() throws Exception {
        var set=set("Map",2); var a=store.create("Same name"); var b=store.create("Same name"); add(a,set,1); add(b,set,1);
        var model=new SongBrowserModel(List.of(set)); model.select("Map",1); var selected=model.selection();
        model.collections(store.all()); model.group(SongBrowserModel.Group.COLLECTIONS);
        var rows=model.rows().stream().filter(r -> !r.group()).toList(); assertEquals(2,rows.size()); assertNotEquals(rows.get(0).key,rows.get(1).key);
        assertEquals(selected,model.selection()); assertEquals(2,model.rows().stream().filter(SongBrowserModel.Row::group).count());
        model.toggleGroup(rows.get(1).parent.key); model.activateRow(rows.get(1).key);
        assertEquals(rows.get(1).key,model.selectedKey()); assertEquals(selected,model.selection()); assertSame(set.difficulties().get(1),model.selectedDifficulty());
        model.group(SongBrowserModel.Group.NONE); assertEquals(SongBrowserModel.rowKey(selected.setId(),selected.difficultyId()),model.selectedKey());
    }
    @Test void membershipIntersectsSearchPerDifficultyAndClearingRestoresIdentity() throws Exception {
        var set=set("Map",3); var other=set("Other",1); var c=store.create("Practice"); add(c,set,0); add(c,set,2);
        var model=new SongBrowserModel(List.of(set,other)); model.select("Map",2); var selected=model.selection(); model.collections(store.all());
        var controls=new SongBrowserControls(); var b=SongBrowserControls.tabBounds(1280,720,SongBrowserControls.Tab.COLLECTIONS.ordinal());
        controls.click(b.x()+1,b.y()+1,1280,720,model); assertEquals(SongBrowserModel.Group.COLLECTIONS,model.group());
        assertEquals(List.of(set),model.visibleSets()); assertEquals(2,model.rows().stream().filter(r -> !r.group() && !r.excluded).count());
        model.search("difficulty=D1"); assertNull(model.selectedDifficulty()); assertTrue(model.visibleSets().isEmpty());
        model.search(""); assertEquals(selected,model.selection());
        model.search("difficulty=D0"); assertSame(set.difficulties().getFirst(),model.selectedDifficulty());
        model.search(""); assertEquals(selected,model.selection());
    }
    @Test void contentMovementResolvesButEditsAndNewSetDifficultiesDoNotJoinAutomatically() throws Exception {
        var original=set("Map",1); var c=store.create("Practice"); add(c,original,0);
        var moved=original.difficulties().getFirst().withAssets(null,null,java.nio.file.Path.of("elsewhere.osu"));
        var clone=new BeatmapSet("moved","Map","A","C",null,null,List.of(moved),List.of());
        var model=new SongBrowserModel(List.of(clone)); model.collections(store.all()); model.group(SongBrowserModel.Group.COLLECTIONS);
        assertSame(moved,model.selectedDifficulty());
        var expanded=set("Map",3); model.library(List.of(expanded)); assertEquals(1,model.rows().stream().filter(r -> !r.group() && !r.excluded).count());
        var edited=set("Different content",1); model.library(List.of(edited)); assertTrue(model.visibleSets().isEmpty());
        assertEquals(1,store.find(c.id()).members().size());
    }
    @Test void deletionRenameAndNoCollectionsRepairSelectionWithoutIdleRebuild() throws Exception {
        var set=set("Map",1); var a=store.create("A"); var b=store.create("B"); add(a,set,0); add(b,set,0);
        var model=new SongBrowserModel(List.of(set)); model.collections(store.all()); model.group(SongBrowserModel.Group.COLLECTIONS);
        var selection=model.selection(); var rows=model.rows(); model.collections(store.all()); assertSame(rows,model.rows());
        store.rename(a.id(),"Renamed"); model.collections(store.all()); assertEquals(selection,model.selection());
        assertTrue(model.rows().stream().anyMatch(r -> r.label.equals("Renamed")));
        store.delete(a.id()); model.collections(store.all()); assertEquals(selection,model.selection());
        store.delete(b.id()); model.collections(store.all()); assertNull(model.selectedDifficulty()); assertTrue(model.entries().isEmpty());
        model.group(SongBrowserModel.Group.NONE); assertEquals(selection,model.selection());
    }
}
