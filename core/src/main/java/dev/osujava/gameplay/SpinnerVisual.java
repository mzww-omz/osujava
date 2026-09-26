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
        int beatmapIndex, java.util.List<SpinEvent> spinEvents) implements HitObjectVisual {
    /** Emitted at the existing full-spin boundary, without changing score or input. */
    public record SpinEvent(long timeMs, long legacyBonusScore, boolean maximumBonus, boolean bonusTick) { }
    public SpinnerVisual { spinEvents = java.util.List.copyOf(spinEvents); }
    public SpinnerVisual(double centerX, double centerY, double radius, double progress, double rotationDegrees,
                         double totalRotationDegrees, int completedSpins, int requiredSpins, long startTimeMs,
                         double endTimeMs, boolean tracking, Judgement judgement, long preemptMs,
                         double spinsPerMinute, long completionTimeMs, long bonusScore, int beatmapIndex) {
        this(centerX, centerY, radius, progress, rotationDegrees, totalRotationDegrees, completedSpins,
                requiredSpins, startTimeMs, endTimeMs, tracking, judgement, preemptMs, spinsPerMinute,
                completionTimeMs, bonusScore, beatmapIndex, java.util.List.of());
    }

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
