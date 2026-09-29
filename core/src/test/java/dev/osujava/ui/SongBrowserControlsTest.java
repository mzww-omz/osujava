package dev.osujava.ui;

import java.util.List;
import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserControlsTest {
    @Test void selectorsAndOptionsHaveSeparateBoundsAtAllProfiles() {
        for (float w : new float[]{1280,1280,1920}) {
            float h = w == 1920 ? 1080 : 720;
            var c = new SongBrowserControls(); var m = new SongBrowserModel(List.of());
            var g = SongBrowserControls.groupBounds(w,h); var s = SongBrowserControls.sortBounds(w,h);
            assertTrue(g.x()+g.width() < s.x());
            assertTrue(c.click(g.x()+5,g.y()+5,w,h,m)); assertTrue(c.open());
            assertTrue(c.click(g.x()+5,g.y()-26-5,w,h,m)); assertEquals(SongBrowserModel.Group.ARTIST,m.group()); assertFalse(c.open());
            c.click(g.x()+5,g.y()+5,w,h,m); c.click(g.x()+5,g.y()-5,w,h,m); assertEquals(SongBrowserModel.Group.NONE,m.group());
            c.click(s.x()+5,s.y()+5,w,h,m); c.click(s.x()+5,s.y()-3*26-5,w,h,m); assertEquals(SongBrowserModel.Sort.BPM,m.sort());
            c.click(s.x()+5,s.y()+5,w,h,m); assertFalse(c.click(40,100,w,h,m)); assertFalse(c.open());
        }
    }
    @Test void toolboxShortcutsAndGapsRouteToExplicitActions() {
        assertEquals(SongSelectAction.MODS,SongSelectAction.shortcut(Input.Keys.F1,false));
        assertEquals(SongSelectAction.OPTIONS,SongSelectAction.shortcut(Input.Keys.F3,false));
        assertEquals(SongSelectAction.RANDOM,SongSelectAction.shortcut(Input.Keys.F2,false));
        assertEquals(SongSelectAction.PREVIOUS_RANDOM,SongSelectAction.shortcut(Input.Keys.F2,true));
        var layout = SongSelectToolboxLayout.create(1280,720,null);
        assertEquals(SongSelectAction.BACK,SongSelectAction.bottom(30,40,layout));
        assertEquals(SongSelectAction.MODE,SongSelectAction.bottom(250,40,layout));
        assertEquals(SongSelectAction.RANDOM,SongSelectAction.bottom(400,40,layout));
        assertEquals(SongSelectAction.OPTIONS,SongSelectAction.bottom(470,40,layout));
        assertEquals(SongSelectAction.IMPORT,SongSelectAction.bottom(layout.importAction.x()+5,layout.importAction.y()+5,layout));
        assertNull(SongSelectAction.bottom(650,40,layout));
        assertNull(SongSelectAction.bottom(340,layout.controlHeight,layout));
    }
    @Test void groupCardsShareMotionWithoutChangingBeatmapPitch() {
        var c = new SongSelectCarousel();
        c.content(List.of(new SongSelectCarousel.Entry("group:A",-1,-2),new SongSelectCarousel.Entry("a#0",0,0),
                new SongSelectCarousel.Entry("a#1",0,1),new SongSelectCarousel.Entry("b#-1",1,-1)),574,80,76.8f,81.6f,"a#1");
        assertEquals(76.8f,c.rows().get(1).logicalY-c.rows().get(0).logicalY,.001);
        assertEquals(81.6f,c.rows().get(2).logicalY-c.rows().get(1).logicalY,.001);
        c.reordered(); for(int i=0;i<120;i++)c.advance(1f/60,null);
        assertEquals(658 - 574 * 220f / 480,c.renderY(c.rows().get(2),658)+40,.01);
        assertEquals(0,c.rows().getFirst().selectedAmount);
    }
}
