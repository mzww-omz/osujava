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
        List<TickMarker> ticks) {

    public SliderVisual(List<BeatmapPoint> pathPoints, BeatmapPoint headPosition, BeatmapPoint tailPosition,
                        BeatmapPoint ballPosition, List<RepeatMarker> repeats, double radius,
                        double approachRadius, double progress, long startTimeMs, double endTimeMs,
                        boolean headJudged, boolean headHit, boolean tracking) {
        this(pathPoints, headPosition, tailPosition, ballPosition, repeats, radius, approachRadius,
                progress, startTimeMs, endTimeMs, headJudged, headHit, tracking, 600, 1, Long.MIN_VALUE,
                0, List.of());
    }

    public SliderVisual {
        pathPoints = List.copyOf(pathPoints);
        repeats = List.copyOf(repeats);
        ticks = List.copyOf(ticks);
    }

    public record RepeatMarker(BeatmapPoint position, int spanIndex, boolean judged, double timeMs) {
        public RepeatMarker(BeatmapPoint position, int spanIndex, boolean judged) {
            this(position, spanIndex, judged, 0);
        }
    }

    public record TickMarker(BeatmapPoint position, double timeMs, boolean judged, boolean hit) {
    }
}
