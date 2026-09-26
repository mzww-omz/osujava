package dev.osujava.gameplay;

import dev.osujava.beatmap.BeatmapPoint;

import java.util.List;

/** Immutable Slider rendering data prepared by the gameplay session. */
public record SliderVisual(
        List<BeatmapPoint> pathPoints,
        BeatmapPoint headPosition,
        BeatmapPoint tailPosition,
        BeatmapPoint ballPosition,
        List<RepeatMarker> repeats,
        double radius,
        double approachRadius,
        double progress,
        long startTimeMs,
        double endTimeMs,
        boolean headJudged,
        boolean headHit,
        boolean tracking,
        long preemptMs,
        int comboNumber,
        long headJudgementTimeMs,
        int comboColorIndex,
        List<TickMarker> ticks,
        double velocity) {

    public SliderVisual(List<BeatmapPoint> pathPoints, BeatmapPoint headPosition, BeatmapPoint tailPosition,
                        BeatmapPoint ballPosition, List<RepeatMarker> repeats, double radius,
                        double approachRadius, double progress, long startTimeMs, double endTimeMs,
                        boolean headJudged, boolean headHit, boolean tracking) {
        this(pathPoints, headPosition, tailPosition, ballPosition, repeats, radius, approachRadius,
                progress, startTimeMs, endTimeMs, headJudged, headHit, tracking, 600, 1, Long.MIN_VALUE,
                0, List.of(), 0.15);
    }

    public SliderVisual {
        pathPoints = List.copyOf(pathPoints);
        repeats = List.copyOf(repeats);
        ticks = List.copyOf(ticks);
    }

    public SliderNestedVisualTiming tailVisualTiming() {
        int repeatIndex = repeats.size();
        double spanDurationMs = (endTimeMs - startTimeMs) / (repeatIndex + 1);
        return SliderNestedVisualTiming.endCircle(startTimeMs, preemptMs, spanDurationMs,
                endTimeMs, repeatIndex, ApproachTimeCalculator.fadeInMs(preemptMs), true);
    }

    public record RepeatMarker(BeatmapPoint position, int spanIndex, boolean judged, double timeMs,
                               SliderNestedVisualTiming visualTiming,
                               SliderNestedVisualTiming reverseArrowTiming) {
        public RepeatMarker(BeatmapPoint position, int spanIndex, boolean judged, double timeMs) {
            this(position, spanIndex, judged, timeMs,
                    new SliderNestedVisualTiming(timeMs, timeMs, 150),
                    new SliderNestedVisualTiming(timeMs, timeMs, 150));
        }

        public RepeatMarker(BeatmapPoint position, int spanIndex, boolean judged) {
            this(position, spanIndex, judged, 0);
        }
    }

    public record TickMarker(BeatmapPoint position, double timeMs, boolean judged, boolean hit,
                             SliderNestedVisualTiming visualTiming) {
        public TickMarker(BeatmapPoint position, double timeMs, boolean judged, boolean hit) {
            this(position, timeMs, judged, hit,
                    new SliderNestedVisualTiming(timeMs, timeMs, 150));
        }
    }
}
