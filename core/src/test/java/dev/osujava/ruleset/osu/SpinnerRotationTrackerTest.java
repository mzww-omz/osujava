package dev.osujava.ruleset.osu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpinnerRotationTrackerTest {
    private static final double CENTER_X = 256;
    private static final double CENTER_Y = 192;
    private static final double RADIUS = 80;

    @Test
    void accumulatesClockwiseAndCounterClockwiseRotation() {
        SpinnerRotationTracker clockwise = trackerAtAngle(0, 0);
        clockwise.moveCursor(CENTER_X, CENTER_Y + RADIUS, 10, true);
        assertEquals(90, clockwise.totalRotationDegrees(), 1e-6);

        SpinnerRotationTracker counterClockwise = trackerAtAngle(0, 0);
        counterClockwise.moveCursor(CENTER_X, CENTER_Y - RADIUS, 10, true);
        assertEquals(90, counterClockwise.totalRotationDegrees(), 1e-6);
        assertEquals(-90, counterClockwise.visualRotationDegrees(), 1e-6);
    }

    @Test
    void normalizesAcrossTheZeroDegreeBoundary() {
        SpinnerRotationTracker tracker = trackerAtAngle(179, 0);
        tracker.moveCursor(pointX(-179), pointY(-179), 10, true);

        assertEquals(2, tracker.totalRotationDegrees(), 1e-6);
        assertEquals(2, tracker.visualRotationDegrees(), 1e-6);
    }

    @Test
    void stationaryCursorDoesNotAccumulateRotation() {
        SpinnerRotationTracker tracker = trackerAtAngle(45, 0);
        double x = pointX(45);
        double y = pointY(45);
        tracker.moveCursor(x, y, 10, true);
        tracker.moveCursor(x, y, 20, true);

        assertEquals(0, tracker.totalRotationDegrees(), 1e-6);
    }

    @Test
    void ignoresUnstableMovementAtTheSpinnerCenter() {
        SpinnerRotationTracker tracker = trackerAtAngle(0, 0);
        tracker.moveCursor(CENTER_X + 2, CENTER_Y + 2, 10, true);
        tracker.moveCursor(CENTER_X, CENTER_Y, 20, true);
        tracker.moveCursor(CENTER_X - 3, CENTER_Y + 1, 30, true);
        tracker.moveCursor(CENTER_X, CENTER_Y + RADIUS, 40, true);

        assertEquals(0, tracker.totalRotationDegrees(), 1e-6);
    }

    @Test
    void directionChangesDoNotTurnBackAndForthIntoExtraSpins() {
        SpinnerSpinHistory history = new SpinnerSpinHistory();
        history.reportDelta(10, 40);
        history.reportDelta(20, -90);
        history.reportDelta(30, 110);

        assertEquals(60, history.totalRotationDegrees(), 1e-6);
        assertEquals(0, history.completedSpins());
    }

    private SpinnerRotationTracker trackerAtAngle(double angleDegrees, double timeMs) {
        SpinnerRotationTracker tracker = new SpinnerRotationTracker(CENTER_X, CENTER_Y);
        tracker.moveCursor(pointX(angleDegrees), pointY(angleDegrees), timeMs, false);
        return tracker;
    }

    private double pointX(double angleDegrees) {
        return CENTER_X + Math.cos(Math.toRadians(angleDegrees)) * RADIUS;
    }

    private double pointY(double angleDegrees) {
        return CENTER_Y + Math.sin(Math.toRadians(angleDegrees)) * RADIUS;
    }
}
