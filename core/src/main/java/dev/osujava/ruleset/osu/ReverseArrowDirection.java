package dev.osujava.ruleset.osu;

/** DrawableSliderRepeat direction towards the first distinct point of the currently snaked curve. */
public final class ReverseArrowDirection {
    private ReverseArrowDirection() { }

    public static double at(SliderPath path, double progress, boolean atEnd) {
        var position = path.positionAt(progress);
        var points = path.sampledPoints();
        double distance = atEnd ? path.distance() : 0;
        for (int i = atEnd ? points.size() - 1 : 0; i >= 0 && i < points.size(); i += atEnd ? -1 : 1) {
            var point = points.get(i);
            boolean visible = !atEnd || distance < progress * path.distance();
            if (atEnd && i > 0) distance -= Math.hypot(point.x() - points.get(i - 1).x(), point.y() - points.get(i - 1).y());
            double dx = point.x() - position.x(), dy = point.y() - position.y();
            if (!visible || Math.hypot(dx, dy) < 0.01) continue;
            return Math.toDegrees(Math.atan2(dy, dx));
        }
        return 0;
    }
}
