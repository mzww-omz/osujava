package dev.osujava.ui;

/** LegacySpinner's centred 640x480 container with Position=(0,-8).
 * OsuPlayfieldAdjustmentContainer: fit 4:3, size .8, scale width/512.
 * This reduces to min(windowWidth/640,windowHeight/480), independently of PlayfieldViewport.
 */
public record LegacySpinnerCoordinates(float left, float top, float unit) {
    public static LegacySpinnerCoordinates fit(float width, float height) {
        float unit = Math.min(width / 640, height / 480);
        return new LegacySpinnerCoordinates((width - 640 * unit) / 2, (height + 480 * unit) / 2 + 8 * unit, unit);
    }
    public float x(double x) { return left + (float) x * unit; }
    public float y(double y) { return top - (float) y * unit; }
    /** osu! positive rotation is clockwise; SpriteBatch uses counter-clockwise Y-up. */
    public static float screenRotation(double degrees) { return (float) -degrees; }
    public float length(double value) { return (float) value * unit; }
}
