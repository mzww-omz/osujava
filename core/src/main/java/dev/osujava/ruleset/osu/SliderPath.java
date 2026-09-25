package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.SliderData;

import java.util.ArrayList;
import java.util.List;

/** Immutable osu! Slider path sampled into piecewise-linear points for distance-based lookup. */
public final class SliderPath {
    private static final int SAMPLES_PER_CURVE = 50;
    private static final int MAX_RECURSION = 12;
    private static final double FLATNESS = 0.35;

    private final List<BeatmapPoint> points;
    private final double[] cumulativeDistance;
    private final double distance;

    public SliderPath(double startX, double startY, SliderData data) {
        List<BeatmapPoint> sampled = new ArrayList<>();
        for (SliderData.Segment segment : data.segments()) {
            List<BeatmapPoint> controlPoints = segment.controlPoints();
            if (controlPoints.isEmpty()) continue;
            switch (segment.curveType()) {
                case LINEAR -> appendPoints(sampled, controlPoints);
                case BEZIER -> appendBezierWithBreaks(sampled, controlPoints);
                case PERFECT -> appendPerfect(sampled, controlPoints);
                case CATMULL -> appendCatmull(sampled, controlPoints);
                case BSPLINE -> appendBSpline(sampled, controlPoints, segment.degree());
            }
        }
        if (sampled.isEmpty()) sampled.add(new BeatmapPoint(startX, startY));
        List<BeatmapPoint> finalControls = data.segments().getLast().controlPoints();
        boolean repeatedTerminalPoint = finalControls.size() >= 2
                && same(finalControls.get(finalControls.size() - 1), finalControls.get(finalControls.size() - 2));
        points = List.copyOf(fitDistance(sampled, data.pixelLength(), repeatedTerminalPoint));
        cumulativeDistance = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulativeDistance[i] = cumulativeDistance[i - 1] + distance(points.get(i - 1), points.get(i));
        }
        distance = cumulativeDistance[cumulativeDistance.length - 1];
    }

    /** Distance-adjusted path length in playfield pixels. */
    public double distance() {
        return distance;
    }

    /** Sampled points are useful to render the same geometry that progress uses. */
    public List<BeatmapPoint> sampledPoints() {
        return points;
    }

    /** Returns a position at normalized arc-length progress. Values outside [0, 1] are clamped. */
    public BeatmapPoint positionAt(double progress) {
        if (points.size() == 1 || distance <= 0) return points.getFirst();
        double target = Math.max(0, Math.min(1, progress)) * distance;
        int low = 0;
        int high = cumulativeDistance.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulativeDistance[middle] < target) low = middle + 1;
            else high = middle;
        }
        if (low == 0) return points.getFirst();
        double before = cumulativeDistance[low - 1];
        double segmentLength = cumulativeDistance[low] - before;
        if (segmentLength <= 1e-9) return points.get(low);
        double fraction = (target - before) / segmentLength;
        BeatmapPoint a = points.get(low - 1);
        BeatmapPoint b = points.get(low);
        return interpolate(a, b, fraction);
    }

    private void appendBezierWithBreaks(List<BeatmapPoint> output, List<BeatmapPoint> controlPoints) {
        List<BeatmapPoint> current = new ArrayList<>();
        current.add(controlPoints.getFirst());
        for (int i = 1; i < controlPoints.size(); i++) {
            BeatmapPoint previous = controlPoints.get(i - 1);
            BeatmapPoint point = controlPoints.get(i);
            if (current.size() > 1 && same(previous, point)) {
                appendBezier(output, current);
                current = new ArrayList<>();
                current.add(point);
            } else {
                current.add(point);
            }
        }
        appendBezier(output, current);
    }

    private void appendBezier(List<BeatmapPoint> output, List<BeatmapPoint> controlPoints) {
        if (controlPoints.size() <= 2) {
            appendPoints(output, controlPoints);
            return;
        }
        appendDistinct(output, controlPoints.getFirst());
        flattenBezier(output, controlPoints, 0);
    }

    private void flattenBezier(List<BeatmapPoint> output, List<BeatmapPoint> controls, int depth) {
        BeatmapPoint first = controls.getFirst();
        BeatmapPoint last = controls.getLast();
        double flatness = 0;
        for (int i = 1; i < controls.size() - 1; i++) {
            flatness = Math.max(flatness, distanceToSegment(controls.get(i), first, last));
        }
        if (depth >= MAX_RECURSION || flatness <= FLATNESS) {
            appendDistinct(output, last);
            return;
        }

        List<BeatmapPoint> left = new ArrayList<>();
        List<BeatmapPoint> right = new ArrayList<>();
        List<BeatmapPoint> row = new ArrayList<>(controls);
        left.add(row.getFirst());
        right.add(row.getLast());
        while (row.size() > 1) {
            List<BeatmapPoint> next = new ArrayList<>(row.size() - 1);
            for (int i = 0; i < row.size() - 1; i++) next.add(interpolate(row.get(i), row.get(i + 1), 0.5));
            row = next;
            left.add(row.getFirst());
            right.add(row.getLast());
        }
        java.util.Collections.reverse(right);
        flattenBezier(output, left, depth + 1);
        flattenBezier(output, right, depth + 1);
    }

    private void appendCatmull(List<BeatmapPoint> output, List<BeatmapPoint> controlPoints) {
        if (controlPoints.size() <= 2) {
            appendPoints(output, controlPoints);
            return;
        }
        appendDistinct(output, controlPoints.getFirst());
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            BeatmapPoint p0 = controlPoints.get(Math.max(0, i - 1));
            BeatmapPoint p1 = controlPoints.get(i);
            BeatmapPoint p2 = controlPoints.get(i + 1);
            BeatmapPoint p3 = controlPoints.get(Math.min(controlPoints.size() - 1, i + 2));
            for (int sample = 1; sample <= SAMPLES_PER_CURVE; sample++) {
                double t = (double) sample / SAMPLES_PER_CURVE;
                double t2 = t * t;
                double t3 = t2 * t;
                double x = 0.5 * (2 * p1.x() + (-p0.x() + p2.x()) * t
                        + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2
                        + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
                double y = 0.5 * (2 * p1.y() + (-p0.y() + p2.y()) * t
                        + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2
                        + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
                appendDistinct(output, new BeatmapPoint(x, y));
            }
        }
    }

    private void appendPerfect(List<BeatmapPoint> output, List<BeatmapPoint> controlPoints) {
        if (controlPoints.size() != 3) {
            appendBezier(output, controlPoints);
            return;
        }
        BeatmapPoint a = controlPoints.get(0);
        BeatmapPoint b = controlPoints.get(1);
        BeatmapPoint c = controlPoints.get(2);
        double determinant = 2 * (a.x() * (b.y() - c.y()) + b.x() * (c.y() - a.y()) + c.x() * (a.y() - b.y()));
        if (Math.abs(determinant) < 1e-8) {
            appendBezier(output, controlPoints);
            return;
        }
        double aa = a.x() * a.x() + a.y() * a.y();
        double bb = b.x() * b.x() + b.y() * b.y();
        double cc = c.x() * c.x() + c.y() * c.y();
        double centerX = (aa * (b.y() - c.y()) + bb * (c.y() - a.y()) + cc * (a.y() - b.y())) / determinant;
        double centerY = (aa * (c.x() - b.x()) + bb * (a.x() - c.x()) + cc * (b.x() - a.x())) / determinant;
        double startAngle = Math.atan2(a.y() - centerY, a.x() - centerX);
        double middleAngle = Math.atan2(b.y() - centerY, b.x() - centerX);
        double endAngle = Math.atan2(c.y() - centerY, c.x() - centerX);
        double ccwSweep = positiveAngle(endAngle - startAngle);
        double middleSweep = positiveAngle(middleAngle - startAngle);
        double sweep = middleSweep <= ccwSweep ? ccwSweep : ccwSweep - Math.PI * 2;
        double radius = Math.hypot(a.x() - centerX, a.y() - centerY);
        int count = Math.max(SAMPLES_PER_CURVE, (int) Math.ceil(Math.abs(sweep) * radius / 2));
        appendDistinct(output, a);
        for (int i = 1; i <= count; i++) {
            double angle = startAngle + sweep * i / count;
            appendDistinct(output, new BeatmapPoint(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle)));
        }
    }

    private void appendBSpline(List<BeatmapPoint> output, List<BeatmapPoint> controlPoints, int requestedDegree) {
        if (controlPoints.size() <= 2) {
            appendPoints(output, controlPoints);
            return;
        }
        int degree = requestedDegree == 0 ? controlPoints.size() - 1 : Math.min(requestedDegree, controlPoints.size() - 1);
        if (degree <= 0) {
            appendPoints(output, controlPoints);
            return;
        }
        int last = controlPoints.size() - 1;
        int knotCount = last + degree + 2;
        double[] knots = new double[knotCount];
        int interiorCount = knotCount - 2 * (degree + 1);
        for (int i = 0; i < knotCount; i++) {
            if (i <= degree) knots[i] = 0;
            else if (i >= knotCount - degree - 1) knots[i] = 1;
            else knots[i] = (double) (i - degree) / (interiorCount + 1);
        }
        appendDistinct(output, controlPoints.getFirst());
        for (int span = degree; span <= last; span++) {
            double low = knots[span];
            double high = knots[span + 1];
            if (high <= low) continue;
            for (int sample = 1; sample <= SAMPLES_PER_CURVE; sample++) {
                double t = low + (high - low) * sample / SAMPLES_PER_CURVE;
                appendDistinct(output, deBoor(controlPoints, knots, degree, span, t));
            }
        }
    }

    private BeatmapPoint deBoor(List<BeatmapPoint> points, double[] knots, int degree, int span, double t) {
        BeatmapPoint[] work = new BeatmapPoint[degree + 1];
        for (int j = 0; j <= degree; j++) work[j] = points.get(span - degree + j);
        for (int r = 1; r <= degree; r++) {
            for (int j = degree; j >= r; j--) {
                int index = span - degree + j;
                double denominator = knots[index + degree - r + 1] - knots[index];
                double alpha = denominator <= 1e-12 ? 0 : (t - knots[index]) / denominator;
                work[j] = interpolate(work[j - 1], work[j], alpha);
            }
        }
        return work[degree];
    }

    private List<BeatmapPoint> fitDistance(List<BeatmapPoint> source, double expected, boolean repeatedTerminalPoint) {
        List<BeatmapPoint> result = new ArrayList<>();
        appendDistinct(result, source.getFirst());
        double actual = 0;
        for (int i = 1; i < source.size(); i++) actual += distance(source.get(i - 1), source.get(i));
        // osu!lazer treats an encoded length of zero as no expected-distance constraint.
        if (expected <= 0 || Math.abs(actual - expected) < 1e-7) {
            appendPoints(result, source.subList(1, source.size()));
            return result;
        }
        if (expected < actual) {
            double accumulated = 0;
            for (int i = 1; i < source.size(); i++) {
                BeatmapPoint a = source.get(i - 1);
                BeatmapPoint b = source.get(i);
                double segment = distance(a, b);
                if (accumulated + segment >= expected) {
                    double fraction = segment == 0 ? 0 : (expected - accumulated) / segment;
                    appendDistinct(result, interpolate(a, b, fraction));
                    break;
                }
                appendDistinct(result, b);
                accumulated += segment;
            }
            return result;
        }

        appendPoints(result, source.subList(1, source.size()));
        if (repeatedTerminalPoint) return result;
        if (source.size() < 2) return result;
        BeatmapPoint previous = source.get(source.size() - 2);
        BeatmapPoint end = source.getLast();
        double lastSegment = distance(previous, end);
        if (lastSegment > 1e-9 && !same(previous, end)) {
            double extension = expected - actual;
            appendDistinct(result, new BeatmapPoint(end.x() + (end.x() - previous.x()) / lastSegment * extension,
                    end.y() + (end.y() - previous.y()) / lastSegment * extension));
        }
        return result;
    }

    private static void appendPoints(List<BeatmapPoint> output, List<BeatmapPoint> points) {
        for (BeatmapPoint point : points) appendDistinct(output, point);
    }

    private static void appendDistinct(List<BeatmapPoint> output, BeatmapPoint point) {
        if (output.isEmpty() || !same(output.getLast(), point)) output.add(point);
    }

    private static double distanceToSegment(BeatmapPoint point, BeatmapPoint start, BeatmapPoint end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double denominator = dx * dx + dy * dy;
        if (denominator <= 1e-12) return distance(point, start);
        double projection = Math.max(0, Math.min(1, ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / denominator));
        return distance(point, new BeatmapPoint(start.x() + projection * dx, start.y() + projection * dy));
    }

    private static double positiveAngle(double angle) {
        double full = Math.PI * 2;
        angle %= full;
        return angle < 0 ? angle + full : angle;
    }

    private static BeatmapPoint interpolate(BeatmapPoint a, BeatmapPoint b, double fraction) {
        return new BeatmapPoint(a.x() + (b.x() - a.x()) * fraction, a.y() + (b.y() - a.y()) * fraction);
    }

    private static double distance(BeatmapPoint a, BeatmapPoint b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    private static boolean same(BeatmapPoint a, BeatmapPoint b) {
        return Math.abs(a.x() - b.x()) < 1e-9 && Math.abs(a.y() - b.y()) < 1e-9;
    }
}
