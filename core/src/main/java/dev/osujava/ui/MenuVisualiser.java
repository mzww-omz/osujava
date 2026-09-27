// Behaviour adapted from osu!lazer (ppy Pty Ltd), MIT; see docs/licenses/ppy-MIT.txt.
package dev.osujava.ui;

import java.util.Arrays;

/** Lazer LogoVisualisation peak hold and decay, with no rendering or decoding responsibilities. */
public final class MenuVisualiser {
    public static final int BARS = 200, ROUNDS = 5, INDEX_CHANGE = 5;
    public static final double UPDATE_MS = 50;
    public static final float MAX_LENGTH = 600, DECAY_PER_MS = .0024f, DEAD_ZONE = 1f / MAX_LENGTH;
    private final float[] bars = new float[BARS];
    private double untilUpdate;
    private int offset;
    public void reset() { Arrays.fill(bars, 0); untilUpdate = 0; offset = 0; }
    public void advance(double deltaMs, MenuAudioAnalysis source, boolean kiai) {
        // No catch-up sampling: match lazer's scheduler; a stall does not invent audio history.
        decay(Math.max(0, deltaMs));
        untilUpdate -= Math.max(0, deltaMs);
        if (untilUpdate > 0) return;
        untilUpdate = UPDATE_MS;
        float[] bins = source.frequencyAmplitudes();
        for (int i = 0; i < BARS; i++) {
            int index = (i + offset) % BARS;
            float value = index < bins.length ? bins[index] : 0;
            if (!Float.isFinite(value)) value = 0;
            bars[i] = Math.max(bars[i], Math.max(0, Math.min(1, value)) * (kiai ? 1 : .5f));
        }
        offset = (offset + INDEX_CHANGE) % BARS;
    }
    public void decay(double ms) {
        for (int i = 0; i < BARS; i++) bars[i] = Math.max(0, bars[i] - (float) ms * DECAY_PER_MS * (bars[i] + .03f));
    }
    public float[] amplitudes() { return bars.clone(); }
    public static boolean visible(float amplitude) { return amplitude >= DEAD_ZONE; }
    public static double rotation(int bar, int round) { return bar * Math.PI * 2 / BARS + round * Math.PI * 2 / ROUNDS; }
}
