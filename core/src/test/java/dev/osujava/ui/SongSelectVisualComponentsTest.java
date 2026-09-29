package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import dev.osujava.ui.theme.UiLayout;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectVisualComponentsTest {
    @Test void tabsSelectGroupsWithoutOverlappingSearchOrCarouselAtEveryAspectRatio() {
        for (int[] size : new int[][]{{1280,720},{1920,1080},{1024,768},{960,720}}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var layout = SongSelectLayout.create(ui,null);
            var controls = new SongBrowserControls(); var browser = new SongBrowserModel(List.of());
            for (var group : SongBrowserModel.Group.values()) {
                if (group.ordinal() >= SongBrowserControls.tabCount(ui.width(),ui.height())) continue;
                var tab = SongBrowserControls.tabBounds(ui.width(),ui.height(),group.ordinal());
                assertTrue(tab.y() > layout.search().y()+layout.search().height());
                assertTrue(layout.search().y() >= layout.chrome().carouselTop());
                assertTrue(controls.click(tab.x()+tab.width()/2,tab.y()+tab.height()/2,ui.width(),ui.height(),browser));
                assertEquals(group,browser.group()); assertFalse(controls.open());
            }
            var g = SongBrowserControls.groupBounds(ui.width(),ui.height());
            controls.click(g.x()+2,g.y()+2,ui.width(),ui.height(),browser);
            var tab = SongBrowserControls.tabBounds(ui.width(),ui.height(),0);
            controls.click(g.x()+5,tab.y()+2,ui.width(),ui.height(),browser);
            assertEquals(SongBrowserModel.Group.NONE,browser.group()); // Dropdown takes precedence over tabs.
        }
    }
    @Test void narrowWindowRetainsAllGroupsInDropdownButUsesFourTabs() {
        assertEquals(4,SongBrowserControls.tabCount(960,720));
        assertEquals(5,SongBrowserControls.tabCount(1280,720));
        var controls = new SongBrowserControls(); var browser = new SongBrowserModel(List.of());
        var g = SongBrowserControls.groupBounds(960,720);
        controls.click(g.x()+1,g.y()+1,960,720,browser);
        assertTrue(controls.click(g.x()+1,g.y()-26*4-13,960,720,browser));
        assertEquals(SongBrowserModel.Group.LENGTH,browser.group());
    }
    @Test void indicatorTracksBothEndsAndHidesWhenThereIsNoScrollRange() {
        assertEquals(0,SongSelectScrollbar.thumb(1280,84,636,0,0).height());
        var start = SongSelectScrollbar.thumb(1280,84,636,0,3000);
        var end = SongSelectScrollbar.thumb(1280,84,636,3000,3000);
        assertEquals(636,start.y()+start.height(),.001);
        assertEquals(84,end.y(),.001);
        assertEquals(start.height(),end.height());
        assertTrue(start.height() >= 18 && start.height() < 552);
        assertEquals(end,SongSelectScrollbar.thumb(1280,84,636,4000,3000));
    }
    @Test void contrastRepairOnlyTouchesDarkTextOnSelectedBundledArtwork() {
        assertTrue(SongSelectRowRenderer.fallbackWash(true,true,Color.BLACK));
        assertFalse(SongSelectRowRenderer.fallbackWash(true,false,Color.BLACK));
        assertFalse(SongSelectRowRenderer.fallbackWash(false,true,Color.BLACK));
        assertFalse(SongSelectRowRenderer.fallbackWash(true,true,Color.WHITE));
    }
}
