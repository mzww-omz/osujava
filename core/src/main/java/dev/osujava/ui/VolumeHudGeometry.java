package dev.osujava.ui;

import dev.osujava.audio.AudioVolumes;

/** Reused unit-circle vertices, starting at twelve o'clock and proceeding clockwise. */
final class VolumeHudGeometry {
    static final int SEGMENTS = 192;
    private static final float[] X = new float[SEGMENTS + 1], Y = new float[SEGMENTS + 1];
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            X[i] = (float) Math.sin(angle);
            Y[i] = (float) Math.cos(angle);
        }
        X[SEGMENTS] = X[0]; Y[SEGMENTS] = Y[0];
    }
    static float end(float volume) { return AudioVolumes.clamp(volume) * SEGMENTS; }
    static float x(float index) { return interpolate(X, index); }
    static float y(float index) { return interpolate(Y, index); }
    private static float interpolate(float[] points, float index) {
        int whole = (int) index;
        if (whole >= SEGMENTS) return points[SEGMENTS];
        return points[whole] + (points[whole + 1] - points[whole]) * (index - whole);
    }
}
