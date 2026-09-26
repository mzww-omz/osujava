package dev.osujava.gameplay;

/** LegacyRulesetExtensions.CalculateScaleFromCircleSize(CS, true) and OsuHitObject radius. */
public final class OsuObjectGeometry {
    private static final double LEGACY_ALLOWANCE = 1.00041;

    private OsuObjectGeometry() {
    }

    public static double radius(double circleSize) {
        double cs = Math.max(0, Math.min(10, circleSize));
        return 64 * (1 - 0.7 * (cs - 5) / 5) / 2 * LEGACY_ALLOWANCE;
    }

    public static double stackOffsetPerHeight(double circleSize) {
        return -6.4 * radius(circleSize) / 64;
    }
}
