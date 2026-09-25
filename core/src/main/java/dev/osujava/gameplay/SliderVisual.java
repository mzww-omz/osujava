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
        boolean tracking) {

    public SliderVisual {
        pathPoints = List.copyOf(pathPoints);
        repeats = List.copyOf(repeats);
    }

    public record RepeatMarker(BeatmapPoint position, int spanIndex, boolean judged) {
    }
}
