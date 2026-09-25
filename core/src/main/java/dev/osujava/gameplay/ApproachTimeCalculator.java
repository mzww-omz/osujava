package dev.osujava.gameplay;

public final class ApproachTimeCalculator {
    private ApproachTimeCalculator() {
    }

    public static long preemptMs(double approachRate) {
        double ar = Math.max(0, Math.min(10, approachRate));
        double preempt = ar < 5 ? 1200 + 120 * (5 - ar) : 1200 - 150 * (ar - 5);
        return Math.round(preempt);
    }
}
