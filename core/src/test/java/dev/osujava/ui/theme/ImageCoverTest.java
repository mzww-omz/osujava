package dev.osujava.ui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageCoverTest {
    @Test void widePortraitOddAndTinySourcesKeepAspectAtEveryDisplayScale() {
        for (int[] size : new int[][]{{1920,1080},{200,800},{1600,160},{81,61},{3,2},{1,1}})
            for (float scale : new float[]{.9375f,1,1.40625f,2}) {
                var crop = ImageCover.crop(size[0],size[1],114*scale,85.5f*scale);
                assertEquals(1, crop.left()+crop.right(), 1e-6);
                assertEquals(1, crop.top()+crop.bottom(), 1e-6);
                double ratio = (crop.right()-crop.left())*size[0] / ((crop.bottom()-crop.top())*size[1]);
                assertEquals(4.0/3, ratio, 1e-5);
                assertTrue(crop.left() == 0 || crop.top() == 0);
            }
    }
    @Test void landscapeCropsSidesAndPortraitCropsTopAndBottom() {
        var landscape = ImageCover.crop(1600,900,400,300);
        assertEquals(.125f,landscape.left()); assertEquals(0,landscape.top());
        var portrait = ImageCover.crop(300,900,400,300);
        assertEquals(0,portrait.left()); assertEquals(.375f,portrait.top());
    }
}
