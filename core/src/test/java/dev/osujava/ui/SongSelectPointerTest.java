package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectPointerTest {
    @Test void pressAndHoverDoNotCommitButReleaseOverTheSameIdentityDoes() {
        var input = new SongSelectPointer();
        assertNull(input.update(false, true, 100, 100));
        input.press("a", 100, 100);
        assertNull(input.update(true, true, 100, 100));
        assertEquals("a", input.update(false, true, 102, 101));
        assertNull(input.update(false, true, 102, 101));
    }
    @Test void releasingOutsideClearsTheCandidateAndAllowsANewPress() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertNull(input.update(false, false, 100, 100));
        assertNull(input.pressedKey());
        input.press("b", 100, 100);
        assertEquals("b", input.update(false, true, 100, 100));
    }
    @Test void draggingThenReturningToTheOriginCannotActivate() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertNull(input.update(true, true, 181, 100));
        assertNull(input.update(false, true, 100, 100));
        input.press("a", 100, 100); input.cancel();
        assertNull(input.update(false, true, 100, 100));
    }
    @Test void exactlyEightyPixelsIncludingDiagonalMotionStillClicks() {
        for (float[] offset : new float[][]{{80, 0}, {0, -80}, {48, 64}}) {
            var input = new SongSelectPointer();
            input.press("a", 100, 100);
            input.update(true, true, 100 + offset[0], 100 + offset[1]);
            assertEquals("a", input.update(false, true, 100, 100));
            assertNull(input.pressedKey());
        }
    }
    @Test void releaseDoesNotIntroduceANewDistanceCancellation() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertEquals("a", input.update(false, true, 200, 100));
    }
    @Test void pressedIdentitySurvivesAnotherRowOverlappingIt() {
        var pressed = new SongSelectRow(1, 0, null, false, false, 100, 100, 400, 72, 0, 1);
        var overlapping = new SongSelectRow(0, 0, null, true, false, 100, 100, 400, 72, 0, 1);
        var input = new SongSelectPointer();
        input.press("pressed", 200, 130);
        assertSame(overlapping, SongSelectRow.hit(java.util.List.of(pressed, overlapping), 200, 130, 0, 720));
        assertEquals("pressed", input.update(false, pressed.boundsContain(200, 130), 200, 130));
    }
    @Test void releaseBoundsUseCurrentPositionAndTopLeftHalfOpenEdgesWithoutAnAlphaGate() {
        var row = new SongSelectRow(0, 0, null, false, false, 100, 100, 400, 72, 0, .01f);
        assertTrue(row.boundsContain(100, 172));
        assertFalse(row.boundsContain(500, 172));
        assertFalse(row.boundsContain(100, 100));
        assertFalse(row.contains(100, 172));
        var input = new SongSelectPointer();
        input.press("a", 200, 300);
        assertNull(input.update(false, row.boundsContain(200, 300), 200, 300));
    }
}
