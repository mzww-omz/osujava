package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectLayoutTest {
    @Test void layoutDensityIsIndependentOfResolutionAndFramebufferDensity() {
        var hd = UiLayout.fromPixels(1280, 720);
        var full = UiLayout.fromPixels(1920, 1080);
        var retina = UiLayout.fromPixels(2560, 1440);
        for (var layout : List.of(hd, full, retina)) {
            assertEquals(80, SongSelectMetrics.rowHeight(layout.width(), null));
            assertEquals(SongSelectMetrics.rowWidth(hd.width()), SongSelectMetrics.rowWidth(layout.width()));
            assertEquals(300, layout.pointerY(Math.round(420 * layout.scale())));
        }
    }
    @Test void hitPriorityMatchesSelectedLastCompositingAndClipping() {
        var selected = row(0, true, 1);
        var last = row(1, false, 1);
        assertSame(selected, SongSelectRow.hit(List.of(selected, last), 600, 110, 84, 600));
        assertSame(last, SongSelectRow.hit(List.of(row(0, false, 1), last), 600, 110, 84, 600));
        assertNull(SongSelectRow.hit(List.of(selected), 600, 84, 84, 600));
        assertNull(SongSelectRow.hit(List.of(row(-1, false, 1), row(1, false, .01f)), 600, 110, 84, 600));
        assertNull(SongSelectRow.hit(List.of(selected), 499, 110, 84, 600));
    }
    private SongSelectRow row(int set, boolean selected, float reveal) {
        return new SongSelectRow(set, 0, "", selected, false, 500, 80, 600, 80, 0, reveal);
    }
}
