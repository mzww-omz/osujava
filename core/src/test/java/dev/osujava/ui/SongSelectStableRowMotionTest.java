package dev.osujava.ui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Numeric contracts independently derived from b20230727.9 IL; see the phase 4 report. */
class SongSelectStableRowMotionTest {
    private SongSelectCarousel.Entry beatmap(String key, String family, boolean open, boolean visible) {
        return new SongSelectCarousel.Entry(key, 0, 0, family, open, visible);
    }
    private SongSelectCarousel.Entry group(String key, boolean open) {
        return new SongSelectCarousel.Entry(key, -1, -2, key, open, true);
    }
    private void content(SongSelectCarousel c, List<SongSelectCarousel.Entry> entries, String selected) {
        c.content(entries, 480, 48, 48, selected);
    }

    @Test void allRowsHaveCoordinatesButOnlyVisibleRowsAdvancePitchAndConditionalGaps() {
        var c = new SongSelectCarousel();
        content(c, List.of(beatmap("hidden-prefix", "p", false, false),
                beatmap("closed-a", "a", false, true), beatmap("hidden-a", "a", false, false),
                beatmap("open-b0", "b", true, true), beatmap("open-b1", "b", true, true),
                beatmap("closed-c", "c", false, true), group("g", false), group("h", true),
                beatmap("singleton", "s", false, true), beatmap("closed-d", "d", false, true)), "closed-a");
        float[] expected = {152, 200, 200, 258, 316, 374, 432, 480, 538, 586};
        assertEquals(expected.length, c.allRows().size());
        assertEquals(8, c.rows().size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], c.allRows().get(i).logicalY);
            assertEquals(i, c.allRows().get(i).logicalIndex);
        }
        assertEquals(386, c.maxScroll());
        assertEquals(0, c.scrollOffset());
        assertEquals(280, c.renderY(c.rows().getFirst(), 480) + 24);
        c.select("closed-d");
        assertEquals(366, c.scrollTarget());
    }

    @Test void hiddenRowsRetainIdentityAndReturningChildrenStartAtTheirRepresentative() {
        var c = new SongSelectCarousel();
        var collapsed = List.of(beatmap("a", "a", false, true), beatmap("a1", "a", false, false),
                beatmap("b", "b", false, true));
        content(c, collapsed, "a");
        var representative = c.allRows().get(0); var child = c.allRows().get(1); var other = c.allRows().get(2);
        var expanded = List.of(beatmap("a", "a", true, true), beatmap("a1", "a", true, true),
                beatmap("b", "b", false, true));
        content(c, expanded, "a");
        assertSame(child, c.rows().get(1));
        for (int i = 0; i < 90; i++) c.advance(1f / 60, null);
        float lastY = child.motionY;
        content(c, collapsed, "a");
        for (int i = 0; i < 30; i++) c.advance(1f / 60, "a");
        assertEquals(lastY, child.motionY, "Hidden position remains available until its next state transition");
        content(c, expanded, "a");
        assertSame(representative, c.rows().get(0)); assertSame(child, c.rows().get(1)); assertSame(other, c.rows().get(2));
        assertEquals(representative.motionY, child.motionY);
        assertEquals(representative.motionX, child.motionX);
        content(c, List.of(expanded.getFirst()), "a");
        assertEquals(List.of(representative), c.allRows(), "Removed library rows must release motion state");
    }

    @Test void hoverUsesTenUnitSeparationAndIndependentNinetyFiveAndEightySevenPointFivePercentDecays() {
        var c = new SongSelectCarousel();
        content(c, List.of(beatmap("a", "a", false, true), beatmap("b", "b", false, true),
                beatmap("c", "c", false, true)), "a");
        float x = c.rows().get(1).motionX;
        c.advance(1f / 60, "b");
        assertEquals(200 - 10 * .125, c.rows().get(0).motionY, .0001);
        assertEquals(248, c.rows().get(1).motionY);
        assertEquals(296 + 10 * .125, c.rows().get(2).motionY, .0001);
        assertEquals(x - 45 * .05, c.rows().get(1).motionX, .0001);
        for (int i = 0; i < 240; i++) c.advance(1f / 60, "b");
        assertEquals(190, c.rows().get(0).motionY, .001);
        assertEquals(306, c.rows().get(2).motionY, .001);
        assertEquals(x - 45, c.rows().get(1).motionX, .001);
    }

    @Test void expansionUsesFiftyReferenceUnitsAtEveryWidthAndNoAdditionalSelectedOffset() {
        for (float fullHeight : new float[]{480, 720, 1080}) {
            var c = new SongSelectCarousel(); float scale = fullHeight / 480;
            var entries = List.of(group("g", true), beatmap("a", "a", true, true), beatmap("a1", "a", true, true));
            c.content(entries, fullHeight, 48 * scale, 48 * scale, "a");
            for (float width : new float[]{640, 1024, 1920}) for (var row : c.rows()) {
                float down = row.motionY - c.scrollOffset();
                assertEquals(SongSelectMetrics.curveX(down, width, fullHeight) - 50 * scale,
                        c.renderX(row, width), .001);
                assertArrayEquals(new float[]{c.renderX(row, width), c.renderY(row, fullHeight)},
                        c.targetPosition(row.logicalIndex, width, fullHeight), .001f);
            }
        }
    }

    @Test void referenceInterpolationComposesAcrossFrameRatesForStationaryTargets() {
        for (double decay : new double[]{.95, .875}) {
            float expected = (float) (200 - (200 - 20) * Math.pow(decay, 60));
            for (int fps : new int[]{30, 60, 120}) {
                float actual = 20;
                for (int i = 0; i < fps; i++) actual = SongSelectCarousel.interpolate(actual, 200, decay, 1f / fps);
                assertEquals(expected, actual, .001);
            }
        }
    }

    @Test void chromeResizeKeepsScreenCoordinatesAndDisplayResizeScalesMotionAndScroll() {
        var c = new SongSelectCarousel();
        var entries = List.of(beatmap("a", "a", true, true), beatmap("a1", "a", true, true),
                beatmap("b", "b", false, true));
        c.content(entries, 552, 72, 72, "a1", 720, 636);
        c.advance(.1f, "a1");
        float y = 720 - c.renderY(c.rows().get(0), 636) - 36;
        float x = c.rows().get(0).motionX, offset = c.scrollOffset();
        c.content(entries, 574, 72, 72, "a1", 720, 658);
        assertEquals(y, 720 - c.renderY(c.rows().get(0), 658) - 36, .001);
        c.content(entries, 861, 108, 108, "a1", 1080, 987);
        assertEquals(y * 1.5f, 1080 - c.renderY(c.rows().get(0), 987) - 54, .001);
        assertEquals(x * 1.5f, c.rows().get(0).motionX, .001);
        assertEquals(offset * 1.5f, c.scrollOffset(), .001);
    }
}
