package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectPointerTest {
    private String update(SongSelectPointer input, boolean held, boolean inside, float x, float y) {
        if (!held) return input.release(inside, false);
        input.sample(x, y);
        return null;
    }

    @Test void pressAndHoverDoNotCommitButReleaseOverTheSameIdentityDoes() {
        var input = new SongSelectPointer();
        assertNull(update(input, false, true, 100, 100));
        input.press("a", 100, 100);
        assertNull(update(input, true, true, 100, 100));
        assertEquals("a", update(input, false, true, 102, 101));
        assertNull(update(input, false, true, 102, 101));
    }
    @Test void releasingOutsideRetainsTheCandidateUntilANewPress() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertNull(update(input, false, false, 100, 100));
        assertEquals("a", input.pressedKey());
        input.press("b", 100, 100);
        assertEquals("b", update(input, false, true, 100, 100));
    }
    @Test void draggingThenReturningToTheOriginCannotActivate() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertNull(update(input, true, true, 181, 100));
        assertNull(update(input, false, true, 100, 100));
        input.press("a", 100, 100); input.cancel();
        assertNull(update(input, false, true, 100, 100));
    }
    @Test void exactlyEightyPixelsIncludingDiagonalMotionStillClicks() {
        for (float[] offset : new float[][]{{80, 0}, {0, -80}, {48, 64}}) {
            var input = new SongSelectPointer();
            input.press("a", 100, 100);
            update(input, true, true, 100 + offset[0], 100 + offset[1]);
            assertEquals("a", update(input, false, true, 100, 100));
            assertNull(input.pressedKey());
        }
    }
    @Test void releaseDoesNotIntroduceANewDistanceCancellation() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertEquals("a", update(input, false, true, 200, 100));
    }
    @Test void pressedIdentitySurvivesAnotherRowOverlappingIt() {
        var pressed = new SongSelectRow(1, 0, null, false, false, 100, 100, 400, 72, 0, 1);
        var overlapping = new SongSelectRow(0, 0, null, true, false, 100, 100, 400, 72, 0, 1);
        var input = new SongSelectPointer();
        input.press("pressed", 200, 130);
        assertSame(overlapping, SongSelectRow.hit(java.util.List.of(pressed, overlapping), 200, 130, 0, 720));
        assertEquals("pressed", update(input, false, pressed.boundsContain(200, 130), 200, 130));
    }
    @Test void releaseBoundsUseCurrentPositionAndTopLeftHalfOpenEdgesWithoutAnAlphaGate() {
        var row = new SongSelectRow(0, 0, null, false, false, 100, 100, 400, 72, 0, .01f);
        assertTrue(row.boundsContain(100, 172));
        assertFalse(row.boundsContain(500, 172));
        assertFalse(row.boundsContain(100, 100));
        assertFalse(row.contains(100, 172));
        var input = new SongSelectPointer();
        input.press("a", 200, 300);
        assertNull(update(input, false, row.boundsContain(200, 300), 200, 300));
    }
}
