package dev.osujava.ui.theme;

/** Centred source crop in normalised image coordinates, without integer-pixel aspect distortion. */
public record ImageCover(float left, float top, float right, float bottom) {
    public static ImageCover crop(int sourceWidth, int sourceHeight, float width, float height) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || !(width > 0) || !(height > 0)
                || !Float.isFinite(width) || !Float.isFinite(height))
            throw new IllegalArgumentException("Positive finite image dimensions required");
        double target = (double) width / height, source = (double) sourceWidth / sourceHeight;
        float u = source > target ? (float) ((1 - target / source) / 2) : 0;
        float v = source < target ? (float) ((1 - source / target) / 2) : 0;
        return new ImageCover(u, v, 1 - u, 1 - v);
    }
}
