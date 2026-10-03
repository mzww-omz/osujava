package dev.osujava.ui;

/** Frame-driven selector transforms, with no clocks, textures or frame allocations. */
final class SongSelectMenuAnimation {
    private double elapsedMs;
    private float opacity;
    private boolean showing;
    void open() { elapsedMs=0; opacity=0; showing=true; }
    void close() { showing=false; }
    void finish() { showing=false;opacity=0; }
    void advance(float seconds) {
        if(!Float.isFinite(seconds) || seconds<0) return;
        elapsedMs+=seconds*1000;
        opacity=Math.clamp(opacity+(showing?1/.2f:-1/.12f)*seconds,0,1);
        if(!showing && opacity<1e-6f) opacity=0;
    }
    boolean visible() { return showing || opacity>0; }
    float opacity() { return opacity; }
    float rowAlpha(int index) {
        float t=Math.clamp((float)((elapsedMs-index*60)/800),0,1);
        return opacity*(1-(1-t)*(1-t)); // Native dialog opacity easing 1 (OutQuad).
    }
    float rowOffset(int index,float scale) {
        double t=Math.clamp((elapsedMs-index*60)/800,0,1);
        // Native dialog alternates a 40-unit horizontal entrance with easing 33 (OutBounce).
        return (float)((index%2==0?40:-40)*(1-outBounce(t)))*scale;
    }
    dev.osujava.ui.SongSelectToolboxLayout.Bounds bounds(dev.osujava.ui.SongSelectToolboxLayout.Bounds b,int index) {
        return new dev.osujava.ui.SongSelectToolboxLayout.Bounds(b.x()+rowOffset(index,b.height()/40),b.y(),b.width(),b.height());
    }
    private static double outBounce(double t) {
        if(t<1/2.75) return 7.5625*t*t;
        if(t<2/2.75) {t-=1.5/2.75;return 7.5625*t*t+.75;}
        if(t<2.5/2.75) {t-=2.25/2.75;return 7.5625*t*t+.9375;}
        t-=2.625/2.75;return 7.5625*t*t+.984375;
    }
}
