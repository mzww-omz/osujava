package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapPoint;

/** DrawableSliderBall.UpdateProgress tangent sampling, in osu!'s downward-positive coordinates. */
public final class SliderBallRotation {
    private SliderBallRotation() { }

    public static double at(SliderPath path, SliderTiming timing, double now, double previousDegrees) {
        if (path.distance() <= 0 || timing.durationMs() <= 0) return previousDegrees;
        double completion = Math.max(0, Math.min(1, (now - timing.startTimeMs()) / timing.durationMs()));
        double checkDistance = 0.1 / path.distance();
        BeatmapPoint a = path.positionAt(timing.progressAt(timing.startTimeMs()
                + Math.min(1 - checkDistance, completion) * timing.durationMs()));
        BeatmapPoint b = path.positionAt(timing.progressAt(timing.startTimeMs()
                + Math.min(1, completion + checkDistance) * timing.durationMs()));
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        if (Math.hypot(dx, dy) < 0.01) return previousDegrees;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        // Equivalent rotations stay continuous across atan2's +/-180 boundary.
        return previousDegrees + Math.IEEEremainder(angle - previousDegrees, 360);
    }
}
