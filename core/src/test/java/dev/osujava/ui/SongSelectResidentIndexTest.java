package dev.osujava.ui;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectResidentIndexTest {
    private List<SongSelectCarousel.Entry> entries(int count, boolean open) {
        var result = new ArrayList<SongSelectCarousel.Entry>();
        for (int i = 0; i < count; i++) {
            result.add(new SongSelectCarousel.Entry("g" + i, -1, -2, "g" + i, open, true));
            for (int d = 0; d < 4; d++) result.add(new SongSelectCarousel.Entry("s" + i + "#" + d,
                    i, d, "s" + i, d < 2, open && d < 2));
        }
        return result;
    }
    private void assertIndex(SongSelectCarousel c) {
        var expected = Collections.newSetFromMap(new IdentityHashMap<SongSelectCarousel.Row, Boolean>());
        var presented = new ArrayList<SongSelectCarousel.Row>();
        for (var row : c.allRows()) {
            if (row.resident && row.entry.visible()) expected.add(row);
            if (c.presents(row)) presented.add(row);
        }
        assertEquals(expected, c.residentRows(), "Index must match the original full scan, including group seeds");
        var ranged = new ArrayList<SongSelectCarousel.Row>();
        for (int i = c.activeStart(); i < c.activeEnd(); i++) {
            var row = c.allRows().get(i);
            if (c.presents(row)) ranged.add(row);
        }
        assertEquals(presented, ranged, "Range traversal must retain the full scan's draw order");
    }
    @Test void membershipMatchesFullScanThroughScrollSelectionGroupsResizeAndReplacement() {
        var c = new SongSelectCarousel();
        c.content(entries(1000, false), 480, 48, 48, "g0");
        assertIndex(c);
        for (int frame = 0; frame < 600; frame++) {
            if (frame % 50 == 0) {
                boolean open = frame % 100 == 0;
                c.content(entries(1000, open), 480, 48, 48, "g" + frame);
                assertIndex(c); // Group seed residency exists before the next advance.
            }
            if (frame % 7 == 0) c.wheel(frame < 300 ? 4 : -4);
            if (frame % 81 == 0) c.select("g" + frame);
            c.focus("g" + frame);
            c.advance(1f / 60, "g" + frame);
            assertIndex(c);
        }
        c.content(entries(1000, true), 720, 72, 72, "g500", 1080, 954);
        assertIndex(c); c.advance(.02f, null); assertIndex(c);
        c.reordered(); c.advance(.016f, null); assertIndex(c);
        c.content(entries(3, false), 480, 48, 48, "g0");
        assertIndex(c); c.advance(.016f, null); assertIndex(c);
        c.content(List.of(), 480, 48, 48, null);
        assertIndex(c); assertTrue(c.residentRows().isEmpty());
    }
    @Test void settledLargeLibraryOnlyPresentsTheExistingOverscanBuffer() {
        var c = new SongSelectCarousel();
        c.content(entries(10000, false), 480, 48, 48, "g5000");
        c.snapOnNextFrame();
        for (int i = 0; i < 120; i++) c.advance(1f / 60, null);
        assertIndex(c);
        assertTrue(c.activeEnd() - c.activeStart() < 100);
        assertFalse(c.residentRows().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> c.residentRows().clear());
    }
}
