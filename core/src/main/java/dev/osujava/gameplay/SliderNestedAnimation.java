package dev.osujava.gameplay;

/** DrawableSliderTick / LegacyReverseArrow visual transforms, without judgement or timing changes. */
public final class SliderNestedAnimation {
    private SliderNestedAnimation() { }

    public static double arrowScale(double now, double animationStart, double hitTime,
                                     double spanDuration, boolean hit, boolean oldSkin) {
        if (hit && now >= hitTime)
            return 1 + 0.4 * GameplayVisualTiming.easeOut(GameplayVisualTiming.progress(now, hitTime, Math.min(300, spanDuration)));
        double p = loopProgress(now, animationStart);
        return 1.3 - 0.3 * (oldSkin ? p : GameplayVisualTiming.easeOut(p));
    }

    public static double arrowWobble(double now, double animationStart, boolean oldSkin) {
        return oldSkin ? 5.625 - 11.25 * loopProgress(now, animationStart) : 0;
    }

    private static double loopProgress(double now, double start) {
        double elapsed = Math.max(0, now - start);
        return (elapsed % 300) / 300;
    }

    public static double repeatAlpha(double now, double time, double spanDuration, boolean hit) {
        double p = GameplayVisualTiming.progress(now, time, Math.min(300, spanDuration));
        return 1 - (hit ? GameplayVisualTiming.easeOut(p) : p);
    }

    public static double tickScale(double now, SliderNestedVisualTiming timing, boolean judged,
                                    boolean hit, double judgementTime) {
        double initial = tickInitialScale(judged && hit ? Math.min(now, judgementTime) : now, timing);
        if (!judged || !hit || now < judgementTime) return initial;
        return initial * (1 + 0.5 * GameplayVisualTiming.easeOut(
                GameplayVisualTiming.progress(now, judgementTime, 150)));
    }

    private static double tickInitialScale(double now, SliderNestedVisualTiming timing) {
        return 0.5 + 0.5 * GameplayVisualTiming.easeOutElasticHalf(
                GameplayVisualTiming.progress(now, timing.lifetimeStartTimeMs(), 600));
    }

    public static double tickAlpha(double now, SliderNestedVisualTiming timing, boolean judged, double judgementTime) {
        double alpha = timing.alphaAt(judged ? Math.min(now, judgementTime) : now);
        return judged ? alpha * (1 - GameplayVisualTiming.easeOutQuint(
                GameplayVisualTiming.progress(now, judgementTime, 150))) : alpha;
    }
}
