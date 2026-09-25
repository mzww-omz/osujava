package dev.osujava.ruleset.osu;

/** Converts cursor positions into wrapped angular deltas while spinner input is held. */
public final class SpinnerRotationTracker {
    public static final double MINIMUM_CURSOR_RADIUS = 12;

    private final double centerX;
    private final double centerY;
    private final SpinnerSpinHistory history = new SpinnerSpinHistory();
    private Double lastAngleDegrees;
    private double visualRotationDegrees;

    public SpinnerRotationTracker(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public void moveCursor(double cursorX, double cursorY, double timeMs, boolean tracking) {
        double dx = cursorX - centerX;
        double dy = cursorY - centerY;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || Math.hypot(dx, dy) < MINIMUM_CURSOR_RADIUS) {
            lastAngleDegrees = null;
            return;
        }

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (lastAngleDegrees != null && tracking) {
            double delta = normalizeAngleDelta(angle - lastAngleDegrees);
            history.reportDelta(timeMs, delta);
            visualRotationDegrees += delta;
        }
        lastAngleDegrees = angle;
    }

    public double totalRotationDegrees() {
        return history.totalRotationDegrees();
    }

    public double visualRotationDegrees() {
        return visualRotationDegrees;
    }

    public int completedSpins() {
        return history.completedSpins();
    }

    static double normalizeAngleDelta(double delta) {
        if (delta > 180) return delta - 360;
        if (delta < -180) return delta + 360;
        return delta;
    }
}
