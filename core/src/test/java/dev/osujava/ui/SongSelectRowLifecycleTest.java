package dev.osujava.ui;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent boundary/trajectory fixtures from 06003273, 3263 and 3266. */
class SongSelectRowLifecycleTest {
    private SongSelectCarousel.Entry map(String key, String family, boolean expanded, boolean visible) {
        return new SongSelectCarousel.Entry(key, 0, 0, family, expanded, visible);
    }
    private SongSelectCarousel.Entry group(String key, boolean expanded) {
        return new SongSelectCarousel.Entry(key, -1, -2, key, expanded, true);
    }
    private void content(SongSelectCarousel c, List<SongSelectCarousel.Entry> entries) {
        c.content(entries, 480, 48, 48, entries.getFirst().key());
    }
    private SongSelectCarousel model(int count) {
        var c = new SongSelectCarousel();
        content(c, IntStream.range(0, count).mapToObj(i -> map("s" + i, "s" + i, false, true)).toList());
        c.advance(0, null); return c;
    }
    private List<SongSelectCarousel.Entry> grouped(boolean open) {
        return List.of(group("g", open), map("a0", "a", true, open), map("a1", "a", true, open),
                map("a2", "a", false, false), map("b0", "b", false, open), map("b1", "b", false, false),
                group("h", false));
    }
    @Test void openingSeedsEveryChildFromGroupAndStepsOnlyAfterVisibleRepresentatives() {
        var c = new SongSelectCarousel(); content(c, grouped(false)); c.advance(0, null);
        var group = c.allRows().getFirst(); group.motionY = 250; group.motionX = -395;
        var children = c.allRows().subList(1, 6); var nextGroup = c.allRows().get(6);
        float nextY = nextGroup.motionY, nextX = nextGroup.motionX;
        content(c, grouped(true));
        float[] expectedY = {250, 298, 298, 298, 346};
        for (int i = 0; i < children.size(); i++) {
            assertSame(children.get(i), c.allRows().get(i + 1));
            assertEquals(expectedY[i], children.get(i).motionY);
            assertEquals(-395, children.get(i).motionX);
        }
        assertEquals(nextY, nextGroup.motionY); assertEquals(nextX, nextGroup.motionX);
        c.advance(0, null);
        assertFalse(children.get(2).resident); assertFalse(children.get(4).resident);
        float y = children.get(1).motionY;
        content(c, grouped(false));
        assertEquals(y, children.get(1).motionY, "Closing must not run the opening seed again");
        assertTrue(children.stream().noneMatch(r -> r.resident));
    }
    @Test void excludedRepresentativesDoNotConsumeGroupSeedPitchAndReopeningUsesCurrentGroupPosition() {
        var c = new SongSelectCarousel();
        var closed = List.of(group("g", false), map("excluded", "a", false, false), map("b", "b", false, false));
        var opened = List.of(group("g", true), closed.get(1), map("b", "b", false, true));
        content(c, closed); content(c, opened);
        assertEquals(200, c.allRows().get(2).motionY);
        c.advance(.05f, null); content(c, closed);
        c.allRows().getFirst().motionY = 310; c.allRows().getFirst().motionX = -380;
        content(c, opened);
        assertEquals(310, c.allRows().get(2).motionY); assertEquals(-380, c.allRows().get(2).motionX);
    }
    @Test void residentBufferIncludesMinusTwentyAndSixFortyButNotTheirExterior() {
        for (float y : new float[]{-20.01f, -20, 640, 640.01f}) {
            var c = model(1); var row = c.rows().getFirst();
            row.logicalY = row.motionY = y; c.advance(0, null);
            assertEquals(y >= -20 && y <= 640, row.resident, "Reference Y=" + y);
        }
    }
    @Test void outsideRowsSnapAndRetireTogetherOnlyWhenCurrentAndDestinationAreOnTheSameSide() {
        var c = model(4);
        var crossing = c.rows().get(0); crossing.motionY = -50; crossing.logicalY = 200;
        c.advance(0, null); assertTrue(crossing.resident); assertEquals(-50, crossing.motionY);
        crossing.logicalY = 800; crossing.motionY = 900;
        c.rows().get(1).motionY = 950; c.rows().get(1).logicalY = 850;
        c.advance(1f / 60, null);
        assertEquals(800, crossing.motionY);
        assertEquals(850, c.rows().get(1).motionY);
        assertTrue(c.rows().stream().noneMatch(r -> r.resident), "The entire suffix is retired");
    }
    @Test void prefixRetirementIncludesHiddenRowsAndLeavesTheirIdentityIntact() {
        var c = new SongSelectCarousel();
        content(c, List.of(map("a", "a", false, true), map("hidden", "a", false, false),
                map("b", "b", false, true), map("c", "c", false, true)));
        var hidden = c.allRows().get(1);
        for (int i = 0; i < 3; i++) { c.allRows().get(i).logicalY = -100 + i * 10; c.allRows().get(i).motionY = -200; }
        c.advance(0, null);
        assertEquals(-90, hidden.motionY); assertFalse(hidden.resident);
        assertSame(hidden, c.allRows().get(1)); assertTrue(c.allRows().get(3).resident);
    }
    @Test void bottomReentryInheritsNeighbourDisplacementAndCapsHorizontalOffset() {
        var c = model(12); var anchor = c.rows().get(9); var returning = c.rows().get(10);
        assertFalse(returning.resident);
        anchor.motionY = 640; anchor.motionX = -100;
        c.dragBy(48); c.advance(0, null);
        assertEquals(688, returning.motionY, "Logical delta 48 is added to the neighbour's current Y");
        assertEquals(-140, returning.motionX, "Base X -340 plus the 200-unit cap");
        assertTrue(returning.resident); assertFalse(c.rows().get(11).resident);
    }
    @Test void topReentryUsesLogicalDeltaAndRemovesNeighbourHoverIndent() {
        var c = model(20); c.dragBy(480); c.advance(0, null);
        var anchor = c.rows().get(6); var returning = c.rows().get(5);
        assertFalse(returning.resident);
        anchor.motionY = 496; anchor.motionX = -400;
        c.dragBy(-48); c.advance(0, "s6");
        assertEquals(448, returning.motionY);
        assertEquals(-355, returning.motionX, "Remove the neighbour's -45 hover indentation");
        assertTrue(returning.resident);
    }
    @Test void emptyActiveRangeReentersFromScrollPredictionWithTwoHundredUnitXOffset() {
        var c = model(1); var row = c.rows().getFirst();
        row.motionY = row.logicalY = 700; c.advance(0, null); assertFalse(row.resident);
        row.logicalY = 200; c.advance(0, null);
        assertEquals(0, row.motionY);
        assertEquals(-68.125, row.motionX, .001); // -340 + abs(700/480-.5)*75 + 200
        assertTrue(row.resident);
    }
    @Test void admissionUsesPredictedTravelButRetirementUsesCurrentScroll() {
        var c = model(20); c.dragBy(480); c.advance(0, null);
        var returning = c.rows().get(5);
        c.dragBy(-48); c.scrollBy(100); c.advance(0, null);
        assertEquals(100, c.predictedTravel(), .001);
        assertFalse(returning.resident, "Target screen Y=8, predicted admission Y=-92");
        c.dragBy(0); c.advance(0, null);
        assertTrue(returning.resident);
    }
    @Test void forcedSnapIsConsumedOnceAndThenNormalInterpolationResumes() {
        var c = model(3); var row = c.rows().get(1);
        row.motionY = 200; row.motionX = -100;
        c.snapOnNextFrame(); c.advance(0, null);
        assertEquals(248, row.motionY); assertEquals(-333.75, row.motionX, .001);
        c.advance(1f / 60, "s1");
        assertEquals(-336.25, row.motionX, .001); // target -383.75, 5% of the difference
        assertEquals(248, row.motionY);
    }
    @Test void scaleAndChromeOriginDoNotChangeTheReferenceResidencyBoundary() {
        for (float scale : new float[]{1, 1.5f, 2.25f}) {
            var c = new SongSelectCarousel(); float height = 480 * scale, top = height - 50 * scale;
            c.content(List.of(map("s", "s", false, true)), height - 100 * scale, 48 * scale, 48 * scale, "s", height, top);
            var row = c.rows().getFirst();
            row.motionY = row.logicalY = (640 - 50) * scale; c.advance(0, null); assertTrue(row.resident);
            row.motionY = row.logicalY = (641 - 50) * scale; c.advance(0, null); assertFalse(row.resident);
        }
    }
    @Test void rebuildingAfterRemovalResetsIndicesAndReleasesOldRowReferences() {
        var c = model(100); c.dragBy(4000); c.advance(0, null);
        var entries = List.of(map("s1", "s1", false, true), map("s2", "s2", false, true));
        content(c, entries); c.snapOnNextFrame(); c.advance(0, null);
        assertEquals(2, c.allRows().size()); assertTrue(c.rows().stream().allMatch(r -> r.resident));
        c.content(List.of(), 480, 48, 48, null); c.advance(.1f, null);
        assertTrue(c.rows().isEmpty()); assertEquals(0, c.scrollOffset());
        content(c, entries); c.advance(.1f, null);
        assertEquals(2, c.allRows().size()); assertTrue(c.rows().stream().allMatch(r -> r.resident));
    }
}
