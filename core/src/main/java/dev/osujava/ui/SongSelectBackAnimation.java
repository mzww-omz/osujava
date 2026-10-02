package dev.osujava.ui;

/** Back's own sprite epoch, independent of the screen and gameplay clocks.
 * 06001b1a: no entrance transforms on Back, so the first update owns the epoch. */
final class SongSelectBackAnimation {
    private double epochMs = Double.NaN;
    private int frame;
    private int geometryFrame;

    void update(double clockMs, int count, int framesPerSecond) {
        // 06001b19 runs the base geometry update before 06001b1a swaps textures.
        geometryFrame = frame;
        if (count < 2) { frame = 0; return; }
        if (!Double.isFinite(clockMs)) return;
        if (Double.isNaN(epochMs) || clockMs < epochMs) epochMs = clockMs;
        // 06001b1b computes the interval in Single before storing it as Double.
        double interval = 1000f / (framesPerSecond > 0 ? framesPerSecond : count);
        frame = (int)(Math.floor((clockMs - epochMs) / interval) % count);
    }
    int frame() { return frame; }
    int geometryFrame() { return geometryFrame; }
}
