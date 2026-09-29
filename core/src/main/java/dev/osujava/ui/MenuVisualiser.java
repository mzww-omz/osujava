package dev.osujava.ui;

import java.util.Arrays;

/** Stable's 1024 FFT spokes, four turns, 10ms index shifts and exponential peak decay. */
public final class MenuVisualiser {
    public static final int BARS = 1024;
    private final float[] bars = new float[BARS];
    private double segmentMs;
    private int offset;
    public void reset() { Arrays.fill(bars, 0); segmentMs = 0; offset = 0; }
    public void advance(double deltaMs, MenuAudioAnalysis source, boolean kiai) {
        if (!Double.isFinite(deltaMs) || deltaMs <= 0 || deltaMs > 1000) return;
        double remaining = Math.max(0, deltaMs);
        float[] bins = source.frequencyAmplitudes();
        do {
            double step = Math.min(remaining, 10 - segmentMs);
            double decay = Math.pow(.95, step / (1000.0 / 60));
            for (int i = 0; i < BARS; i++) {
                int bin = (BARS - 1 - i + offset) % BARS;
                float value = source.available() && bin < bins.length ? bins[bin] : 0;
                if (!Float.isFinite(value)) value = 0;
                bars[i] = (float) (Math.max(bars[i], Math.max(0, value) * 1.6f * 3) * decay);
                if (bars[i] < .01f) bars[i] = 0;
            }
            remaining -= step; segmentMs += step;
            // stable rotates only when it passes the 10ms boundary.
            if (segmentMs >= 10 && remaining > 0) { segmentMs = 0; offset = (offset + 50) % BARS; }
        } while (remaining > 0);
    }
    public float[] amplitudes() { return bars.clone(); }
    public static float opacity(float length) { return .4f * MainMenuMotion.clamp((length - .04f) / .08f); }
    public static double rotation(int bar) { return Math.PI * 2 * (.4 + bar * 4.0 / BARS); }
}
