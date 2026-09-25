package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;

/** Beatmap-clock timing for one osu! Slider. */
public final class SliderTiming {
    private final long startTimeMs;
    private final int spanCount;
    private final double spanDurationMs;
    private final double velocity;
    private final double tickDistance;
    private final boolean ticksEnabled;

    private SliderTiming(long startTimeMs, int spanCount, double spanDurationMs, double velocity,
                         double tickDistance, boolean ticksEnabled) {
        this.startTimeMs = startTimeMs;
        this.spanCount = spanCount;
        this.spanDurationMs = spanDurationMs;
        this.velocity = velocity;
        this.tickDistance = tickDistance;
        this.ticksEnabled = ticksEnabled;
    }

    public static SliderTiming calculate(BeatmapDifficulty difficulty, HitObject object, SliderPath path) {
        if (object.sliderData() == null) throw new IllegalArgumentException("HitObject has no Slider data");

        double beatLength = 500;
        double sliderVelocityMultiplier = 1;
        boolean ticksEnabled = true;
        for (TimingPoint point : difficulty.timingPoints()) {
            if (point.timeMs() > object.timeMs()) break;
            double value = point.beatLength();
            if (point.uninherited()) {
                if (Double.isFinite(value) && value > 0) beatLength = value;
                sliderVelocityMultiplier = 1;
                ticksEnabled = !Double.isNaN(value);
            } else if (Double.isNaN(value)) {
                ticksEnabled = false;
            } else {
                sliderVelocityMultiplier = value < 0 ? clamp(100 / -value, 0.1, 10) : 1;
                ticksEnabled = true;
            }
        }

        double multiplier = difficulty.settings().sliderMultiplier();
        if (!Double.isFinite(multiplier) || multiplier <= 0) multiplier = 1.4;
        double effectiveBeatLength = beatLength * sliderVelocityMultiplier;
        double velocity = 100 * multiplier / effectiveBeatLength;
        if (!Double.isFinite(velocity) || velocity <= 0) velocity = 100 * 1.4 / 500;

        double spanDuration = path.distance() / velocity;
        double tickRate = difficulty.settings().sliderTickRate();
        double tickDistance = tickRate > 0 && Double.isFinite(tickRate)
                ? velocity * beatLength / tickRate : Double.POSITIVE_INFINITY;
        int spanCount = path.distance() <= 1e-7 ? 1 : object.sliderData().spanCount();
        return new SliderTiming(object.timeMs(), spanCount, spanDuration, velocity,
                tickDistance, ticksEnabled && Double.isFinite(tickDistance) && tickDistance > 0);
    }

    public long startTimeMs() {
        return startTimeMs;
    }

    public int spanCount() {
        return spanCount;
    }

    public double spanDurationMs() {
        return spanDurationMs;
    }

    public double durationMs() {
        return spanDurationMs * spanCount;
    }

    public double endTimeMs() {
        return startTimeMs + durationMs();
    }

    /** Distance travelled in one millisecond, used for end leniency and tick spacing. */
    public double velocity() {
        return velocity;
    }

    public double tickDistance() {
        return tickDistance;
    }

    public boolean ticksEnabled() {
        return ticksEnabled;
    }

    /** Current forward or reverse path progress for an absolute beatmap time. */
    public double progressAt(double beatmapTimeMs) {
        if (beatmapTimeMs < startTimeMs) return 0;
        if (spanDurationMs <= 0 || beatmapTimeMs >= endTimeMs()) return endProgress();
        double elapsed = beatmapTimeMs - startTimeMs;
        int span = Math.min(spanCount - 1, (int) (elapsed / spanDurationMs));
        double spanProgress = Math.max(0, Math.min(1, (elapsed - span * spanDurationMs) / spanDurationMs));
        return span % 2 == 0 ? spanProgress : 1 - spanProgress;
    }

    public double endProgress() {
        return spanCount % 2 == 1 ? 1 : 0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
