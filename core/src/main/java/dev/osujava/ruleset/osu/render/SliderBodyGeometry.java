package dev.osujava.ruleset.osu.render;

import dev.osujava.beatmap.BeatmapPoint;
import java.util.ArrayList;
import java.util.List;

/** Static capsule bounds. The fragment shader resolves the union using centreline distance. */
public final class SliderBodyGeometry {
    public static final int FLOATS_PER_VERTEX = 8;
    public static final int VERTICES_PER_SEGMENT = 6;
    public record Segment(BeatmapPoint start, BeatmapPoint end, double before, double after) {
        public BeatmapPoint endAt(double limit) {
            double t = after == before ? 0 : Math.max(0, Math.min(1, (limit - before) / (after - before)));
            return new BeatmapPoint(start.x() + (end.x() - start.x()) * t,
                    start.y() + (end.y() - start.y()) * t);
        }
    }
    private final List<Segment> segments;
    private final float[] vertices;
    private final double length;
    public record Bounds(double minX, double minY, double maxX, double maxY) { }
    private final Bounds bounds;

    public SliderBodyGeometry(List<BeatmapPoint> path) {
        List<Segment> result = new ArrayList<>();
        double distance = 0;
        for (int i = 1; i < path.size(); i++) {
            var a = path.get(i - 1);
            var b = path.get(i);
            double size = Math.hypot(b.x() - a.x(), b.y() - a.y());
            if (size == 0) continue;
            result.add(new Segment(a, b, distance, distance + size));
            distance += size;
        }
        if (result.isEmpty() && !path.isEmpty()) result.add(new Segment(path.getFirst(), path.getFirst(), 0, 0));
        double minX = 0, minY = 0, maxX = 0, maxY = 0;
        if (!path.isEmpty()) {
            minX = maxX = path.getFirst().x(); minY = maxY = path.getFirst().y();
            for (var point : path) {
                minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y());
            }
        }
        bounds = new Bounds(minX, minY, maxX, maxY);
        segments = List.copyOf(result);
        length = distance;
        vertices = new float[segments.size() * VERTICES_PER_SEGMENT * FLOATS_PER_VERTEX];
        float[] corners = {-1,-1, 1,-1, 1,1, -1,-1, 1,1, -1,1};
        int index = 0;
        for (Segment s : segments) for (int v = 0; v < VERTICES_PER_SEGMENT; v++) {
            vertices[index++] = corners[v * 2]; vertices[index++] = corners[v * 2 + 1];
            vertices[index++] = (float) s.start.x(); vertices[index++] = (float) s.start.y();
            vertices[index++] = (float) s.end.x(); vertices[index++] = (float) s.end.y();
            vertices[index++] = (float) s.before; vertices[index++] = (float) s.after;
        }
    }
    public Bounds bounds() { return bounds; }
    public List<Segment> segments() { return segments; }
    public float[] vertices() { return vertices.clone(); }
    public double limit(double progress) { return length * Math.max(0, Math.min(1, progress)); }
    public int visibleSegments(double progress) {
        if (progress <= 0 || segments.isEmpty()) return 0;
        if (length == 0) return 1;
        double limit = limit(progress);
        int low = 0, high = segments.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (segments.get(mid).before < limit) low = mid + 1; else high = mid;
        }
        return low;
    }
    /** Mirrors the GPU capsule distance, including the clipped round end cap. */
    public double distanceAt(double x, double y, double progress) {
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0, n = visibleSegments(progress); i < n; i++) {
            var s = segments.get(i);
            var b = s.endAt(limit(progress));
            double dx = b.x() - s.start.x(), dy = b.y() - s.start.y();
            double squared = dx * dx + dy * dy;
            double t = squared == 0 ? 0 : Math.max(0, Math.min(1,
                    ((x - s.start.x()) * dx + (y - s.start.y()) * dy) / squared));
            distance = Math.min(distance, Math.hypot(x - s.start.x() - dx * t, y - s.start.y() - dy * t));
        }
        return distance;
    }
}
