package dev.osujava.ui;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectTrackingTest {
    private List<SongSelectCarousel.Entry> entries() {
        return IntStream.range(0, 20).mapToObj(i -> new SongSelectCarousel.Entry("s" + i, i, -1)).toList();
    }
    private SongSelectCarousel model() {
        var c = new SongSelectCarousel(); c.content(entries(), 480, 48, 48, "s0"); return c;
    }
    @Test void pointerSelectionAndKeyboardTrackingUseDifferentDecays() {
        var pointer = model(); var keyboard = model();
        pointer.select("s4"); keyboard.select("s4"); keyboard.keyboardNavigation();
        pointer.advance(.1f, null); keyboard.advance(.1f, null);
        assertEquals(172 * (1 - Math.pow(.99, 100)), pointer.scrollOffset(), .0001);
        assertEquals(172 * (1 - Math.pow(.992, 100)), keyboard.scrollOffset(), .0001);
        for (int i = 0; i < 240; i++) { pointer.advance(1f / 60, null); keyboard.advance(1f / 60, null); }
        assertEquals(172, keyboard.scrollOffset(), .0001);
        assertTrue(pointer.scrollOffset() < 172 && pointer.scrollOffset() > 171);
    }
    @Test void leftPointerTrackingUsesTheTwoHundredReferenceUnitThreshold() {
        var left = model(); var boundary = model();
        left.wheel(1); boundary.wheel(1);
        left.pointerTracking(199, false, true); boundary.pointerTracking(200, false, true);
        left.advance(.1f, null); boundary.advance(.1f, null);
        assertEquals(0, left.scrollOffset());
        assertEquals(30.05476, boundary.scrollOffset(), .0001);
        assertEquals(1, left.rows().getFirst().selectedAmount);
    }
    @Test void wheelCancelsFocusTrackingAndLeftPointerReturnsToPlayableSelection() {
        var c = model(); c.select("s4"); c.focus("s4"); c.emphasize("s0");
        c.selectionTrackingTarget("s0"); c.keyboardNavigation(); c.advance(.1f, null);
        assertTrue(c.scrollOffset() > 0);
        c.wheel(1); c.pointerTracking(200, false, true);
        float wheelDestination = c.scrollTarget();
        c.advance(.1f, null); assertEquals(wheelDestination, c.scrollTarget(), .001);
        c.pointerTracking(199, false, true); c.advance(.1f, null);
        assertEquals(0, c.scrollTarget(), .0001);
        for (int i = 0; i < 240; i++) c.advance(1f / 60, null);
        assertEquals(0, c.scrollOffset(), .0001);
        assertEquals(1, c.rows().get(4).focusAmount);
        assertEquals(0, c.rows().get(4).selectedAmount, .0001);
        assertEquals(1, c.rows().get(0).selectedAmount, .0001);
    }
    @Test void pointerPressAndDisabledInteractionSuspendTrackingWithoutTeleporting() {
        for (boolean enabled : new boolean[]{false, true}) {
            var c = model(); c.select("s4"); c.keyboardNavigation();
            c.pointerTracking(100, enabled, enabled); // Disabled, or held down on the left.
            assertEquals(0, c.scrollOffset());
            c.advance(.1f, null);
            assertEquals(172 * (1 - Math.pow(.99, 100)), c.scrollOffset(), .0001);
        }
        var clicked = model(); clicked.select("s4"); clicked.keyboardNavigation();
        clicked.pointerPressed(); clicked.pointerTracking(200, false, true);
        clicked.advance(.1f, null);
        assertEquals(172 * (1 - Math.pow(.99, 100)), clicked.scrollOffset(), .0001);
    }
    @Test void resizingScalesLogicalVelocityAndPredictionWithoutChangingReferenceInertia() {
        var c = model(); c.wheel(1); c.advance(.1f, null);
        float offset = c.scrollOffset(), velocity = c.scrollVelocity(), prediction = c.predictedTravel();
        c.content(entries(), 720, 72, 72, "s0");
        assertEquals(offset * 1.5, c.scrollOffset(), .0001);
        assertEquals(velocity * 1.5, c.scrollVelocity(), .0001);
        assertEquals(prediction * 1.5, c.predictedTravel(), .0001);
    }
    @Test void curveUsesSignedUnclampedPredictionAtTheScrollBoundary() {
        var c = model(); c.wheel(-1);
        var row = c.rows().get(4); float before = c.renderX(row, 1280);
        c.advance(.1f, null);
        assertEquals(0, c.scrollOffset()); assertTrue(c.predictedTravel() < 0);
        float curve = SongSelectMetrics.curveX(row.motionY + c.predictedTravel(), 1280, 480);
        assertEquals(curve - (curve - before) * .95 * .95 * .95 * .95 * .95 * .95,
                c.renderX(row, 1280), .001);
    }
    @Test void rightPointerClampsTheSeventyToFourHundredReferenceYRangeAndUsesTrackDecay() {
        for (float y : new float[]{0, 70, 235, 400, 480}) {
            var c = model(); c.rightScroll(200, y);
            double expected = y <= 70 ? 0 : y >= 400 ? c.maxScroll() : c.maxScroll() / 2;
            assertEquals(expected, c.scrollTarget(), .0001);
            c.advance(.1f, null);
            assertEquals(expected * (1 - Math.pow(.992, 100)), c.scrollOffset(), .0001);
        }
    }
    @Test void leftAreaAndKeyboardTrackingTakePriorityOverRightPositioning() {
        var c = model(); c.rightScroll(199, 400);
        assertEquals(0, c.scrollTarget());
        c.keyboardNavigation(); c.rightScroll(200, 400);
        assertEquals(0, c.scrollTarget());
        c.pointerPressed(); c.rightScroll(200, 400);
        assertEquals(c.maxScroll(), c.scrollTarget());
        c.pointerTracking(199, false, true); c.advance(.1f, null);
        assertEquals(0, c.scrollTarget());
    }
}
