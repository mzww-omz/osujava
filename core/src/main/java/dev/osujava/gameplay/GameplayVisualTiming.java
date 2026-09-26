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

    /** Default MainCirclePiece grows to 1.5 over 400 ms after a hit. */
    public static double hitCircleScale(double currentTimeMs, double hitTimeMs) {
        double progress = progress(currentTimeMs, hitTimeMs, 400);
        return 1 + 0.5 * (1 - (1 - progress) * (1 - progress));
    }

    /** Default MainCirclePiece fades its explosion over 800 ms after a 40 ms flash. */
    public static double hitCircleAlpha(double currentTimeMs, double hitTimeMs) {
        if (currentTimeMs < hitTimeMs) return 0;
        return fadeOutAlpha(currentTimeMs, hitTimeMs + 40, 800);
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
