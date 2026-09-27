package dev.osujava.ui;

import com.badlogic.gdx.Input;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainMenuTest {
    @Test void actionsGoThroughOneNavigationGate() {
        for (int key : new int[]{Input.Keys.P, Input.Keys.ENTER, Input.Keys.SPACE, Input.Keys.ESCAPE}) {
            var navigation = new UiNavigation(); int[] calls = new int[2];
            var input = new MainMenuInput(navigation, () -> calls[0]++, () -> calls[1]++);
            assertTrue(input.keyDown(key));
            input.keyDown(Input.Keys.P); // Pending action cannot be replaced or duplicated.
            assertEquals(0, calls[0] + calls[1]);
            assertTrue(navigation.advance(.13f));
            assertEquals(key == Input.Keys.ESCAPE ? 1 : 0, calls[1]);
            assertEquals(1, calls[0] + calls[1]);
        }
        for (int target = 0; target < 3; target++) {
            var navigation = new UiNavigation(); int[] calls = new int[2];
            var input = new MainMenuInput(navigation, () -> calls[0]++, () -> calls[1]++);
            input.click(target == 0, target - 1);
            navigation.advance(.13f);
            assertEquals(target == 2 ? 1 : 0, calls[1]);
            assertEquals(target == 2 ? 0 : 1, calls[0]);
        }
    }

    @Test void polygonsAndTextFitAtEveryRequiredSizeAndDensity() {
        for (int[] size : new int[][]{{1024,768},{1280,720},{1920,1080},{600,800},{2560,1440},{1200,1600}}) {
            var ui = UiLayout.fromPixels(size[0], size[1]); var m = MainMenuLayout.from(ui);
            float scale = MainMenuMotion.scale(1.25f, 1, false);
            assertTrue(m.cx() < m.width() / 2);
            assertTrue(m.cx() - m.radius() * scale - 26 > 0);
            assertTrue(m.cy() + m.radius() * scale + 26 < m.height() - 100);
            assertTrue(m.stripRight() + MainMenuLayout.HOVER_EXTENSION < m.width());
            assertTrue(m.stripRight() - MainMenuLayout.SLANT - m.labelX() - 28 > 110);
            for (int row = 0; row < MainMenuLayout.ITEMS; row++) {
                float y = m.rowY(row) + m.rowHeight() * .5f;
                float[] hover = new float[2]; hover[row] = 1;
                assertEquals(row, m.stripAt(m.labelX() + 12, y, 1, hover, scale));
                float edge = m.right(1, 1) - MainMenuLayout.SLANT * .5f;
                assertEquals(row, m.stripAt(edge - .1f, y, 1, hover, scale));
                assertEquals(-1, m.stripAt(edge + .1f, y, 1, hover, scale));
                assertEquals(-1, m.stripAt(m.cx(), y, 1, hover, scale));
            }
            // Density changes physical coordinates, not the logical composition.
            var doubled = MainMenuLayout.from(UiLayout.fromPixels(size[0] * 2, size[1] * 2));
            assertEquals(m, doubled);
        }
    }

    @Test void entranceIsStaggeredAndCompletesWithin360Milliseconds() {
        assertEquals(0, MainMenuMotion.background(0));
        assertTrue(MainMenuMotion.background(.04f) > 0);
        assertEquals(0, MainMenuMotion.strip(.08f, 0));
        assertTrue(MainMenuMotion.strip(.11f, 0) > 0);
        assertEquals(0, MainMenuMotion.strip(.11f, 1));
        assertEquals(1, MainMenuMotion.cookie(.36f));
        assertEquals(1, MainMenuMotion.strip(.36f, 1), .00001);
        var m = MainMenuLayout.from(UiLayout.fromPixels(1280,720));
        assertEquals(-1, m.stripAt(m.labelX(), m.cy() + 20, .08f, new float[2], 1));
        assertTrue(MainMenuMotion.scale(1.25f, 1, true) < MainMenuMotion.scale(1.25f, 1, false));
    }
}
