package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserFocusTest {
    static BeatmapSet set(String id, String artist, int count) {
        var diffs = new ArrayList<BeatmapDifficulty>();
        for (int i = 0; i < count; i++) diffs.add(new BeatmapDifficulty(id, artist, "Mapper", "D" + i, 0,
                "", "", DifficultySettings.defaults(), List.of(), List.of(), null, null, Path.of(id, i + ".osu")));
        return new BeatmapSet(id, id, artist, "Mapper", null, null, diffs, List.of());
    }
    private SongBrowserModel model() {
        return new SongBrowserModel(List.of(set("a", "Alpha", 2), set("b", "Beta", 3), set("c", "Gamma", 1)));
    }
    @Test void siblingNavigationSelectsButCrossSetNavigationOnlyFocusesRepresentative() {
        var m = model();
        m.moveDifficulty(1);
        assertEquals("D1", m.selectedDifficulty().version()); assertNull(m.focusKey());
        var chosen = m.selection(); var projection = m.entries();
        m.moveDifficulty(1);
        assertEquals(chosen, m.selection()); assertSame(projection, m.entries());
        var focus = m.row(m.focusKey());
        assertEquals("b", focus.set.id()); assertSame(focus, focus.representative);
        assertEquals(SongBrowserModel.RowState.COLLAPSED, focus.state);
        assertTrue(m.confirmFocus());
        assertEquals("b", m.selectedSet().id()); assertEquals("D0", m.selectedDifficulty().version());
        assertNull(m.focusKey()); assertFalse(m.confirmFocus());
    }
    @Test void returningToSelectedFamilyClearsFocusAndSelectsTheReachedSibling() {
        var m = model(); m.select("a", 1);
        m.moveDifficulty(1); m.moveDifficulty(-1);
        assertNull(m.focusKey()); assertEquals("D1", m.selectedDifficulty().version());
        m.moveDifficulty(1); m.moveDifficulty(1);
        assertEquals("c", m.row(m.focusKey()).set.id());
        m.moveDifficulty(1); // Circular end -> first expanded difficulty of a.
        assertNull(m.focusKey()); assertEquals("a", m.selectedSet().id()); assertEquals("D0", m.selectedDifficulty().version());
    }
    @Test void eitherHorizontalDirectionConfirmsFocusBeforeMovingToAnotherSet() {
        for (int direction : new int[]{-1, 1}) {
            var m = model(); m.select("a", 1); m.moveDifficulty(1);
            m.moveSet(direction);
            assertEquals("b", m.selectedSet().id()); assertNull(m.focusKey());
        }
    }
    @Test void horizontalNavigationSkipsSelectedFamilyAndFindsHiddenDifficulties() {
        var m = model(); m.select("a", 1);
        m.moveSet(-1); assertEquals("c", m.selectedSet().id());
        m.moveSet(-1); assertEquals("b", m.selectedSet().id()); assertEquals("D0", m.selectedDifficulty().version());
        m.moveSet(1); assertEquals("c", m.selectedSet().id());
        m.moveSet(1); assertEquals("a", m.selectedSet().id());
    }
    @Test void pageSkipsCollapsedChildrenAndDoesNotExpandIntermediateSets() {
        var sets = new ArrayList<BeatmapSet>();
        sets.add(set("a", "Artist", 4));
        for (char c = 'b'; c <= 'l'; c++) sets.add(set("" + c, "Artist", 3));
        var m = new SongBrowserModel(sets); m.select("a", 2);
        var projection = m.entries(); var chosen = m.selection();
        m.movePage(1);
        assertEquals("j", m.row(m.focusKey()).set.id());
        assertEquals(chosen, m.selection()); assertSame(projection, m.entries());
        m.movePage(-1);
        assertEquals(chosen, m.selection()); assertNull(m.focusKey());
    }
    @Test void pageStopsAfterOneCircuitAtItsOriginWithSmallLibraries() {
        var m = model(); m.select("a", 1);
        var chosen = m.selection(); m.movePage(1);
        assertEquals(chosen, m.selection()); assertNull(m.focusKey());
        m.moveDifficulty(1); var focused = m.focusKey();
        m.movePage(-1);
        assertEquals(chosen, m.selection()); assertEquals(focused, m.focusKey());
    }
    @Test void focusCanOpenAndCloseGroupWithoutChangingPlayableSelection() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST); m.select("a", 1);
        var chosen = m.selection(); m.moveDifficulty(1);
        var group = m.row(m.focusKey());
        assertTrue(group.group()); assertEquals("B", group.label); assertFalse(group.expanded);
        assertTrue(m.confirmFocus()); assertTrue(group.expanded);
        assertEquals(chosen, m.selection()); assertEquals(group.key, m.focusKey());
        assertTrue(m.confirmFocus()); assertFalse(group.expanded);
        assertEquals(chosen, m.selection());
        m.confirmFocus(); m.moveDifficulty(1);
        assertEquals("b", m.row(m.focusKey()).set.id());
        m.confirmFocus(); assertEquals("b", m.selectedSet().id()); assertNull(m.focusKey());
    }
    @Test void hiddenSelectionStartsMovementFromTheActiveOpenGroup() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST);
        var b = m.rows().stream().filter(r -> r.group() && r.label.equals("B")).findFirst().orElseThrow();
        m.toggleGroup(b.key);
        assertFalse(m.row(m.selectedKey()).visible());
        m.moveDifficulty(1);
        assertEquals("b", m.row(m.focusKey()).set.id());
        assertEquals("a", m.selectedSet().id());
    }
    @Test void groupNavigationWrapsAndParentToggleIgnoresDifferentFocusedGroup() {
        var m = model(); m.group(SongBrowserModel.Group.ARTIST); m.select("a", 1);
        m.moveDifficulty(1); assertEquals("B", m.row(m.focusKey()).label);
        m.toggleParentGroup();
        assertNull(m.focusKey()); assertEquals("A", m.row(m.groupTargetKey()).label);
        assertFalse(m.row(m.groupTargetKey()).expanded);
        m.moveGroup(-1); assertEquals("G", m.row(m.groupTargetKey()).label);
        m.moveGroup(1); assertEquals("A", m.row(m.groupTargetKey()).label);
        assertEquals("a", m.selectedSet().id());
    }
    @Test void filteringResetsFocusAndNeverNavigatesExcludedRows() {
        var m = model(); m.select("a", 1); m.moveDifficulty(1);
        m.search("Gamma"); assertNull(m.focusKey()); assertEquals("c", m.selectedSet().id());
        m.moveDifficulty(1); m.moveSet(1); m.movePage(-1);
        assertEquals("c", m.selectedSet().id()); assertNull(m.focusKey());
        m.search("absent"); m.moveDifficulty(1); m.moveSet(-1); m.movePage(1); m.moveGroup(1); m.toggleParentGroup();
        assertNull(m.selectedSet()); assertNull(m.focusKey()); assertFalse(m.confirmFocus());
        m.library(List.of()); m.moveDifficulty(1); m.movePage(-1); assertTrue(m.rows().isEmpty());
    }
    @Test void focusIdentitySurvivesSortAndMetadataRefreshButNotRemoval() {
        var m = model(); m.select("a", 1); m.moveDifficulty(1);
        var focused = m.row(m.focusKey()); var key = m.focusKey();
        m.sort(SongBrowserModel.Sort.ARTIST);
        m.library(List.of(set("b", "Beta", 3), set("a", "Alpha", 2), set("c", "Gamma", 1)));
        assertEquals(key, m.focusKey()); assertSame(focused, m.row(m.focusKey()));
        m.library(List.of(set("a", "Alpha", 2), set("c", "Gamma", 1)));
        assertNull(m.focusKey()); assertFalse(m.confirmFocus());
    }
}
