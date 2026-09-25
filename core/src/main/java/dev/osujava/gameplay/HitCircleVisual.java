package dev.osujava.gameplay;

public record HitCircleVisual(double x, double y, double radius, double approachRadius, long timeMs,
                              long preemptMs, int comboNumber) {
    public HitCircleVisual(double x, double y, double radius, double approachRadius, long timeMs) {
        this(x, y, radius, approachRadius, timeMs, 600, 1);
    }
}
