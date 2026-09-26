package dev.osujava.gameplay;

/** Pure timing helpers for visuals derived from the beatmap clock. */
public final class GameplayVisualTiming {
    private GameplayVisualTiming() {
    }

    public static double progress(double currentTimeMs, double startTimeMs, double durationMs) {
        if (durationMs <= 0) return currentTimeMs >= startTimeMs ? 1 : 0;
        return clamp((currentTimeMs - startTimeMs) / durationMs);
    }

    public static double approachProgress(double currentTimeMs, double objectTimeMs, double preemptMs) {
        return progress(currentTimeMs, objectTimeMs - Math.max(1, preemptMs), Math.max(1, preemptMs));
    }

    public static double approachRadius(double objectRadius, double progress) {
        return objectRadius * (4 - 3 * clamp(progress));
    }

    /** DrawableHitCircle starts at alpha 0 and reaches 0.9 by twice TimeFadeIn. */
    public static double approachAlpha(double currentTimeMs, double objectTimeMs, double preemptMs) {
        double start = objectTimeMs - preemptMs;
        double duration = Math.min(ApproachTimeCalculator.fadeInMs(preemptMs) * 2, preemptMs);
        double alpha = 0.9 * progress(currentTimeMs, start, duration);
        if (currentTimeMs >= objectTimeMs) alpha *= fadeOutAlpha(currentTimeMs, objectTimeMs, 50);
        return alpha;
    }

    /** Default SnakingSliderBody reaches full path length after one third of preempt. */
    public static double sliderSnakeProgress(double currentTimeMs, double startTimeMs, double preemptMs) {
        return progress(currentTimeMs, startTimeMs - preemptMs, preemptMs / 3);
    }

    public static double spinnerIntroScale(double currentTimeMs, double startTimeMs, double preemptMs) {
        double spawn = startTimeMs - preemptMs;
        if (currentTimeMs < spawn + preemptMs / 2) return 0;
        double p = easeOutQuint(progress(currentTimeMs, spawn + preemptMs / 2, preemptMs / 2));
        return 0.2 + 0.8 * p;
    }

    public static double spinnerCompletionScale(double currentTimeMs, double endTimeMs, boolean hit) {
        double p = progress(currentTimeMs, endTimeMs, 320);
        return hit ? 1 + 0.2 * p : 1 - 0.2 * p;
    }

    /** LegacyMainCirclePiece: 240 ms linear fade and Easing.Out (quadratic) growth. */
    public static double hitCircleScale(double currentTimeMs, double hitTimeMs) {
        return 1 + 0.4 * easeOut(progress(currentTimeMs, hitTimeMs, 240));
    }

    public static double hitCircleAlpha(double currentTimeMs, double hitTimeMs) {
        return currentTimeMs < hitTimeMs ? 0 : fadeOutAlpha(currentTimeMs, hitTimeMs, 240);
    }

    public static double hitCircleNumberScale(double currentTimeMs, double hitTimeMs, double legacyVersion) {
        return legacyVersion > 1 ? 1 : hitCircleScale(currentTimeMs, hitTimeMs);
    }

    public static double hitCircleNumberAlpha(double currentTimeMs, double hitTimeMs, double legacyVersion) {
        return fadeOutAlpha(currentTimeMs, hitTimeMs, legacyVersion > 1 ? 60 : 240);
    }

    /** DrawableHitCircle miss transform. */
    public static double hitCircleMissAlpha(double currentTimeMs, double hitTimeMs) {
        return fadeOutAlpha(currentTimeMs, hitTimeMs, 100);
    }

    public static double easeOut(double progress) {
        double p = clamp(progress);
        return p * (2 - p);
    }

    /** osu-framework DefaultEasingFunction.OutElasticHalf, including endpoint correction. */
    public static double easeOutElasticHalf(double progress) {
        double p = clamp(progress);
        double c = 2 * Math.PI / 0.3;
        double offset = Math.pow(2, -10) * Math.sin((0.5 - 0.3 / 4) * c);
        return Math.pow(2, -10 * p) * Math.sin((0.5 * p - 0.3 / 4) * c) + 1 - offset * p;
    }

    public static double fadeInProgress(double currentTimeMs, double objectTimeMs,
                                        double preemptMs, double fadeInDurationMs) {
        double spawnTime = objectTimeMs - Math.max(1, preemptMs);
        return progress(currentTimeMs, spawnTime, Math.min(Math.max(1, preemptMs), Math.max(1, fadeInDurationMs)));
    }

    public static double fadeOutAlpha(double currentTimeMs, double fadeStartTimeMs, double durationMs) {
        return 1 - progress(currentTimeMs, fadeStartTimeMs, durationMs);
    }

    public static boolean isVisible(double currentTimeMs, double objectTimeMs,
                                    double preemptMs, double endTimeMs, double postemptMs) {
        return currentTimeMs >= objectTimeMs - Math.max(1, preemptMs)
                && currentTimeMs <= endTimeMs + Math.max(0, postemptMs);
    }

    public static double easeOutQuint(double progress) {
        double inverse = 1 - clamp(progress);
        return 1 - inverse * inverse * inverse * inverse * inverse;
    }

    public static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
