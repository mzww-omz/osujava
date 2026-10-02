package dev.osujava.ui;

/** Song Select geometry in UiLayout's 720-high logical units, never framebuffer pixels.
 * Carousel parameters use a 480-high space (see songselect-parity-phase4-20260929).
 * Skin artwork uses the 768-high legacy asset canvas independently of row pitch.
 */
final class SongSelectMetrics {
    static final float LEGACY_CANVAS_HEIGHT = 768;
    static final float CAROUSEL_HEIGHT = 480;
    static final float ROW_PITCH = 48;
    static final float FIRST_ROW_Y = 200;
    static final float SELECTION_Y = 220;
    static final float ROW_RIGHT_OFFSET = 340;
    static final float HOVER_INDENT = 45, HOVER_SPACING = 10;
    static final float OPEN_INDENT = 50, OPEN_SPACING = 10;
    static final float ROW_DEPTH_START = .6f, ROW_DEPTH_STEP = .00003f;

    private SongSelectMetrics() { }
    static float rowWidth(float width, float height) {
        // Cover the right edge even at maximum hover/group indentation. A half-screen
        // body ended inside the default 1100x720 window when the row moved left.
        return Math.max(width * .50f + 18, width - wheelLeft(width, height) + 18);
    }
    static float rowHeight(float height) {
        // Navigation and label clipping use row pitch. Artwork has its own native canvas.
        return rowPitch(height);
    }
    static float wheelLeft(float width, float height) {
        float leftmostBody = width - (ROW_RIGHT_OFFSET + OPEN_INDENT + HOVER_INDENT) * carouselScale(height);
        return Math.min(width * .50f, leftmostBody);
    }
    static float carouselScale(float height) { return height / CAROUSEL_HEIGHT; }
    static float rowPitch(float height) { return ROW_PITCH * carouselScale(height); }
    static float selectionAnchor(float screenHeight, float top, float viewportHeight) {
        // Screen-down Y=220 on the 480-high canvas, converted to distance below the content top.
        return Math.max(0, Math.min(viewportHeight, top - screenHeight + SELECTION_Y * carouselScale(screenHeight)));
    }
    static float curveX(float screenDown, float width, float height) {
        // Static row indentation grows linearly away from screen center; bounded off-screen too.
        float scale = carouselScale(height);
        float indent = Math.min(200 * scale, Math.abs(screenDown - height / 2) * (75f / 480));
        return width - ROW_RIGHT_OFFSET * scale + indent;
    }
}
