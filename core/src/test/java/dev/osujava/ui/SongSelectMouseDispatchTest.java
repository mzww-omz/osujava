package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectMouseDispatchTest {
    private final SongSelectInputController input = new SongSelectInputController(
            new SongSelectToolboxState(), new SongBrowserControls(), null);
    private final SongSelectCarousel carousel = new SongSelectCarousel();

    SongSelectMouseDispatchTest() {
        carousel.content(IntStream.range(0, 20).mapToObj(i -> new SongSelectCarousel.Entry("s" + i, i, -1)).toList(),
                574, 72, 72, "s0");
    }

    private void sample(SongSelectInputController.Buttons buttons, float y, float delta) {
        input.samplePointer(buttons, 800, y, 1, 533, y / 1.5f, delta, carousel);
    }
    private void pressLeft() {
        input.pressPointer("s0", 800, 300, false, carousel);
        sample(input.buttons(true, false, false, 0), 300, .2f);
    }

    @Test void snapshotCoalescesSimultaneousEdgesAndRetainsPreviousRightForRelease() {
        var down = input.buttons(true, true, false, 0);
        assertTrue(down.pressed()); assertTrue(down.leftPressed());
        assertFalse(down.released()); assertFalse(down.context());
        var held = input.buttons(true, true, false, 0);
        assertFalse(held.pressed()); assertFalse(held.released()); assertTrue(held.context());
        var up = input.buttons(false, false, false, 0);
        assertFalse(up.pressed()); assertTrue(up.released()); assertTrue(up.context());
        assertFalse(input.buttons(false, false, false, 0).released());
    }
    @Test void exchangingButtonsInOneFrameReportsDownBeforeUpWithTheOldRightState() {
        input.buttons(true, false, false, 0);
        var right = input.buttons(false, true, false, 0);
        assertTrue(right.pressed()); assertTrue(right.released()); assertFalse(right.context());
        var left = input.buttons(true, false, false, 0);
        assertTrue(left.pressed()); assertTrue(left.released()); assertTrue(left.context());
    }
    @Test void middleEdgesAlsoUseTheGenericDispatchWithoutBecomingALeftDrag() {
        var down = input.buttons(false, false, true, 0);
        assertTrue(down.pressed()); assertFalse(down.leftPressed());
        input.pressPointer("s0", 800, 300, false, carousel); sample(down, 300, .1f);
        assertTrue(input.buttons(false, false, false, 0).released());
        assertEquals("s0", input.releasePointer(false, true));
        assertEquals(0, carousel.scrollOffset());
    }
    @Test void rightRowPressCapturesWithoutDraggingAndCommitsOnlyOnRelease() {
        input.pressPointer("s0", 800, 300, true, carousel);
        sample(input.buttons(false, true, false, 0), 280, .02f);
        assertEquals("s0", input.pressedKey()); assertEquals(0, carousel.scrollOffset());
        assertFalse(input.rightScrolling());
        assertTrue(input.buttons(false, false, false, 0).context());
        assertEquals("s0", input.releasePointer(false, true));
    }
    @Test void extraButtonPressDuringDragRetainsHoverAndDoesNotResetItsVelocity() {
        pressLeft(); sample(input.buttons(true, false, false, 0), 250, .02f);
        float speed = carousel.scrollVelocity();
        input.pressPointer("s1", 800, 250, true, carousel);
        sample(input.buttons(true, true, false, 0), 250, .02f);
        assertEquals("s0", input.pressedKey()); assertEquals(speed, carousel.scrollVelocity());
        assertEquals(50, carousel.scrollOffset());
    }
    @Test void rightReleaseCanCommitWhileLeftDragContinuesWithoutASecondCommitOnLeftRelease() {
        pressLeft();
        input.pressPointer("s0", 800, 300, true, carousel);
        sample(input.buttons(true, true, false, 0), 300, .02f);
        var up = input.buttons(true, false, false, 0);
        assertTrue(up.released()); assertTrue(up.context());
        assertEquals("s0", input.releasePointer(true, true));
        sample(up, 270, .02f);
        assertEquals(30, carousel.scrollOffset());
        assertNull(input.releasePointer(false, true));
    }
    @Test void releaseIsEvaluatedBeforeNewHeldMotionCanCancelTheCandidate() {
        pressLeft();
        input.pressPointer("s0", 800, 300, true, carousel);
        sample(input.buttons(true, true, false, 0), 300, .02f);
        var up = input.buttons(true, false, false, 0);
        assertEquals("s0", input.releasePointer(true, true));
        sample(up, 200, .02f);
        assertEquals(100, carousel.scrollOffset());
    }
    @Test void cancelledDragStaysCancelledAcrossAnotherPressAndReleaseWhileLeftRemainsHeld() {
        pressLeft(); sample(input.buttons(true, false, false, 0), 210, .02f);
        input.pressPointer("s1", 800, 210, true, carousel);
        sample(input.buttons(true, true, false, 0), 210, 0);
        assertNull(input.releasePointer(true, true));
        assertNull(input.releasePointer(false, true));
        input.buttons(false, false, false, 0);
        input.pressPointer("s1", 800, 210, true, carousel);
        sample(input.buttons(false, true, false, 0), 210, 0);
        assertEquals("s1", input.releasePointer(false, true));
    }
    @Test void firstDragFrameDoesNotContributeToTheVelocityEstimate() {
        pressLeft(); sample(input.buttons(true, false, false, 0), 250, .02f);
        assertEquals(50, carousel.scrollOffset());
        assertEquals(2.5 * (1 - Math.pow(.9, 20)) * 1000, carousel.scrollVelocity(), .001);
    }
    @Test void blockedSnapshotsPreventHeldButtonsFromStartingAgainAfterCancellation() {
        pressLeft(); input.cancelPointer();
        assertFalse(input.buttons(true, false, false, 0).pressed());
        sample(input.buttons(true, false, false, 0), 210, .02f);
        assertEquals(0, carousel.scrollOffset()); assertNull(input.pressedKey());
        assertNull(input.releasePointer(false, true));
    }

    @Test void middleDoubleClickSkipsOrdinaryDownAndThirdPressStartsAFreshPair() {
        assertTrue(input.buttons(false, false, true, 0).pressed());
        input.buttons(false, false, false, 10);
        var second = input.buttons(false, false, true, 10);
        assertTrue(second.physicalPressed()); assertFalse(second.pressed());
        assertTrue(input.buttons(false, false, false, 10).released());
        assertTrue(input.buttons(false, false, true, 10).pressed());
    }
    @Test void leftAndRightSecondPressesStillDeliverOrdinaryDown() {
        input.buttons(false, false, true, 0); input.buttons(false, false, false, 0);
        assertTrue(input.buttons(true, false, false, 10).pressed());
        input.buttons(false, false, false, 0);
        assertTrue(input.buttons(true, false, false, 10).pressed());
        input.buttons(false, false, false, 0);
        assertTrue(input.buttons(false, true, false, 10).pressed());
    }
    @Test void differentButtonsShareTheSameDoubleClickCounter() {
        input.buttons(false, true, false, 0); input.buttons(false, false, false, 0);
        assertFalse(input.buttons(false, false, true, 249).pressed());
        input.buttons(false, false, false, 0);
        assertTrue(input.buttons(false, false, true, 0).pressed());
    }
    @Test void elapsedFrameTimeIsAppliedBeforeTheNewPressAndExpiryIsInclusive() {
        input.buttons(false, false, true, 0); input.buttons(false, false, false, 0);
        assertTrue(input.buttons(false, false, true, 250).pressed());
        input.buttons(false, false, false, 0);
        assertFalse(input.buttons(false, false, true, 249).pressed());
    }
    @Test void fractionalMillisecondsAreTruncatedEveryFrameInsteadOfAccumulated() {
        input.buttons(false, false, true, 0);
        for (int i = 0; i < 249; i++) input.buttons(false, false, false, .1);
        assertTrue(input.buttons(false, false, true, .1).pressed(), "250 frames consume the integer timer in 25ms");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"30,8", "60,15", "144,36"})
    void countdownExpiresAtTheNativeIntegerFrameBoundary(int fps, int frames) {
        input.buttons(false, false, true, 0);
        for (int i = 0; i < frames - 1; i++) input.buttons(false, false, false, 1000.0 / fps);
        assertTrue(input.buttons(false, false, true, 1000.0 / fps).pressed());
        input.buttons(false, false, false, 0);
        for (int i = 0; i < frames - 1; i++) input.buttons(false, false, false, 1000.0 / fps);
        assertFalse(input.buttons(false, false, true, 0).pressed());
    }
    @Test void simultaneousPhysicalPressesConsumeOnlyOneDoubleClickDecision() {
        assertTrue(input.buttons(true, true, true, 0).pressed());
        input.buttons(false, false, false, 0);
        assertFalse(input.buttons(false, false, true, 0).pressed());
    }
    @Test void heldFramesDoNotRestartTheCounterAndReleaseTimeCounts() {
        input.buttons(false, false, true, 0);
        input.buttons(false, false, true, 200);
        input.buttons(false, false, false, 50);
        assertTrue(input.buttons(false, false, true, 0).pressed());
    }
    @Test void cancellingARowGestureDoesNotResetTheSharedDoubleClickCounter() {
        input.buttons(true, false, false, 0); input.cancelPointer();
        input.buttons(false, false, false, 0);
        assertFalse(input.buttons(false, false, true, 10).pressed());
    }
    @Test void nonFiniteOrNegativeTimeCannotExpireTheCounter() {
        input.buttons(false, false, true, 0);
        for (double dt : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -100})
            input.buttons(false, false, false, dt);
        assertFalse(input.buttons(false, false, true, 0).pressed());
    }
    @Test void suppressedMiddleDownUpdatesDistanceOriginWithoutReplacingTheCandidate() {
        pressLeft(); sample(input.buttons(true, false, false, 0), 240, .02f);
        var second = input.buttons(true, false, true, 0);
        assertTrue(second.physicalPressed()); assertFalse(second.pressed());
        input.pressPosition(800, 240); sample(second, 210, .02f);
        assertEquals("s0", input.releasePointer(true, true));
    }
}
