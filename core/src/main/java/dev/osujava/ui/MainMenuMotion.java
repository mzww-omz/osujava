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
    static float beatScale(MenuBeatTiming.Beat beat, float amplitude) {
        // 60ms early contraction (Easing.Out = quadratic), then two-beat OutQuint recovery.
        double shifted = (beat.positionMs() + 60 - beat.originMs()) / beat.lengthMs();
        long index = (long) Math.floor(shifted);
        if (index < 0) return 1;
        double elapsed = (shifted - index) * beat.lengthMs();
        float depth = .02f * Math.min(1, .4f + normalized(amplitude));
        float previous = 1 - depth * (1 - outQuint((beat.lengthMs() - 60) / (beat.lengthMs() * 2)));
        if (elapsed < 60) {
            float ease = 1 - (float) Math.pow(1 - elapsed / 60, 2);
            return previous + (1 - depth - previous) * ease;
        }
        return 1 - depth * (1 - outQuint((elapsed - 60) / (beat.lengthMs() * 2)));
    }
    static float amplitudeTarget(float maximum) { return 1 - Math.max(0, normalized(maximum) - .4f) * .04f; }
    static float damp(float current, float target, double ms) {
        return target + (current - target) * (float) Math.pow(.9, Math.max(0, ms));
    }
}
