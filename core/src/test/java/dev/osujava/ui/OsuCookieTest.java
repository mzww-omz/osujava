package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsuCookieTest {
    @Test void onlyToolbarPartOfLogoAcceptsClicksAtAllSizesAndDensities() {
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440},{600,800}}) {
            var layout = UiLayout.fromPixels(size[0], size[1]);
            float radius = Math.min(70, layout.height() * .10f);
            float x = layout.width() - radius * .50f, y = radius * .55f;
            var cookie = new OsuCookie();
            cookie.bounds(x, y, radius, 38);
            assertTrue(cookie.hit(x, 19), "Logo still starts play inside the toolbar");
            assertTrue(cookie.hit(x, 38));
            assertFalse(cookie.hit(x, 38.1f), "Carousel input passes through overlapping artwork");
            assertFalse(cookie.hit(x, y + radius / 2));
            assertFalse(cookie.hit(x - radius - 1, 19));
            assertFalse(cookie.hit(x, -1));
            cookie.close(); cookie.close();
        }
    }
}
