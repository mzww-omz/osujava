package dev.osujava.ui;

/** Stable ranking positions in top-down 480-high coordinates; skin dimensions are in 768 units. */
public record ResultsLayout(float width, float pixelScale, boolean legacy) {
    public static final float IMAGE_SCALE = 1 / 1.6f;
    public static ResultsLayout fit(int width, int height, boolean legacy) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid viewport");
        float scale = height / 480f;
        return new ResultsLayout((float) Math.ceil(width / scale), scale, legacy);
    }
    public float pointer(int pixel) { return pixel / pixelScale; }
    public float panelY() { return legacy ? 46 : 64; }
    public float scoreScale() { return legacy ? 1.05f : 1.3f; }
    public float hitX(int index) { return index < 3 ? 40 : 240; }
    public float hitY(int index) { return 160 + index % 3 * 60; }
    public float countY(int index) { return hitY(index) - (legacy ? 25 : 16); }
    public float labelY() { return legacy ? 312 : 300; }
    public float graphY() { return legacy ? 360 : 380; }
    public float perfectX() { return legacy ? 200 : 260; }
    public float gradeY() { return legacy ? 170 : 200; }
}
