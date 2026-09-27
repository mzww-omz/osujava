package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsuCookieTest {
    @Test void onlyToolbarPartOfLogoAcceptsClicksAtAllSizesAndDensities() {
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440},{600,800}}) {
            var layout = UiLayout.fromPixels(size[0], size[1]);
            float radius = SongSelectChrome.cookieRadius(layout.height());
            float x = SongSelectChrome.cookieX(layout.width(), radius), y = SongSelectChrome.cookieY(radius);
            var cookie = new OsuCookie();
            float bottom = SongSelectChrome.bottomHeight(layout.height());
            cookie.bounds(x, y, radius, bottom);
            assertTrue(cookie.hit(x, 19), "Logo still starts play inside the toolbar");
            assertTrue(cookie.hit(x, bottom));
            assertFalse(cookie.hit(x, bottom + .1f), "Carousel input passes through overlapping artwork");
            assertFalse(cookie.hit(x, y + radius * .6f));
            assertFalse(cookie.hit(x - radius - 1, 19));
            assertFalse(cookie.hit(x, -1));
            cookie.close(); cookie.close();
        }
    }
}
