package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.ui.theme.*;
import dev.osujava.ui.SongSelectToolboxLayout.Bounds;
import dev.osujava.ui.SongSelectToolboxState.Mod;

/** Mode flyout and full-screen Mods dialog; capability decisions stay in selector logic. */
final class SongSelectToolboxOverlay {
    static final float DIALOG_DIM_ALPHA=240f/255;
    private static final Color TINT=new Color(), FILL=new Color();
    private static final String[] MODE_NAMES={"osu!","osu!taiko","osu!catch","osu!mania"};
    private static final String[] GROUP_NAMES={"Difficulty Reduction","Difficulty Increase","Special"};
    // AWT SansSerif has wider metrics than the native dialog font; keep labels clear of icons.
    private static final float DIALOG_FONT_FACTOR=.85f, GROUP_FONT_SIZE=18;
    private static final Color GREEN=new Color(.2f,.8f,.2f,1), RED=new Color(1,.27f,0,1), GREY=new Color(.45f,.45f,.45f,1);
    static Bounds close(UiLayout l) { return new SongSelectSelectorLayout(l).closeMods(); }
    static Bounds reset(UiLayout l) { return new SongSelectSelectorLayout(l).reset(); }
    static void click(SongSelectToolboxState state, UiLayout layout, float x, float y) {
        var g=new SongSelectSelectorLayout(layout);
        if(state.overlay()==SongSelectToolboxState.Overlay.MODE) {
            if(g.mode(0).contains(x,y) || !g.modeList().contains(x,y)) state.close();
        } else if(state.animation.rowAlpha(1)>0 && state.animation.bounds(close(layout),1).contains(x,y)) state.close();
        else if(state.animation.rowAlpha(0)>0 && state.animation.bounds(reset(layout),0).contains(x,y)) state.reset();
        else for(var mod:Mod.values()) if(g.mod(mod).contains(x,y)) { state.toggle(mod);break; }
    }
    static void draw(UiView view, SpriteBatch batch, UiLayout layout, SongSelectToolboxState state, SongSelectSkinAssets skin,float px,float py) {
        if(!state.animation.visible()) return;
        var g=new SongSelectSelectorLayout(layout);float alpha=state.animation.opacity(),s=g.scale;
        boolean mods=state.drawing()==SongSelectToolboxState.Overlay.MODS;
        view.beginShapes();
        view.box(0,0,layout.width(),layout.height(),0,FILL.set(0,0,0,(mods?DIALOG_DIM_ALPHA:.35f)*alpha));
        if(!mods) {
            var area=g.modeList();view.box(area.x(),area.y(),area.width(),area.height(),0,FILL.set(0,0,0,.35f*alpha));
            for(int i=0;i<4;i++) {var b=g.mode(i);if(state.open() && b.contains(px,py)) view.box(b.x(),b.y(),b.width(),b.height(),0,FILL.set(1,1,1,.12f*alpha));}
        } else {
            dialogButton(view,g.reset(),"1. Reset All Mods",RED,state.animation,0,px,py,true,false);
            dialogButton(view,g.closeMods(),"2. Close",GREY,state.animation,1,px,py,true,false);
        }
        view.endShapes();view.beginText();
        if(!mods) {
            for(int i=0;i<4;i++) {
                var b=g.mode(i);var icon=g.modeIcon(i);
                TINT.set(1,1,1,alpha*(i==0?1:.48f));
                SongSelectSkinDrawing.fit(batch,skin,SongSelectSkinAssets.modeImage(i,0),icon.x(),icon.y(),icon.width(),icon.height(),TINT);
                view.textCenteredVertically(MODE_NAMES[i],b.x()+94*s,b.y()+b.height()/2,b.width()-100*s,1.2f*s,TINT,true);
                if(i>0 && b.contains(px,py)) view.textSmooth("This mode is not playable in osu!java.",12*s,layout.height()-28*s,layout.width()-24*s,.65f*s,TINT);
            }
        } else {
            view.textSmooth("Select mods to change how you play. Hover over an icon for details.",5*s,layout.height()-24*s,layout.width()-10*s,24f/17*DIALOG_FONT_FACTOR*s,TINT.set(1,1,1,alpha));
            view.textSmooth("Score Multiplier: 1.00x",0,layout.height()-89*s,layout.width(),30f/17*DIALOG_FONT_FACTOR*s,TINT,Align.center);
            Mod hovered=null;
            for(int group=0;group<3;group++) view.textCenteredVertically(GROUP_NAMES[group],24*s,layout.height()-(138+group*60)*s,180*s,GROUP_FONT_SIZE/17*s,TINT.set(group==0?GREEN:group==1?RED:Color.WHITE).mul(1,1,1,alpha),false);
            for(var mod:Mod.values()) {
                var b=g.mod(mod);boolean hover=state.open() && b.contains(px,py);if(hover) hovered=mod;
                TINT.set(1,1,1,alpha*(hover?.9f:.60f));
                if(SongSelectSkinDrawing.present(skin,mod.image())) SongSelectSkinDrawing.fit(batch,skin,mod.image(),b.x(),b.y(),b.width(),b.height(),TINT);
                else {view.textSmoothBold(mod.acronym,b.x()+6*s,b.y()+28*s,b.width()-12*s,.90f*s,TINT);view.textSmooth(mod.label,b.x(),b.y()+9*s,b.width(),.42f*s,TINT);}
            }
            if(hovered!=null) view.textSmooth(hovered.label+" ("+hovered.shortcut+") — "+(hovered.capability()==SongSelectToolboxState.Capability.DEBUG_ONLY?"Debug Auto is available through F6; this mod is not selectable.":"Not available in osu!java yet."),12*s,layout.height()-294*s,layout.width()-24*s,.65f*s,TINT.set(1,1,1,alpha));
            dialogButton(view,g.reset(),"1. Reset All Mods",RED,state.animation,0,px,py,true,true);
            dialogButton(view,g.closeMods(),"2. Close",GREY,state.animation,1,px,py,true,true);
        }
        view.endText();
    }
    static void dialogButton(UiView view,Bounds b,String label,Color color,SongSelectMenuAnimation animation,int index,float px,float py,boolean enabled,boolean text) {
        float alpha=animation.rowAlpha(index),offset=animation.rowOffset(index,b.height()/40);
        if(text) view.textCentered(label,b.x()+offset,b.y()+b.height()/2,b.width(),b.height()*14/18/17*DIALOG_FONT_FACTOR,TINT.set(1,1,1,alpha*(enabled?1:.45f)));
        else {
            boolean hover=enabled && px>=b.x()+offset && px<b.x()+offset+b.width() && py>=b.y() && py<b.y()+b.height();
            view.box(b.x()+offset,b.y(),b.width(),b.height(),2,FILL.set(color).mul(hover?1: .85f,hover?1:.85f,hover?1:.85f,alpha*(enabled?1:.4f)));
            view.box(b.x()+offset,b.y()+b.height()/2,b.width(),b.height()/2,0,FILL.set(1,1,1,.06f*alpha));
        }
    }
}
