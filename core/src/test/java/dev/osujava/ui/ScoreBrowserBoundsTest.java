package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreBrowserBoundsTest {
    @Test void rowPitchUsesTheStableThirtyThreeUnitContractIndependentOfSkinDensity() {
        for (int[] size : new int[][]{{1280,720},{1024,768},{1280,800},{1920,1080},{960,1000}}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var bounds = ScoreBrowserBounds.of(ui);
            assertEquals(33 * ui.height() / 480, bounds.rowPitch(), .0001f);
            for (int i = 0; i < bounds.capacity(); i++) {
                var clip = bounds.rowClip(i);
                assertTrue(clip.y() >= bounds.bottom() - .001f);
                assertEquals(i,bounds.slot(clip.x()+1,clip.y()+clip.height()/2));
                assertEquals(-1,bounds.slot(clip.x()+1,clip.y()-.1f));
                assertTrue(clip.x()+clip.width() <= SongSelectMetrics.wheelLeft(ui.width(),ui.height()) + .001f);
            }
        }
    }
    @Test void scoreIndicatorTracksTheVisibleRangeAndStaysInsideTheViewport() {
        var bounds = ScoreBrowserBounds.of(UiLayout.fromPixels(1280,720));
        assertEquals(0,bounds.thumb(0,0).height());
        assertEquals(0,bounds.thumb(0,bounds.capacity()).height());
        var top = bounds.thumb(0,1000); var bottom = bounds.thumb(1000-bounds.capacity(),1000);
        assertEquals(bounds.top(),top.y()+top.height(),.001);
        assertEquals(bounds.bottom(),bottom.y(),.001);
        assertEquals(bounds.x()+bounds.width(),top.x()+top.width(),.001);
        assertTrue(top.height() >= 18);
    }
}
