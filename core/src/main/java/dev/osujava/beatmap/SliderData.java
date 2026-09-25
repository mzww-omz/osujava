package dev.osujava.beatmap;

import java.util.List;
import java.util.Objects;

/** Slider-specific data retained from a legacy .osu HitObjects line. */
public record SliderData(List<Segment> segments, int repeatCount, double pixelLength) {
    public SliderData {
        segments = List.copyOf(Objects.requireNonNull(segments));
        if (segments.isEmpty()) throw new IllegalArgumentException("A slider must have a path segment");
        if (repeatCount < 0) throw new IllegalArgumentException("Slider repeat count cannot be negative");
        if (!Double.isFinite(pixelLength) || pixelLength < 0) {
            throw new IllegalArgumentException("Slider pixel length must be finite and non-negative");
        }
    }

    /** The number of one-way traversals encoded by the .osu `slides` field. */
    public int spanCount() {
        return repeatCount + 1;
    }

    public CurveType curveType() {
        return segments.getFirst().curveType();
    }

    /** Returns all absolute control points in segment order, retaining duplicate Bezier separators. */
    public List<BeatmapPoint> controlPoints() {
        return segments.stream().flatMap(segment -> segment.controlPoints().stream()).toList();
    }

    public record Segment(CurveType curveType, int degree, List<BeatmapPoint> controlPoints) {
        public Segment {
            Objects.requireNonNull(curveType);
            controlPoints = List.copyOf(Objects.requireNonNull(controlPoints));
            if (controlPoints.isEmpty()) throw new IllegalArgumentException("A slider segment needs control points");
            if (degree < 0) throw new IllegalArgumentException("Curve degree cannot be negative");
        }
    }

    /** Curve letters used by legacy osu! beatmaps; B followed by a number is a degree-specific B-spline. */
    public enum CurveType {
        BEZIER,
        BSPLINE,
        LINEAR,
        PERFECT,
        CATMULL
    }
}
