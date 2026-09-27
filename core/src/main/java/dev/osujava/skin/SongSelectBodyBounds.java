package dev.osujava.skin;

import java.util.function.IntBinaryOperator;

/** Normalised substantial-alpha body; transparent padding and soft shadow stay in the image. */
public record SongSelectBodyBounds(float left, float bottom, float width, float height) {
    public static final SongSelectBodyBounds FULL = new SongSelectBodyBounds(0, 0, 1, 1);

    /** Scan once during asset loading, never during drawing. Pixel Y runs downwards. */
    public static SongSelectBodyBounds detect(int width, int height, IntBinaryOperator alpha) {
        if (width <= 0 || height <= 0) return FULL;
        int left = width, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (alpha.applyAsInt(x, y) < 160) continue;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        // Sparse decoration and extremely transparent artwork have no reliable body: use the image.
        if (right - left + 1 < width * .35f || bottom - top + 1 < height * .35f) return FULL;
        return new SongSelectBodyBounds(left / (float) width, (height - bottom - 1f) / height,
                (right - left + 1f) / width, (bottom - top + 1f) / height);
    }
}
