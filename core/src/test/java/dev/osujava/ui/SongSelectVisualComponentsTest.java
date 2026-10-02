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
            for (var category : SongBrowserControls.Tab.values()) {
                if (!category.available() || category.ordinal() >= SongBrowserControls.tabCount(ui.width(),ui.height())) continue;
                var tab = SongBrowserControls.tabBounds(ui.width(),ui.height(),category.ordinal());
                assertTrue(tab.y() > layout.search().y()+layout.search().height());
                assertTrue(layout.search().y() >= layout.chrome().carouselTop());
                assertTrue(controls.click(tab.x()+tab.width()/2,tab.y()+tab.height()/2,ui.width(),ui.height(),browser));
                assertEquals(category.group,browser.group()); assertFalse(controls.open());
            }
            var g = SongBrowserControls.groupBounds(ui.width(),ui.height());
            controls.click(g.x()+2,g.y()+2,ui.width(),ui.height(),browser);
            var tab = SongBrowserControls.tabBounds(ui.width(),ui.height(),0);
            controls.click(g.x()+5,tab.y()+2,ui.width(),ui.height(),browser);
            assertEquals(SongBrowserModel.Group.NONE,browser.group()); // Dropdown takes precedence over tabs.
        }
    }
    @Test void narrowPlayWindowRetainsAllLocalGroupsInDropdownButUsesFiveNativeTabs() {
        assertEquals(5,SongBrowserControls.tabCount(960,720));
        assertEquals(6,SongBrowserControls.tabCount(1280,720));
        assertEquals(5,SongBrowserControls.tabCount(1152,768));
        assertEquals(6,SongBrowserControls.tabCount(1153,768));
        assertEquals(5,SongBrowserControls.tabCount(1280,900));
        assertEquals(5,SongBrowserControls.tabCount(1080,720));
        assertEquals(6,SongBrowserControls.tabCount(1081,720));
        var controls = new SongBrowserControls(); var browser = new SongBrowserModel(List.of());
        var g = SongBrowserControls.groupBounds(960,720);
        controls.click(g.x()+1,g.y()+1,960,720,browser);
        assertTrue(controls.click(g.x()+1,g.y()-26*4-13,960,720,browser));
        assertEquals(SongBrowserModel.Group.LENGTH,browser.group());
    }
    @Test void unimplementedNativeTabsKeepTheirIdentityWithoutSelectingAnotherLocalGroup() {
        assertEquals(List.of(SongBrowserControls.Tab.ALL,SongBrowserControls.Tab.DIFFICULTY,
                SongBrowserControls.Tab.ARTIST,SongBrowserControls.Tab.CREATOR,SongBrowserControls.Tab.RECENT,
                SongBrowserControls.Tab.COLLECTIONS),List.of(SongBrowserControls.Tab.values()));
        var browser = new SongBrowserModel(List.of()); var controls = new SongBrowserControls();
        browser.group(SongBrowserModel.Group.BPM);
        var requests = new java.util.ArrayList<SongBrowserControls.Tab>();
        for (var tab : SongBrowserControls.Tab.values()) if (!tab.available()) {
            var b = SongBrowserControls.tabBounds(1280,720,tab.ordinal());
            assertTrue(controls.click(b.x()+2,b.y()+2,1280,720,browser,requests::add));
            assertEquals(SongBrowserModel.Group.BPM,browser.group());
        }
        assertEquals(List.of(SongBrowserControls.Tab.DIFFICULTY,SongBrowserControls.Tab.COLLECTIONS),requests);
    }
    @Test void artistAndCreatorTabsCoupleSortButNoGroupingPreservesIt() {
        var browser = new SongBrowserModel(List.of()); var controls = new SongBrowserControls();
        for (var tab : List.of(SongBrowserControls.Tab.ARTIST,SongBrowserControls.Tab.CREATOR,
                SongBrowserControls.Tab.ALL)) {
            var b = SongBrowserControls.tabBounds(1280,720,tab.ordinal());
            assertTrue(controls.click(b.x()+2,b.y()+2,1280,720,browser));
            assertEquals(tab.group,browser.group());
            assertEquals(tab == SongBrowserControls.Tab.ARTIST ? SongBrowserModel.Sort.ARTIST
                    : SongBrowserModel.Sort.CREATOR,browser.sort());
        }
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
    @Test void focusedRowBrightensEachRgbByteByFortyPercentWithoutChangingAlpha() {
        var tint = new Color(35 / 255f, 143 / 255f, 240 / 255f, .37f);
        SongSelectRowColours.focusTint(tint, 1);
        assertEquals(49 / 255f, tint.r, .0001f);
        assertEquals(200 / 255f, tint.g, .0001f);
        assertEquals(1, tint.b, .0001f);
        assertEquals(.37f, tint.a);
        var base = new Color(.1f, .2f, .3f, .4f);
        var unchanged = new Color(base);
        SongSelectRowColours.focusTint(unchanged, 0);
        assertEquals(base, unchanged);
    }

}
