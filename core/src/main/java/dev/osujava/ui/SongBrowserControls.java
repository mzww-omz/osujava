package dev.osujava.ui;

import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiView;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Small legacy-style text selectors. Mouse activation never captures typing focus. */
final class SongBrowserControls {
    enum Menu { GROUP, SORT }
    private Menu menu;
    private static final float OPTION_HEIGHT = 26;
    private static final Color TAB_IDLE = new Color(.72f,.25f,.42f,1);
    private static final Color OPTION_HOVER = new Color(.25f,.20f,.32f,1);
    private static final Color PANEL = new Color(.055f, .04f, .09f, .97f);
    record Bounds(float x, float y, float width, float height) {
        boolean hit(float px, float py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    static Bounds groupBounds(float w, float h) { return new Bounds(w * .56f, h - 30, w * .23f, 26); }
    static Bounds sortBounds(float w, float h) { return new Bounds(w * .80f, h - 30, w * .19f, 26); }
    static Bounds tabBounds(float w, float h, int index) {
        if (index >= tabCount(w,h)) return new Bounds(0,0,0,0);
        float start = w * .54f, width = (w - start - 12) / tabCount(w,h);
        return new Bounds(start + width * index, h - 52, width - 1, 20);
    }
    // Four/five tabs are documented; this aspect threshold is a local layout policy.
    static int tabCount(float w, float h) { return w <= h * 4 / 3f ? 4 : 5; }
    Menu menu() { return menu; }
    String hover(float x, float y, float w, float h) {
        if (groupBounds(w,h).hit(x,y)) return "group";
        if (sortBounds(w,h).hit(x,y)) return "sort";
        if (menu != null) {
            var b = menu == Menu.GROUP ? groupBounds(w,h) : sortBounds(w,h);
            int count = menu == Menu.GROUP ? SongBrowserModel.Group.values().length : SongBrowserModel.Sort.values().length;
            if (x >= b.x() && x < b.x()+b.width() && y < b.y() && y >= b.y()-count*OPTION_HEIGHT)
                return menu + ":" + (int)((b.y()-y)/OPTION_HEIGHT);
            return null;
        }
        for (var group : SongBrowserModel.Group.values()) if (tabBounds(w,h,group.ordinal()).hit(x,y)) return "tab:"+group;
        return null;
    }
    boolean open() { return menu != null; }
    void close() { menu = null; }
    boolean click(float x, float y, float w, float h, SongBrowserModel browser) {
        if (groupBounds(w,h).hit(x,y)) { menu = menu == Menu.GROUP ? null : Menu.GROUP; return true; }
        if (sortBounds(w,h).hit(x,y)) { menu = menu == Menu.SORT ? null : Menu.SORT; return true; }
        if (menu == null) {
            for (var group : SongBrowserModel.Group.values()) if (tabBounds(w,h,group.ordinal()).hit(x,y)) {
                browser.group(group); return true;
            }
            return false;
        }
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
    void drawShapes(UiView view, float w, float h, SongSelectSkinAssets skin) {
        for (var b : new Bounds[]{groupBounds(w,h),sortBounds(w,h)})
            view.box(b.x()+52,b.y(),b.width()-52,b.height(),0,PANEL);
        if (!SongSelectSkinDrawing.present(skin,Image.TAB)) for (var group : SongBrowserModel.Group.values()) {
            var b = tabBounds(w,h,group.ordinal());
            view.box(b.x(),b.y(),b.width(),b.height(),0,PANEL);
        }
    }
    void drawLabels(UiView view, SpriteBatch batch, float w, float h,
                    SongSelectRenderer.BrowserView browser, SongSelectSkinAssets skin, float px, float py) {
        var g = groupBounds(w,h); var s = sortBounds(w,h);
        view.textSmooth("Group",g.x(),h-22,50,.83f,UiTheme.MUTED);
        view.textSmooth(browser.group().label+"  ▾",g.x()+58,h-22,g.width()-62,.76f,UiTheme.TEXT);
        view.textSmooth("Sort",s.x(),h-22,50,.83f,UiTheme.MUTED);
        view.textSmooth(browser.sort().label+"  ▾",s.x()+58,h-22,s.width()-62,.76f,UiTheme.TEXT);
        for (var group : SongBrowserModel.Group.values()) {
            var b = tabBounds(w,h,group.ordinal());
            if (b.width() <= 0) continue;
            boolean selected = group == browser.group();
            Color tint = selected ? Color.WHITE : b.hit(px,py) ? UiTheme.ACCENT : TAB_IDLE;
            SongSelectSkinDrawing.fit(batch,skin,Image.TAB,b.x(),b.y(),b.width(),b.height(),tint);
            String label = group == SongBrowserModel.Group.NONE ? "All" : group.label;
            Color text = selected && SongSelectSkinDrawing.present(skin,Image.TAB) ? Color.BLACK : UiTheme.TEXT;
            // A contrasting outline also works when the author supplies a transparent tab.
            for (int[] offset : LABEL_OUTLINE) view.textSmooth(label,
                    b.x()+10+offset[0],b.y()+6+offset[1],b.width()-20,.60f,
                    text == Color.BLACK ? Color.WHITE : Color.BLACK, com.badlogic.gdx.utils.Align.center);
            view.textSmooth(label,
                    b.x()+10,b.y()+6,b.width()-20,.60f,
                    text,
                    com.badlogic.gdx.utils.Align.center);
        }
    }
    private static final int[][] LABEL_OUTLINE = {{-1,0},{1,0},{0,-1},{0,1}};
    void drawMenu(UiView view, float w, float h, SongSelectRenderer.BrowserView browser, float px, float py) {
        if (menu == null) return;
        var b = menu == Menu.GROUP ? groupBounds(w,h) : sortBounds(w,h);
        int count = menu == Menu.GROUP ? SongBrowserModel.Group.values().length : SongBrowserModel.Sort.values().length;
        view.beginShapes();
        view.box(b.x(), b.y() - count * OPTION_HEIGHT, b.width(), count * OPTION_HEIGHT, 0, PANEL);
        for (int i = 0; i < count; i++) {
            boolean current = menu == Menu.GROUP ? browser.group().ordinal() == i : browser.sort().ordinal() == i;
            boolean hovered = new Bounds(b.x(), b.y()-(i+1)*OPTION_HEIGHT,b.width(),OPTION_HEIGHT).hit(px,py);
            if (current || hovered) view.box(b.x(), b.y() - (i + 1) * OPTION_HEIGHT, b.width(), OPTION_HEIGHT, 0,
                    current ? UiTheme.ACCENT : OPTION_HOVER);
        }
        view.endShapes(); view.beginText();
        for (int i = 0; i < count; i++) {
            String label = menu == Menu.GROUP ? SongBrowserModel.Group.values()[i].label : SongBrowserModel.Sort.values()[i].label;
            view.textSmooth(label, b.x() + 9, b.y() - (i + 1) * OPTION_HEIGHT + 8, b.width() - 18, .74f, UiTheme.TEXT);
        }
        view.endText();
    }
}
