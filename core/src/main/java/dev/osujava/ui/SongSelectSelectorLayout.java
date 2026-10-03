package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.SongSelectToolboxLayout.Bounds;

/** Stable's 480-high dialog coordinates, converted once for both painting and hit testing. */
final class SongSelectSelectorLayout {
    static final float BUTTON_WIDTH=460, BUTTON_HEIGHT=40, BUTTON_PITCH=50;
    static final float MOD_X=240, MOD_PITCH=66, MOD_ROW_PITCH=60;
    final float scale, width, height;
    SongSelectSelectorLayout(UiLayout layout) { scale=Math.min(layout.height()/480,layout.width()/640); width=layout.width(); height=layout.height(); }
    Bounds button(float top) { return new Bounds((width-BUTTON_WIDTH*scale)/2,height-(top+BUTTON_HEIGHT)*scale,BUTTON_WIDTH*scale,BUTTON_HEIGHT*scale); }
    Bounds option(int index) { return button(105+index*BUTTON_PITCH); }
    Bounds reset() { return button(320); }
    Bounds closeMods() { return button(370); }
    Bounds mod(SongSelectToolboxState.Mod mod) {
        return new Bounds((MOD_X+mod.column*MOD_PITCH-32)*scale,height-(138+mod.group*MOD_ROW_PITCH+24)*scale,64*scale,48*scale);
    }
    Bounds mode(int index) { return new Bounds(0,(60+index*80)*scale,230*scale,80*scale); }
    Bounds modeIcon(int index) { var b=mode(index); return new Bounds(b.x()+8*scale,b.y()+8*scale,64*scale,64*scale); }
    Bounds modeList() { return new Bounds(0,60*scale,230*scale,320*scale); }
}
