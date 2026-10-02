package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Song selection artwork geometry, in UiLayout coordinates (bottom-left in libGDX).
 * The legacy chrome canvas is 768 high; unlike selection buttons, bottom art stretches only in X.
 * Wiki: https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection
 */
final class SongSelectChrome {
    static final float MAX_TOP_FRACTION = .40f, MAX_BOTTOM_FRACTION = .30f;
    private SongSelectChrome() { }
    record Bounds(float x, float y, float width, float height) { }
    record TopExtension(Bounds bounds, float u, float u2) { }
    record Content(float rankingHeaderTop, float carouselTop, float bottom) { }
    static Content content(float width, float height, SongSelectSkinAssets skin) {
        float scale = height / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
        float left = skin == null ? 0 : skin.topDepth(0, width * .52f / scale) * scale;
        float right = skin == null ? 0 : skin.topDepth(width * .55f / scale, width / scale) * scale;
        var bottom = skin == null ? null : skin.get(Image.BOTTOM);
        return content(height, left, right, bottom == null ? 0 : bottom.logicalHeight() * scale);
    }
    static Content content(float height, float leftDepth, float rightDepth, float bottomDepth) {
        // Authored artwork keeps native drawing bounds. It must not reserve the whole viewport.
        // Extreme canvases keep native scale inside a bounded clip; this is an osujava policy.
        return new Content(height - reservation(leftDepth + 8, 130, height * MAX_TOP_FRACTION),
                height - reservation(rightDepth + 4, 84, height * MAX_TOP_FRACTION), bottomReservation(height, bottomDepth));
    }
    static float bottomReservation(float height, float depth) {
        return reservation(depth, bottomHeight(height), height * MAX_BOTTOM_FRACTION);
    }
    private static float reservation(float depth, float minimum, float maximum) {
        return Math.min(maximum, Math.max(minimum, Float.isFinite(depth) ? depth : minimum));
    }
    static Bounds topClip(float width, float height) {
        // Keep native scale, but honour the same safety ceiling as content reservation.
        return new Bounds(0, height * (1 - MAX_TOP_FRACTION), width, height * MAX_TOP_FRACTION);
    }
    static Bounds bottomClip(float width, float height) {
        return new Bounds(0, 0, width, height * MAX_BOTTOM_FRACTION);
    }
    static float bottomHeight(float height) { return height * (84f / 720); }
    static float cookieRadius(float height) { return height * .135f; }
    static float cookieX(float width, float radius) { return width - radius * .32f; }
    static float cookieY(float radius) { return radius * .42f; }
    static boolean procedural(SongSelectSkinAssets skin, Image image) {
        return skin == null || skin.get(image) == null;
    }
    static Bounds top(float width, float height, SongSelectSkinAssets.SkinTexture asset) {
        float scale = height / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
        return new Bounds(0, height - asset.logicalHeight() * scale,
                asset.logicalWidth() * scale, asset.logicalHeight() * scale);
    }
    /** 0600136e: display width gate/position; 060040af/b1: logical crop -> physical UV. */
    static TopExtension topExtension(float width, float height, int windowWidth, SongSelectSkinAssets.SkinTexture asset) {
        if (windowWidth <= 1366 || asset.logicalWidth() <= 0 || asset.logicalHeight() <= 0) return null;
        float scale = height / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
        var bounds = new Bounds(1365f * width / windowWidth,height - asset.logicalHeight() * scale,
                (windowWidth - 1365) * scale,asset.logicalHeight() * scale);
        float u = 1365f * asset.density() / asset.texture().getWidth();
        float u2 = 1366f * asset.density() / asset.texture().getWidth();
        return new TopExtension(bounds,u,u2);
    }
}
