package dev.osujava.ui;

import dev.osujava.score.ScoreDetails.HealthPoint;
import java.util.ArrayList;
import java.util.List;

/** Stable 06001c18 / 06001c1a: decimation and reveal by polyline length, independent of storage. */
public final class ResultsHealthGraph {
    public record Segment(float x1, float y1, float x2, float y2, boolean healthy) {
        double length() { return Math.hypot(x2 - x1, y2 - y1); }
    }
    private final List<Segment> segments;
    private final double length;
    public ResultsHealthGraph(List<HealthPoint> samples) {
        var lines = new ArrayList<Segment>();
        if (samples != null && samples.size() >= 2) {
            float start = samples.getFirst().timeMs(), end = samples.getLast().timeMs();
            if (end > start) {
                var points = new ArrayList<>(samples);
                while (points.size() > 100)
                    for (int i = points.size() - 1; i >= 0; i -= 2) points.remove(i);
                for (int i = 1; i < points.size(); i++) {
                    var a = points.get(i - 1); var b = points.get(i);
                    lines.add(new Segment((a.timeMs() - start) / (end - start) * 186, (1 - a.value()) * 86,
                            (b.timeMs() - start) / (end - start) * 186, (1 - b.value()) * 86, b.value() > .5f));
                }
            }
        }
        segments = List.copyOf(lines); length = segments.stream().mapToDouble(Segment::length).sum();
    }
    public List<Segment> reveal(float fraction) {
        if (!Float.isFinite(fraction) || fraction <= 0) return List.of();
        if (fraction >= 1) return segments;
        double remaining = length * fraction;
        var visible = new ArrayList<Segment>();
        for (var segment : segments) {
            double size = segment.length();
            if (remaining >= size) { visible.add(segment); remaining -= size; }
            else {
                float t = (float) (remaining / size);
                visible.add(new Segment(segment.x1, segment.y1, segment.x1 + (segment.x2 - segment.x1) * t,
                        segment.y1 + (segment.y2 - segment.y1) * t, segment.healthy));
                break;
            }
        }
        return List.copyOf(visible);
    }
}
