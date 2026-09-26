package dev.osujava.gameplay;

/** Immutable Spinner rendering data prepared by the osu! gameplay session. */
public record SpinnerVisual(
        double centerX,
        double centerY,
        double radius,
        double progress,
        double rotationDegrees,
        double totalRotationDegrees,
        int completedSpins,
        int requiredSpins,
        long startTimeMs,
        double endTimeMs,
        boolean tracking,
        Judgement judgement,
        long preemptMs,
        double spinsPerMinute,
        long completionTimeMs,
        long bonusScore,
        int beatmapIndex) implements HitObjectVisual {
    public SpinnerVisual(double centerX,
        double centerY,
        double radius,
        double progress,
        double rotationDegrees,
        double totalRotationDegrees,
        int completedSpins,
        int requiredSpins,
        long startTimeMs,
        double endTimeMs,
        boolean tracking,
        Judgement judgement,
        long preemptMs,
        double spinsPerMinute,
        long completionTimeMs,
        long bonusScore) {
        this(centerX, centerY, radius, progress, rotationDegrees, totalRotationDegrees, completedSpins,
                requiredSpins, startTimeMs, endTimeMs, tracking, judgement, preemptMs, spinsPerMinute,
                completionTimeMs, bonusScore, -1);
    }

    public SpinnerVisual(double centerX, double centerY, double radius, double progress,
                         double rotationDegrees, double totalRotationDegrees, int completedSpins,
                         int requiredSpins, long startTimeMs, double endTimeMs,
                         boolean tracking, Judgement judgement) {
        this(centerX, centerY, radius, progress, rotationDegrees, totalRotationDegrees, completedSpins,
                requiredSpins, startTimeMs, endTimeMs, tracking, judgement, 600, 0, Long.MIN_VALUE, 0);
    }
}
