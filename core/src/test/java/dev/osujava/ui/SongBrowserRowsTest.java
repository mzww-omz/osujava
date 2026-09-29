package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserRowsTest {
    private BeatmapSet multi() {
        var first = SongBrowserModelTest.set("a", "A", "Alpha", "Mapper", 120, 1000);
        var second = SongBrowserModelTest.set("a#nested", "A", "Alpha", "Mapper", 120, 1000);
        return new BeatmapSet("a", "A", "Alpha", "Mapper", null, null,
                List.of(first.difficulties().getFirst(), second.difficulties().getFirst()), List.of());
    }
    private SongBrowserModel model() {
        return new SongBrowserModel(List.of(multi(), SongBrowserModelTest.set("b", "B", "Beta", "Mapper", 120, 1000)));
    }
    private SongBrowserModel.Row group(SongBrowserModel model, String label) {
        return model.rows().stream().filter(r -> r.group() && r.label.equals(label)).findFirst().orElseThrow();
    }
    @Test void representativeSurvivesCollapseExpansionAndSelectedDifficultyChanges() {
        var m = model();
        var representative = m.row(m.selectedKey());
        var child = m.rows().get(1);
        assertSame(representative, child.representative);
        m.select("b", 0);
        assertEquals(SongBrowserModel.RowState.COLLAPSED, representative.state);
        assertEquals(SongBrowserModel.RowState.HIDDEN, child.state);
        assertSame(representative, m.row(representative.key));
        assertEquals(SongBrowserModel.Kind.SET, m.entries().getFirst().kind());
        assertEquals(representative.key, m.entries().getFirst().key());
        m.select("a", 1);
        assertSame(child, m.row(m.selectedKey()));
        assertEquals(SongBrowserModel.RowState.EXPANDED, representative.state);
        assertEquals(SongBrowserModel.RowState.SELECTED, child.state);
        assertEquals(representative.key, m.entries().getFirst().key());
    }
    @Test void singletonIsADifficultyWithoutAnExpandedSiblingGroup() {
        var m = model();
        var singleton = m.rows().getLast();
        assertEquals(SongBrowserModel.RowState.SINGLETON, singleton.state);
        assertSame(singleton, singleton.representative);
        assertEquals(SongBrowserModel.Kind.DIFFICULTY, m.entries().getLast().kind());
        assertNotNull(m.entries().getLast().difficulty());
    }
    @Test void openingAnotherGroupClosesPreviousAndKeepsPlayableSelection() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST);
        var selected = m.row(m.selectedKey()); var selection = m.selection();
        var a = group(m, "A"); var b = group(m, "B");
        assertSame(a, selected.parent);
        assertTrue(a.expanded); assertFalse(b.expanded);
        assertEquals(2, a.matchingChildren); assertEquals(1, b.matchingChildren);
        m.toggleGroup(b.key);
        assertFalse(a.expanded); assertTrue(b.expanded);
        assertEquals(selection, m.selection()); assertNotNull(m.selectedDifficulty());
        assertEquals(SongBrowserModel.RowState.HIDDEN, selected.state);
        assertEquals(b.key, m.groupTargetKey());
        assertTrue(m.entries().stream().noneMatch(e -> e.set() != null && e.set().id().equals("a")));
        m.toggleGroup(b.key);
        assertEquals(2, m.entries().size()); // Only the two closed Group cards.
        assertEquals(selection, m.selection());
    }
    @Test void explicitSelectionReopensParentEvenIfDifficultyDidNotChange() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST);
        var a = group(m, "A"); m.toggleGroup(a.key);
        assertEquals(SongBrowserModel.RowState.HIDDEN, m.row(m.selectedKey()).state);
        m.select("a", 0);
        assertTrue(a.expanded); assertNull(m.groupTargetKey());
        assertEquals(SongBrowserModel.RowState.SELECTED, m.row(m.selectedKey()).state);
    }
    @Test void sortingAndLibraryRefreshKeepClosedGroupAndReuseRows() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST);
        var selected = m.row(m.selectedKey()); var a = group(m, "A");
        m.toggleGroup(a.key); m.sort(SongBrowserModel.Sort.ARTIST);
        m.library(List.of(SongBrowserModelTest.set("b", "B", "Beta", "Mapper", 120, 1000), multi()));
        assertSame(a, group(m, "A")); assertFalse(a.expanded);
        assertSame(selected, m.row(m.selectedKey()));
        assertEquals(SongBrowserModel.RowState.HIDDEN, selected.state);
        assertSame(m.selectedDifficulty(), selected.difficulty);
    }
    @Test void zeroResultsRetainExcludedRowsAndRestoreTheirIdentities() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST);
        var selected = m.row(m.selectedKey()); var parent = selected.parent;
        m.search("absent");
        assertTrue(m.entries().isEmpty()); assertNull(m.selectedKey());
        assertEquals(5, m.rows().size());
        assertTrue(m.rows().stream().allMatch(r -> r.excluded && r.state == SongBrowserModel.RowState.HIDDEN));
        assertNull(selected.representative); assertEquals(0, parent.matchingChildren);
        m.toggleGroup(parent.key); assertNull(m.groupTargetKey());
        m.search("");
        assertSame(selected, m.row(m.selectedKey())); assertSame(parent, selected.parent);
        assertEquals(SongBrowserModel.RowState.SELECTED, selected.state);
        assertFalse(selected.excluded); assertEquals(2, parent.matchingChildren);
    }
    @Test void difficultyReorderPreservesIdentityButReassignsRepresentative() {
        var m = model(); m.select("a", 1);
        var selected = m.row(m.selectedKey()); var set = multi();
        m.library(List.of(new BeatmapSet("a", "A", "Alpha", "Mapper", null, null,
                List.of(set.difficulties().getLast(), set.difficulties().getFirst()), List.of())));
        assertSame(selected, m.row(m.selectedKey()));
        assertSame(selected, selected.representative);
        assertTrue(m.rows().stream().allMatch(r -> r.representative == selected));
    }
    @Test void deletedRowsAndGroupsArePrunedAndGroupTargetIsRepaired() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST);
        var a = group(m, "A"); var removed = m.row(m.selectedKey()); m.toggleGroup(a.key);
        m.library(List.of(SongBrowserModelTest.set("b", "B", "Beta", "Mapper", 120, 1000)));
        assertNull(m.row(removed.key)); assertNull(m.row(a.key)); assertNull(m.groupTargetKey());
        assertEquals("b", m.selectedSet().id()); assertTrue(group(m, "B").expanded);
        m.library(List.of()); assertTrue(m.rows().isEmpty()); assertTrue(m.entries().isEmpty());
    }
    @Test void groupingModeReparentsPersistentBeatmapRows() {
        var m = model(); var row = m.row(m.selectedKey());
        m.group(SongBrowserModel.Group.ARTIST); var oldParent = row.parent;
        m.group(SongBrowserModel.Group.CREATOR);
        assertSame(row, m.row(m.selectedKey())); assertNotSame(oldParent, row.parent);
        assertNull(m.row(oldParent.key)); assertTrue(row.parent.expanded);
        m.group(SongBrowserModel.Group.NONE);
        assertSame(row, m.row(m.selectedKey())); assertNull(row.parent);
    }
    @Test void rowKeysCannotCollideWhenPathsOrSetIdsContainSeparators() {
        assertNotEquals(SongBrowserModel.rowKey("a#b", "c"), SongBrowserModel.rowKey("a", "b#c"));
        assertNotEquals(SongBrowserModel.rowKey("a", "12:x"), SongBrowserModel.rowKey("a12", ":x"));
    }
}
