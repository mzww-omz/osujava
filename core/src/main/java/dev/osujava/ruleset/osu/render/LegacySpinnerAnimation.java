package dev.osujava.ruleset.osu.render;

import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.SpinnerVisual;
import dev.osujava.skin.SkinConfiguration.Rgb;

/** Absolute-clock evaluation of LegacySpinner transforms. No renderer-owned event detection. */
public final class LegacySpinnerAnimation {
    public enum Style { OLD, NEW, FALLBACK }
    public static final float SPRITE_SCALE = .625f;
    public static final float TOP_OFFSET = 45 - 16;
    public static final float Y_CENTRE = TOP_OFFSET + 219;
    public static final float METRE_HEIGHT = 692 * SPRITE_SCALE;
    public static Style select(boolean background, boolean top) {
        return background ? Style.OLD : top ? Style.NEW : Style.FALLBACK;
    }
    public static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    public static double out(double x) { x = clamp(x); return x * (2 - x); }
    private static double phase(double now, double start, double duration) {
        return duration <= 0 ? now >= start ? 1 : 0 : clamp((now - start) / duration);
    }
    public static double wholeAlpha(SpinnerVisual s, double now) {
        double fade = ApproachTimeCalculator.fadeInMs(s.preemptMs());
        return phase(now, s.startTimeMs() - fade, fade) * (1 - phase(now, s.endTimeMs(), 240));
    }
    public static double approachScale(SpinnerVisual s, double now) {
        return SPRITE_SCALE * (1.86 - 1.76 * phase(now, s.startTimeMs(), s.endTimeMs() - s.startTimeMs()));
    }
    public static double spinAlpha(SpinnerVisual s, double now) {
        double fade = ApproachTimeCalculator.fadeInMs(s.preemptMs()) / 2;
        double finalFade = Math.min(400, s.endTimeMs() - s.startTimeMs());
        double finalStart = s.endTimeMs() - finalFade;
        double value = phase(now, s.startTimeMs() - fade, fade);
        // TargetGroupingTransformTracker.AddTransform removes following Alpha transforms.
        // A tick before the scheduled final fade cancels that fade; it must never resurrect SPIN.
        double previousTime = Double.NaN, previousAlpha = 0;
        for (var event : s.spinEvents()) {
            if (event.timeMs() > now) break;
            double atEvent;
            if (!Double.isNaN(previousTime))
                atEvent = previousAlpha * (1 - phase(event.timeMs(), previousTime, 300));
            else if (event.timeMs() >= finalStart)
                atEvent = 1 - phase(event.timeMs(), finalStart, finalFade);
            else
                atEvent = phase(event.timeMs(), s.startTimeMs() - fade, fade);
            previousTime = event.timeMs(); previousAlpha = atEvent;
        }
        if (!Double.isNaN(previousTime)) value = previousAlpha * (1 - phase(now, previousTime, 300));
        else if (now >= finalStart) value = 1 - phase(now, finalStart, finalFade);
        return value;
    }
    public static double clearAlpha(SpinnerVisual s, double now) {
        if (s.completionTimeMs() == Long.MIN_VALUE || now < s.completionTimeMs()) return 0;
        double start = Math.min(s.completionTimeMs(), s.endTimeMs() - 400);
        if (now >= s.endTimeMs() - 50)
            return out(phase(s.endTimeMs() - 50, start, 400)) * (1 - phase(now, s.endTimeMs() - 50, 50));
        return out(phase(now, start, 400));
    }
    public static double clearScale(SpinnerVisual s, double now) {
        double age = now - Math.min(s.completionTimeMs(), s.endTimeMs() - 400);
        return age < 240 ? SPRITE_SCALE * (2 - 1.2 * out(age / 240))
                : SPRITE_SCALE * (.8 + .2 * phase(age, 240, 160));
    }
    public static double spmOffset(SpinnerVisual s, double now) {
        double fade = ApproachTimeCalculator.fadeInMs(s.preemptMs());
        return 50 * (1 - out(phase(now, s.startTimeMs() - fade, fade)));
    }
    public static String spmText(double spm) { return Long.toString((long) spm); }
    public static double topRotation(double rotation, boolean middle2) { return rotation * (middle2 ? .5 : 1); }
    public static double bottomRotation(double rotation, boolean middle2) { return topRotation(rotation, middle2) / 3; }
    public static double middle2Rotation(double rotation) { return rotation; }
    public static double fixedRotation() { return 0; }
    public static double progressScale(double progress) { return SPRITE_SCALE * (.8 + out(progress) * .2); }
    public static double glowAlpha(double progress) { return clamp(progress); }
    public static Rgb fixedColour(SpinnerVisual s, double now) {
        float channel = srgb(1 - phase(now, s.startTimeMs(), s.endTimeMs() - s.startTimeMs()));
        return new Rgb(1, channel, channel);
    }
    public static SpinnerVisual.SpinEvent lastBonus(SpinnerVisual s, double now, boolean tickOnly) {
        SpinnerVisual.SpinEvent result = null;
        for (var e : s.spinEvents()) {
            if (e.timeMs() > now) break;
            if (e.legacyBonusScore() > 0 && (!tickOnly || e.bonusTick())) result = e;
        }
        return result;
    }
    public static double bonusAlpha(SpinnerVisual.SpinEvent e, double now) {
        return e == null ? 0 : 1 - out(phase(now, e.timeMs(), e.maximumBonus() ? 500 : 800));
    }
    public static double bonusScale(SpinnerVisual.SpinEvent e, double now) {
        if (e == null) return SPRITE_SCALE;
        return e.maximumBonus() ? 1.4 + .4 * out(phase(now, e.timeMs(), 1000))
                : SPRITE_SCALE * (2 - .72 * out(phase(now, e.timeMs(), 800)));
    }
    public static Rgb glowColour(SpinnerVisual s, double now) {
        var e = lastBonus(s, now, true);
        double t = e == null ? 1 : phase(now, e.timeMs(), 200);
        return new Rgb(mixWhite(3 / 255f, t), mixWhite(151 / 255f, t), 1);
    }
    private static float mixWhite(float target, double t) {
        double linear = target <= .04045 ? target / 12.92 : Math.pow((target + .055) / 1.055, 2.4);
        return srgb(1 + (linear - 1) * t);
    }
    private static float srgb(double linear) {
        return (float) (linear <= .0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - .055);
    }
    /** Same ten-bar Bernoulli distribution; sampling is indexed by absolute ms and object identity. */
    public static int metreBars(double progress, boolean noBlink, long now, int objectIndex) {
        int percent = (int) (clamp(progress) * 100);
        if (!noBlink) percent = Math.min(99, percent);
        int bars = percent / 10;
        if (!noBlink && sample(now, objectIndex) < (percent % 10) / 10.0) bars++;
        return bars;
    }
    private static double sample(long now, int index) {
        long z = now ^ ((long) index * 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
    private LegacySpinnerAnimation() { }
}
