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
            assertEquals(72, SongSelectMetrics.rowHeight(layout.height()));
            assertEquals(SongSelectMetrics.rowWidth(hd.width()), SongSelectMetrics.rowWidth(layout.width()));
            assertEquals(300, layout.pointerY(Math.round(420 * layout.scale())));
        }
    }
    @Test void settledBodiesDoNotCoverAdjacentLabelsOrHitBounds() {
        for (var layout : List.of(UiLayout.fromPixels(1100, 720), UiLayout.fromPixels(1280, 720),
                UiLayout.fromPixels(1920, 1080), UiLayout.fromPixels(2560, 1440))) {
            float body = SongSelectMetrics.rowHeight(layout.height());
            var model = new SongSelectCarousel();
            var entries = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(n -> new SongSelectCarousel.Entry("set#" + n, 0, n)).toList();
            model.content(entries, 574, body, SongSelectMetrics.rowPitch(layout.height()),
                    SongSelectMetrics.rowPitch(layout.height()), "set#3", layout.height(), 658);
            for (int frame = 0; frame < 120; frame++) model.advance(1f / 60, null);
            for (int i = 1; i < model.rows().size(); i++) {
                float upperBottom = model.renderY(model.rows().get(i - 1), 658);
                float lowerTop = model.renderY(model.rows().get(i), 658) + body;
                assertTrue(lowerTop <= upperBottom + .001f, "An upper row must not cover its neighbour");
            }
            assertEquals(390, model.renderY(model.rows().get(3), 658) + body / 2, .001);
            assertTrue(SongSelectRowPresentation.geometry(650, body, 500, true).starsY() >= 0);
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
    @Test void confirmedReferenceCoordinatesAreIndependentOfSkinBodyHeight() {
        assertEquals(72, SongSelectMetrics.rowPitch(720));
        assertEquals(108, SongSelectMetrics.rowPitch(1080));
        assertEquals(770, SongSelectMetrics.curveX(360, 1280, 720));
        assertEquals(826.25, SongSelectMetrics.curveX(0, 1280, 720));
        for (float bodyHeight : new float[]{76, 80, 88}) {
            var model = new SongSelectCarousel();
            var entries = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(n -> new SongSelectCarousel.Entry("set#" + n, 0, n)).toList();
            model.content(entries, 574, bodyHeight, 72, 72, "set#5", 720, 658);
            for (int frame = 0; frame < 180; frame++) model.advance(1f / 60, null);
            var selected = model.rows().get(5);
            assertEquals(390, model.renderY(selected, 658) + bodyHeight / 2, .001);
            assertEquals(72, model.rows().get(6).logicalY - selected.logicalY, .001);
            float before = model.renderX(selected, 1280);
            for (int frame = 0; frame < 180; frame++) model.advance(1f / 60, "set#5");
            assertEquals(67.5, before - model.renderX(selected, 1280), .001);
            assertEquals(15, model.rows().get(4).separationY, .001);
            assertEquals(-15, model.rows().get(6).separationY, .001);
        }
    }

}
