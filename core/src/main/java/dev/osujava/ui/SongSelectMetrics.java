package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets;

/** Song Select geometry in UiLayout's 720-high logical units, never framebuffer pixels.
 * These bounded density/curve choices are osujava's; they are not measured Stable constants.
 * Skin chrome alone uses the documented 768-high legacy asset canvas.
 */
final class SongSelectMetrics {
    static final float LEGACY_CANVAS_HEIGHT = 768;
    static final float DEFAULT_ROW_HEIGHT = 80;
    static final float MIN_ROW_HEIGHT = 76, MAX_ROW_HEIGHT = 88;
    static final float SET_PITCH = .96f, DIFFICULTY_PITCH = 1.02f;
    static final float GROUP_INDENT = .052f;
    static final float SELECTED_INDENT = 3, HOVER_INDENT = 7;
    static final float SELECTION_SPACING = .025f, HOVER_SPACING = .04f;
    static final float CURVE_ORIGIN = .61f, CURVE_DEPTH = .065f;
    static final float MIN_ROW_X = .50f, MAX_ROW_X = .70f;

    private SongSelectMetrics() { }
    static float rowWidth(float width) { return width * .50f + 18; }
    static float rowHeight(float width, SongSelectSkinAssets skin) {
        var image = skin == null ? null : skin.get(SongSelectSkinAssets.Image.MENU_BUTTON_BACKGROUND);
        float height = image == null ? DEFAULT_ROW_HEIGHT
                : SongSelectCarousel.skinRowHeight(image.logicalWidth(), image.logicalHeight(), rowWidth(width))
                    * skin.rowBody().height() / skin.rowBody().width();
        return Math.max(MIN_ROW_HEIGHT, Math.min(MAX_ROW_HEIGHT, height));
    }
    /** Keep one row of context when paging; paging changes viewport, not selection. */
    static float pageDistance(float viewportHeight, float rowHeight) {
        return Math.max(rowHeight, viewportHeight - rowHeight);
    }
}
