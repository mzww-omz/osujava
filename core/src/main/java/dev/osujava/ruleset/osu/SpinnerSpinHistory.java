package dev.osujava.ruleset.osu;

import java.util.ArrayDeque;
import java.util.Deque;

/** Counts spins like lazer: direction changes undo progress before they can add more spins. */
public final class SpinnerSpinHistory {
    private static final double SPIN_COMPLETION_EPSILON = 1e-6;
    private final Deque<CompletedSpin> completedSpins = new ArrayDeque<>();
    private double totalAccumulatedRotation;
    private double rotationAtLastCompletion;
    private double currentSpinMaxRotation;
    private double lastReportTime = Double.NEGATIVE_INFINITY;

    public double totalRotationDegrees() {
        return completedSpins.size() * 360.0 + currentSpinMaxRotation;
    }

    public int completedSpins() {
        return completedSpins.size();
    }

    public void reportDelta(double currentTimeMs, double deltaDegrees) {
        if (deltaDegrees == 0 || !Double.isFinite(deltaDegrees) || !Double.isFinite(currentTimeMs)) return;

        totalAccumulatedRotation += deltaDegrees;
        if (currentTimeMs >= lastReportTime) {
            currentSpinMaxRotation = Math.max(currentSpinMaxRotation, Math.abs(currentSpinRotation()));
            while (currentSpinMaxRotation >= 360 - SPIN_COMPLETION_EPSILON) {
                int direction = (int) Math.signum(currentSpinRotation());
                completedSpins.push(new CompletedSpin(currentTimeMs, direction));
                rotationAtLastCompletion += direction * 360;
                currentSpinMaxRotation = Math.abs(currentSpinRotation());
            }
        } else {
            while (!completedSpins.isEmpty() && completedSpins.peek().completionTimeMs() > currentTimeMs) {
                CompletedSpin removed = completedSpins.pop();
                rotationAtLastCompletion -= removed.direction() * 360;
            }
            currentSpinMaxRotation = Math.abs(currentSpinRotation());
        }
        lastReportTime = currentTimeMs;
    }

    private double currentSpinRotation() {
        return totalAccumulatedRotation - rotationAtLastCompletion;
    }

    private record CompletedSpin(double completionTimeMs, int direction) {
    }
}
