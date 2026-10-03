package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectSelectorLayoutTest {
    @Test void nativeDialogWidthsPitchAndModOrderAreResolutionIndependent() {
        for(int[] size:new int[][]{{1280,720},{1280,800},{1024,768},{1920,1080},{800,1000}}) {
            var l=UiLayout.fromPixels(size[0],size[1]);var g=new SongSelectSelectorLayout(l);float s=g.scale;
            assertEquals(460,g.option(0).width()/s,.0001);
            assertEquals(40,g.option(0).height()/s,.0001);
            assertEquals(50,(g.option(0).y()-g.option(1).y())/s,.0001);
            assertEquals(l.width()/2,g.option(0).x()+g.option(0).width()/2,.0001);
            for(int i=0;i<6;i++) {var b=g.option(i);assertTrue(b.x()>=0 && b.y()>=0 && b.x()+b.width()<=l.width() && b.y()+b.height()<=l.height());}
            var easy=g.mod(SongSelectToolboxState.Mod.EASY);var nf=g.mod(SongSelectToolboxState.Mod.NO_FAIL);
            assertEquals(66,(nf.x()-easy.x())/s,.0001);
            assertEquals(60,(easy.y()-g.mod(SongSelectToolboxState.Mod.HARD_ROCK).y())/s,.0001);
            assertTrue(g.mod(SongSelectToolboxState.Mod.HIDDEN).x()>g.mod(SongSelectToolboxState.Mod.DOUBLE_TIME).x());
            assertTrue(g.mod(SongSelectToolboxState.Mod.SCORE_V2).x()>g.mod(SongSelectToolboxState.Mod.AUTO).x());
            assertEquals(60,g.mode(0).y()/s,.0001);
            assertEquals(80,(g.mode(1).y()-g.mode(0).y())/s,.0001);
            assertTrue(g.mode(3).y()+g.mode(3).height()<=l.height());
        }
    }
    @Test void staggeredEntranceAndClosingKeepPaintAndInputTransformsEqual() {
        var a=new SongSelectMenuAnimation();a.open();var g=new SongSelectSelectorLayout(new UiLayout(853.3333f,480,1));
        assertEquals(0,a.rowAlpha(0));assertEquals(40,a.rowOffset(0,1));assertEquals(-40,a.rowOffset(1,1));
        a.advance(.06f);assertTrue(a.rowAlpha(0)>0);assertEquals(0,a.rowAlpha(1),.00001);
        var transformed=a.bounds(g.option(0),0);
        assertEquals(g.option(0).x()+a.rowOffset(0,1),transformed.x(),.0001);
        assertFalse(transformed.contains(g.option(0).x()+1,g.option(0).y()+1));
        a.advance(.34f);assertEquals(.75f,a.rowAlpha(0),.00001);
        a.advance(1.2f);assertEquals(1,a.rowAlpha(5));assertEquals(0,a.rowOffset(5,1),.00001);
        a.close();assertTrue(a.visible());a.advance(.12f);assertFalse(a.visible());
        a.open();a.advance(.1f);a.close();a.open();assertEquals(0,a.opacity());assertTrue(a.visible());
        a.advance(Float.NaN);assertEquals(0,a.opacity());
    }
}
