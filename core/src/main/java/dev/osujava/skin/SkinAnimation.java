package dev.osujava.skin;

/** Pure sampling of the official AnimationFramerate contract. Drawing never advances time. */
public final class SkinAnimation {
    private SkinAnimation() { }
    public static int frameIndex(int count, int framesPerSecond, double elapsedSeconds) {
        if (count <= 1 || !Double.isFinite(elapsedSeconds) || elapsedSeconds <= 0) return 0;
        double rate = framesPerSecond > 0 ? framesPerSecond : count;
        double duration = count / rate;
        return Math.min(count - 1, (int)Math.floor((elapsedSeconds % duration) * rate));
    }
}
