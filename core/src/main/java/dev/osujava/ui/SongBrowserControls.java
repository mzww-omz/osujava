package dev.osujava.ui;

import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiView;
import com.badlogic.gdx.graphics.Color;

/** Small legacy-style text selectors. Mouse activation never captures typing focus. */
final class SongBrowserControls {
    enum Menu { GROUP, SORT }
    private Menu menu;
    private static final float OPTION_HEIGHT = 26;
    private static final Color PANEL = new Color(.055f, .04f, .09f, .97f);
    record Bounds(float x, float y, float width, float height) {
        boolean hit(float px, float py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    static Bounds groupBounds(float w, float h) { return new Bounds(w * .56f, h - 30, w * .23f, 26); }
    static Bounds sortBounds(float w, float h) { return new Bounds(w * .80f, h - 30, w * .19f, 26); }
    boolean open() { return menu != null; }
    void close() { menu = null; }
    boolean click(float x, float y, float w, float h, SongBrowserModel browser) {
        if (groupBounds(w,h).hit(x,y)) { menu = menu == Menu.GROUP ? null : Menu.GROUP; return true; }
        if (sortBounds(w,h).hit(x,y)) { menu = menu == Menu.SORT ? null : Menu.SORT; return true; }
        if (menu == null) return false;
        var bounds = menu == Menu.GROUP ? groupBounds(w,h) : sortBounds(w,h);
        int count = menu == Menu.GROUP ? SongBrowserModel.Group.values().length : SongBrowserModel.Sort.values().length;
        var panel = new Bounds(bounds.x(), bounds.y() - count * OPTION_HEIGHT, bounds.width(), count * OPTION_HEIGHT);
        if (panel.hit(x,y)) {
            int index = Math.min(count - 1, (int)((bounds.y() - y) / OPTION_HEIGHT));
            if (menu == Menu.GROUP) browser.group(SongBrowserModel.Group.values()[index]);
            else browser.sort(SongBrowserModel.Sort.values()[index]);
            close(); return true;
        }
        close(); return false;
    }
    void drawLabels(UiView view, float w, float h, SongSelectRenderer.BrowserView browser) {
        var g = groupBounds(w,h); var s = sortBounds(w,h);
        view.textSmooth("Group: " + browser.group().label + "  ▾", g.x(), h - 22, g.width(), .76f, UiTheme.TEXT);
        view.textSmooth("Sort: " + browser.sort().label + "  ▾", s.x(), h - 22, s.width(), .76f, UiTheme.TEXT);
    }
    void drawMenu(UiView view, float w, float h, SongSelectRenderer.BrowserView browser) {
        if (menu == null) return;
        var b = menu == Menu.GROUP ? groupBounds(w,h) : sortBounds(w,h);
        int count = menu == Menu.GROUP ? SongBrowserModel.Group.values().length : SongBrowserModel.Sort.values().length;
        view.beginShapes();
        view.box(b.x(), b.y() - count * OPTION_HEIGHT, b.width(), count * OPTION_HEIGHT, 0, PANEL);
        for (int i = 0; i < count; i++) {
            boolean current = menu == Menu.GROUP ? browser.group().ordinal() == i : browser.sort().ordinal() == i;
            if (current) view.box(b.x(), b.y() - (i + 1) * OPTION_HEIGHT, b.width(), OPTION_HEIGHT, 0, UiTheme.ACCENT);
        }
        view.endShapes(); view.beginText();
        for (int i = 0; i < count; i++) {
            String label = menu == Menu.GROUP ? SongBrowserModel.Group.values()[i].label : SongBrowserModel.Sort.values()[i].label;
            view.textSmooth(label, b.x() + 9, b.y() - (i + 1) * OPTION_HEIGHT + 8, b.width() - 18, .74f, UiTheme.TEXT);
        }
        view.endText();
    }
}
