package dev.osujava.gameplay;

/** Display lifetime and fade-in timing for one Slider nested object. */
public record SliderNestedVisualTiming(double lifetimeStartTimeMs,
                                       double fadeInStartTimeMs,
                                       double fadeInDurationMs) {
    private static final double REPEAT_TICK_PREEMPT_OFFSET_MS = 200;
    private static final double TICK_FADE_IN_MS = 150;
    private static final double REVERSE_ARROW_FADE_IN_MS = 150;

    /** Mirrors SliderTick's span-aware TimePreempt calculation in osu!lazer. */
    public static SliderNestedVisualTiming tick(double sliderStartTimeMs, double sliderPreemptMs,
                                                double spanDurationMs, int spanIndex, double tickTimeMs) {
        double spanStartTimeMs = sliderStartTimeMs + spanIndex * spanDurationMs;
        double offsetMs = spanIndex > 0
                ? REPEAT_TICK_PREEMPT_OFFSET_MS
                : sliderPreemptMs * 0.66;
        double tickPreemptMs = (tickTimeMs - spanStartTimeMs) / 2 + offsetMs;
        double lifetimeStartTimeMs = tickTimeMs - tickPreemptMs;
        return new SliderNestedVisualTiming(lifetimeStartTimeMs, lifetimeStartTimeMs, TICK_FADE_IN_MS);
    }

    /** Mirrors SliderEndCircle.TimePreempt and its delayed first-span fade-in. */
    public static SliderNestedVisualTiming endCircle(double sliderStartTimeMs, double sliderPreemptMs,
                                                     double spanDurationMs, double endTimeMs,
                                                     int repeatIndex, double timeFadeInMs,
                                                     boolean snakingIn) {
        double lifetimeStartTimeMs = endCircleLifetimeStart(
                sliderStartTimeMs, sliderPreemptMs, spanDurationMs, endTimeMs, repeatIndex);
        double fadeDelayMs = snakingIn && repeatIndex == 0 ? sliderPreemptMs / 3 : 0;
        double fadeInDurationMs = repeatIndex > 0 ? 0 : timeFadeInMs;
        return new SliderNestedVisualTiming(lifetimeStartTimeMs,
                lifetimeStartTimeMs + fadeDelayMs, fadeInDurationMs);
    }

    /** The reverse arrow uses its own 150 ms fade, while sharing the repeat's lifetime. */
    public static SliderNestedVisualTiming reverseArrow(double sliderStartTimeMs, double sliderPreemptMs,
                                                        double spanDurationMs, double repeatTimeMs,
                                                        int repeatIndex, boolean snakingIn) {
        double lifetimeStartTimeMs = endCircleLifetimeStart(
                sliderStartTimeMs, sliderPreemptMs, spanDurationMs, repeatTimeMs, repeatIndex);
        double fadeDelayMs = snakingIn && repeatIndex == 0 ? sliderPreemptMs / 3 : 0;
        double fadeInDurationMs = repeatIndex > 0
                ? Math.min(spanDurationMs, REVERSE_ARROW_FADE_IN_MS)
                : REVERSE_ARROW_FADE_IN_MS;
        return new SliderNestedVisualTiming(lifetimeStartTimeMs,
                lifetimeStartTimeMs + fadeDelayMs, fadeInDurationMs);
    }

    public double alphaAt(double currentTimeMs) {
        if (currentTimeMs < lifetimeStartTimeMs) return 0;
        return GameplayVisualTiming.progress(currentTimeMs, fadeInStartTimeMs, fadeInDurationMs);
    }

    private static double endCircleLifetimeStart(double sliderStartTimeMs, double sliderPreemptMs,
                                                 double spanDurationMs, double endTimeMs,
                                                 int repeatIndex) {
        double timePreemptMs = repeatIndex > 0
                ? spanDurationMs * 2
                : sliderPreemptMs + endTimeMs - sliderStartTimeMs;
        return endTimeMs - timePreemptMs;
    }
}
