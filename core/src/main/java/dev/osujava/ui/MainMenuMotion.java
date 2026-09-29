// Behaviour adapted from osu!lazer (ppy Pty Ltd), MIT; see docs/licenses/ppy-MIT.txt.
package dev.osujava.ui;

/** Easing and independent scale layers. UI durations are milliseconds; beat timing comes from music. */
final class MainMenuMotion {
    private MainMenuMotion() { }
    static float clamp(double value) { return (float) Math.max(0, Math.min(1, value)); }
    static float outExpo(double value) { float t = clamp(value); return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); }
    static float outQuint(double value) { return 1 - (float) Math.pow(1 - clamp(value), 5); }
    static float elastic(double value) {
        float t = clamp(value);
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - .75) * Math.PI * 2 / 3) + 1);
    }
    static float normalized(float value) { return Float.isFinite(value) ? clamp(value) : 0; }
}
