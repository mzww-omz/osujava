package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiView;
import dev.osujava.ui.SongSelectToolboxLayout.Bounds;
import dev.osujava.ui.SongSelectToolboxState.Mod;

/** Broad, flat stable-style selector band with disabled audited capabilities. */
final class SongSelectToolboxOverlay {
    private static final Color DIM = new Color(0,0,0,.64f), BAND = new Color(.035f,.03f,.055f,.96f);
    private static final Color TILE = new Color(.16f,.14f,.20f,.85f);
    static Bounds close(UiLayout l) { return new Bounds(l.width() - 185,190,150,34); }
    static Bounds reset(UiLayout l) { return new Bounds(l.width() - 385,190,180,34); }
    static void click(SongSelectToolboxState state, UiLayout layout, float x, float y) {
        if (close(layout).contains(x,y)) state.close();
        else if (state.overlay() == SongSelectToolboxState.Overlay.MODS && reset(layout).contains(x,y)) state.reset();
    }
    static void draw(UiView view, SpriteBatch batch, UiLayout layout, SongSelectToolboxState state, SongSelectSkinAssets skin) {
        if (!state.open()) return;
        view.beginShapes();
        view.box(0,0,layout.width(),layout.height(),0,DIM);
        view.box(0,178,layout.width(),365,0,BAND);
        box(view,close(layout),UiTheme.ACCENT);
        if (state.overlay() == SongSelectToolboxState.Overlay.MODS) {
            box(view,reset(layout),TILE);
            for (Mod mod : Mod.values()) box(view,tile(layout,mod),TILE);
        } else for (int i = 0; i < 4; i++)
            view.box(36 + i * (layout.width()-72)/4,300,(layout.width()-96)/4,100,0,i == 0 ? UiTheme.ACCENT : TILE);
        view.endShapes();
        view.beginText();
        if (state.overlay() == SongSelectToolboxState.Overlay.MODS) {
            view.textSmoothBold("Gameplay Mods",36,512,layout.width()-72,1.3f,UiTheme.TEXT);
            view.textSmooth("Active: None  ·  No gameplay modifiers available",36,486,layout.width()-72,.8f,UiTheme.MUTED);
            for (int group = 0; group < 3; group++) view.textSmooth(new String[]{"Difficulty reduction","Difficulty increase","Automation"}[group],
                    36,454 - group * 76,180,.72f,UiTheme.MUTED);
            for (Mod mod : Mod.values()) {
                var b = tile(layout,mod);
                if (SongSelectSkinDrawing.present(skin,mod.image()))
                    SongSelectSkinDrawing.fit(batch,skin,mod.image(),b.x()+4,b.y()+18,40,40,UiTheme.TEXT);
                else view.textSmoothBold(mod.acronym,b.x()+7,b.y()+37,40,.9f,UiTheme.MUTED);
                view.textSmooth(mod.label,b.x()+48,b.y()+33,b.width()-52,.56f,UiTheme.MUTED);
                view.textSmooth(mod.capability() == SongSelectToolboxState.Capability.DEBUG_ONLY ? "F6 debug only" : "Unavailable",
                        b.x()+7,b.y()+6,b.width()-14,.51f,UiTheme.MUTED);
            }
            view.textSmooth("1  Reset all mods",reset(layout).x()+10,211,160,.7f,UiTheme.MUTED);
        } else {
            view.textSmoothBold("Game mode",36,512,layout.width()-72,1.3f,UiTheme.TEXT);
            view.textSmooth("Only osu!standard is playable",36,486,layout.width()-72,.8f,UiTheme.MUTED);
            for (int i = 0; i < 4; i++) {
                float x = 46 + i * (layout.width()-72)/4;
                var icon = SongSelectSkinAssets.modeImage(i,2);
                if (!SongSelectSkinDrawing.present(skin,icon)) icon = SongSelectSkinAssets.modeImage(i,0);
                SongSelectSkinDrawing.fit(batch,skin,icon,x+8,350,52,52,UiTheme.TEXT);
                view.textSmoothBold(new String[]{"osu!standard","osu!taiko","osu!catch","osu!mania"}[i],x+65,365,(layout.width()-110)/4-65,.85f,UiTheme.TEXT);
                view.textSmooth(i == 0 ? "Current mode" : "Unavailable",x,330,(layout.width()-110)/4,.72f,UiTheme.MUTED);
            }
        }
        view.textSmooth("Esc / 2  Close",close(layout).x()+10,211,130,.7f,UiTheme.TEXT);
        view.endText();
    }
    private static Bounds tile(UiLayout layout, Mod mod) {
        int column = 0;
        for (var other : Mod.values()) { if (other == mod) break; if (other.group == mod.group) column++; }
        float width = Math.min(128,(layout.width()-260)/5);
        return new Bounds(220 + column * (width+8),395 - mod.group * 76,width,60);
    }
    private static void box(UiView view, Bounds b, Color color) { view.box(b.x(),b.y(),b.width(),b.height(),0,color); }
}
