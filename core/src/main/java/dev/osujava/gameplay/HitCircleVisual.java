package dev.osujava.gameplay;

public record HitCircleVisual(double x, double y, double radius, double approachRadius, long timeMs,
                              long preemptMs, int comboNumber, int comboColorIndex, int beatmapIndex,
                              Judgement judgement, double judgementTimeMs) implements HitObjectVisual {
    public HitCircleVisual(double x, double y, double radius, double approachRadius, long timeMs,
                           long preemptMs, int comboNumber, int comboColorIndex, int beatmapIndex) {
        this(x, y, radius, approachRadius, timeMs, preemptMs, comboNumber, comboColorIndex,
                beatmapIndex, null, Double.NaN);
    }

    public HitCircleVisual(double x, double y, double radius, double approachRadius, long timeMs,
                              long preemptMs, int comboNumber, int comboColorIndex) {
        this(x, y, radius, approachRadius, timeMs, preemptMs, comboNumber, comboColorIndex, -1);
    }

    @Override public long startTimeMs() { return timeMs; }

    public HitCircleVisual(double x, double y, double radius, double approachRadius, long timeMs) {
        this(x, y, radius, approachRadius, timeMs, 600, 1, 0);
    }
}
