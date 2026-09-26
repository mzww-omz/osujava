package dev.osujava.skin;

/** Pure legacy Slider Ball animation/size rules; velocity is supplied by SliderTiming. */
public final class LegacySliderBallAnimation {
    public static final double MIN_FRAME_DURATION_MS = 1000 / 60.0;
    public static final int MAX_LOGICAL_SIZE = 128 * 3;

    private LegacySliderBallAnimation() { }

    /** LegacySliderBall.onHitObjectApplied: velocity is in osu! pixels per millisecond. */
    public static double frameDurationMs(double velocity) {
        if (!Double.isFinite(velocity) || velocity <= 0) return MIN_FRAME_DURATION_MS;
        return Math.max(0.15 / velocity * MIN_FRAME_DURATION_MS, MIN_FRAME_DURATION_MS);
    }

    /** SkinnableTextureAnimation uses the parent object's start time minus its preempt. */
    public static int frameIndex(int frameCount, double nowMs, double startTimeMs,
                                  double preemptMs, double velocity) {
        if (frameCount <= 0) return -1;
        double elapsed = Math.max(0, nowMs - (startTimeMs - preemptMs));
        return (int) (Math.floor(elapsed / frameDurationMs(velocity)) % frameCount);
    }

    /** Native logical pixels are scaled by OsuHitObject.Scale (radius / 64). */
    public static float screenScale(double radius, float viewportScale) {
        return (float) (radius / 64) * viewportScale;
    }

    /** Legacy WithMaximumSize centre-crops excess pixels, keeping the remaining pixels un-stretched. */
    public static SpriteSize spriteSize(int pixelWidth, int pixelHeight, int density,
                                        double radius, float viewportScale) {
        float width = Math.min(pixelWidth, MAX_LOGICAL_SIZE * density);
        float height = Math.min(pixelHeight, MAX_LOGICAL_SIZE * density);
        float scale = screenScale(radius, viewportScale) / density;
        float u = (pixelWidth - width) / (2 * pixelWidth);
        float v = (pixelHeight - height) / (2 * pixelHeight);
        return new SpriteSize(width * scale, height * scale, u, 1 - v, 1 - u, v);
    }

    public record SpriteSize(float width, float height, float u, float v, float u2, float v2) { }
}
