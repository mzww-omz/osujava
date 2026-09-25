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
        return objectRadius * (2.5 - 1.5 * clamp(progress));
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
