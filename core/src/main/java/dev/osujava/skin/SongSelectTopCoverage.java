package dev.osujava.skin;

import java.util.function.IntBinaryOperator;

/** Bottom extent of visible top-chrome pixels, scanned once at load (pixel Y points down). */
public final class SongSelectTopCoverage {
    private final int[] depths;
    private final int density;
    private SongSelectTopCoverage(int[] depths, int density) { this.depths = depths; this.density = density; }
    public static SongSelectTopCoverage detect(int width, int height, int density, IntBinaryOperator alpha) {
        int[] depths = new int[width];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++)
            if (alpha.applyAsInt(x,y) >= 16) depths[x] = y + 1;
        return new SongSelectTopCoverage(depths, density);
    }
    /** Includes the repeated right strip when the requested interval extends past the original image. */
    public float depth(float start, float end) {
        int from = Math.max(0, (int)Math.floor(start * density));
        int to = Math.min(depths.length, (int)Math.ceil(end * density));
        int depth = 0;
        for (int x = from; x < to; x++) depth = Math.max(depth, depths[x]);
        if (end * density > depths.length)
            for (int x = Math.max(0, depths.length - 20 * density); x < depths.length; x++)
                depth = Math.max(depth, depths[x]);
        return depth / (float)density;
    }
}
