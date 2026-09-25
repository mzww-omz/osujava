package dev.osujava.ui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiLayoutTest {
    @Test void handlesCommonAspectRatiosAndPointerCoordinates() {
        for (int[] size : new int[][] {{1280, 720}, {1280, 800}, {1024, 768}, {800, 600}}) {
            UiLayout layout = UiLayout.fromPixels(size[0], size[1]);
            assertTrue(layout.width() >= 959.99);
            assertTrue(layout.height() >= 719.99);
            assertEquals(0, layout.pointerX(0));
            assertEquals(layout.height(), layout.pointerY(0));
            assertEquals(layout.width(), layout.pointerX(size[0]), 0.001);
            assertEquals(0, layout.pointerY(size[1]), 0.001);
        }
    }
    @Test void contentStaysWithinWindow() {
        UiLayout layout = UiLayout.fromPixels(1024, 768);
        assertTrue(layout.contentX() >= UiTheme.PAD);
        assertTrue(layout.contentX() + layout.contentWidth() <= layout.width() - UiTheme.PAD);
    }
}
