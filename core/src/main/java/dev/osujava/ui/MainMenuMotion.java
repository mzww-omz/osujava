package dev.osujava.ui;

/** UI seconds only: entrance ends at 360ms; no gameplay clock or wall-clock reads. */
final class MainMenuMotion {
    private MainMenuMotion() { }
    static float ease(float time, float delay, float duration) {
        float t = Math.max(0, Math.min(1, (time - delay) / duration));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
    static float background(float time) { return ease(time, 0, .18f); }
    static float cookie(float time) { return ease(time, .035f, .22f); }
    static float strip(float time, int index) { return ease(time, .09f + index * .045f, .225f); }
    static float beat(float time) { return (float) Math.pow(Math.max(0, Math.sin(time * Math.PI * 2)), 5); }
    static float scale(float time, float hover, boolean pressed) {
        return (.94f + .06f * cookie(time)) * (1 + .009f * beat(time) + .014f * hover - (pressed ? .025f : 0));
    }
    static float approach(float current, boolean target, float delta) {
        float step = Math.max(0, delta) / .11f;
        return target ? Math.min(1, current + step) : Math.max(0, current - step);
    }
}
