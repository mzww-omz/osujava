package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectPointerTest {
    @Test void pressAndHoverDoNotCommitButReleaseOverTheSameIdentityDoes() {
        var input = new SongSelectPointer();
        assertNull(input.update(false, "a", 100, 100));
        input.press("a", 100, 100);
        assertNull(input.update(true, "a", 100, 100));
        assertEquals("a", input.update(false, "a", 102, 101));
        assertNull(input.update(false, "a", 102, 101));
    }
    @Test void releasingOutsideOrOverAnotherRowCancels() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertNull(input.update(false, "b", 100, 100));
        input.press("a", 100, 100);
        assertNull(input.update(false, null, 100, 100));
    }
    @Test void draggingThenReturningToTheOriginCannotActivate() {
        var input = new SongSelectPointer();
        input.press("a", 100, 100);
        assertNull(input.update(true, "a", 120, 100));
        assertNull(input.update(false, "a", 100, 100));
        input.press("a", 100, 100); input.cancel();
        assertNull(input.update(false, "a", 100, 100));
    }
}
