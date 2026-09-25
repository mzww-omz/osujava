package dev.osujava.ui.theme;

/** Virtual coordinates retain a 720 unit baseline while giving narrow windows usable width. */
public record UiLayout(float width, float height, float scale) {
    public static UiLayout fromPixels(int pixelWidth, int pixelHeight) {
        if (pixelWidth <= 0 || pixelHeight <= 0) throw new IllegalArgumentException("Window size must be positive");
        float scale = Math.min(pixelHeight / 720f, pixelWidth / 960f);
        return new UiLayout(pixelWidth / scale, pixelHeight / scale, scale);
    }

    public float pointerX(int pixelX) { return pixelX / scale; }
    public float pointerY(int pixelY) { return height - pixelY / scale; }
    public float contentWidth() { return Math.min(width - UiTheme.PAD * 2f, 1440f); }
    public float contentX() { return (width - contentWidth()) / 2f; }
}
