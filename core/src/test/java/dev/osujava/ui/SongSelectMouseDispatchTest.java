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
        sample(input.buttons(true, false, false), 300, .2f);
    }

    @Test void snapshotCoalescesSimultaneousEdgesAndRetainsPreviousRightForRelease() {
        var down = input.buttons(true, true, false);
        assertTrue(down.pressed()); assertTrue(down.leftPressed());
        assertFalse(down.released()); assertFalse(down.context());
        var held = input.buttons(true, true, false);
        assertFalse(held.pressed()); assertFalse(held.released()); assertTrue(held.context());
        var up = input.buttons(false, false, false);
        assertFalse(up.pressed()); assertTrue(up.released()); assertTrue(up.context());
        assertFalse(input.buttons(false, false, false).released());
    }
    @Test void exchangingButtonsInOneFrameReportsDownBeforeUpWithTheOldRightState() {
        input.buttons(true, false, false);
        var right = input.buttons(false, true, false);
        assertTrue(right.pressed()); assertTrue(right.released()); assertFalse(right.context());
        var left = input.buttons(true, false, false);
        assertTrue(left.pressed()); assertTrue(left.released()); assertTrue(left.context());
    }
    @Test void middleEdgesAlsoUseTheGenericDispatchWithoutBecomingALeftDrag() {
        var down = input.buttons(false, false, true);
        assertTrue(down.pressed()); assertFalse(down.leftPressed());
        input.pressPointer("s0", 800, 300, false, carousel); sample(down, 300, .1f);
        assertTrue(input.buttons(false, false, false).released());
        assertEquals("s0", input.releasePointer(false, true));
        assertEquals(0, carousel.scrollOffset());
    }
    @Test void rightRowPressCapturesWithoutDraggingAndCommitsOnlyOnRelease() {
        input.pressPointer("s0", 800, 300, true, carousel);
        sample(input.buttons(false, true, false), 280, .02f);
        assertEquals("s0", input.pressedKey()); assertEquals(0, carousel.scrollOffset());
        assertFalse(input.rightScrolling());
        assertTrue(input.buttons(false, false, false).context());
        assertEquals("s0", input.releasePointer(false, true));
    }
    @Test void extraButtonPressDuringDragRetainsHoverAndDoesNotResetItsVelocity() {
        pressLeft(); sample(input.buttons(true, false, false), 250, .02f);
        float speed = carousel.scrollVelocity();
        input.pressPointer("s1", 800, 250, true, carousel);
        sample(input.buttons(true, true, false), 250, .02f);
        assertEquals("s0", input.pressedKey()); assertEquals(speed, carousel.scrollVelocity());
        assertEquals(50, carousel.scrollOffset());
    }
    @Test void rightReleaseCanCommitWhileLeftDragContinuesWithoutASecondCommitOnLeftRelease() {
        pressLeft();
        input.pressPointer("s0", 800, 300, true, carousel);
        sample(input.buttons(true, true, false), 300, .02f);
        var up = input.buttons(true, false, false);
        assertTrue(up.released()); assertTrue(up.context());
        assertEquals("s0", input.releasePointer(true, true));
        sample(up, 270, .02f);
        assertEquals(30, carousel.scrollOffset());
        assertNull(input.releasePointer(false, true));
    }
    @Test void releaseIsEvaluatedBeforeNewHeldMotionCanCancelTheCandidate() {
        pressLeft();
        input.pressPointer("s0", 800, 300, true, carousel);
        sample(input.buttons(true, true, false), 300, .02f);
        var up = input.buttons(true, false, false);
        assertEquals("s0", input.releasePointer(true, true));
        sample(up, 200, .02f);
        assertEquals(100, carousel.scrollOffset());
    }
    @Test void cancelledDragStaysCancelledAcrossAnotherPressAndReleaseWhileLeftRemainsHeld() {
        pressLeft(); sample(input.buttons(true, false, false), 210, .02f);
        input.pressPointer("s1", 800, 210, true, carousel);
        sample(input.buttons(true, true, false), 210, 0);
        assertNull(input.releasePointer(true, true));
        assertNull(input.releasePointer(false, true));
        input.buttons(false, false, false);
        input.pressPointer("s1", 800, 210, true, carousel);
        sample(input.buttons(false, true, false), 210, 0);
        assertEquals("s1", input.releasePointer(false, true));
    }
    @Test void firstDragFrameDoesNotContributeToTheVelocityEstimate() {
        pressLeft(); sample(input.buttons(true, false, false), 250, .02f);
        assertEquals(50, carousel.scrollOffset());
        assertEquals(2.5 * (1 - Math.pow(.9, 20)) * 1000, carousel.scrollVelocity(), .001);
    }
    @Test void blockedSnapshotsPreventHeldButtonsFromStartingAgainAfterCancellation() {
        pressLeft(); input.cancelPointer();
        assertFalse(input.buttons(true, false, false).pressed());
        sample(input.buttons(true, false, false), 210, .02f);
        assertEquals(0, carousel.scrollOffset()); assertNull(input.pressedKey());
        assertNull(input.releasePointer(false, true));
    }
}
