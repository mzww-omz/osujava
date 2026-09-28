package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Song selection artwork geometry, in UiLayout coordinates (bottom-left in libGDX).
 * The legacy chrome canvas is 768 high; unlike selection buttons, bottom art stretches only in X.
 * Wiki: https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection
 */
final class SongSelectChrome {
    private SongSelectChrome() { }
    record Bounds(float x, float y, float width, float height) { }
    record Content(float rankingHeaderTop, float carouselTop, float bottom) { }
    static Content content(float width, float height, SongSelectSkinAssets skin) {
        float scale = height / 768f;
        float left = skin == null ? 0 : skin.topDepth(0, width * .52f / scale) * scale;
        float right = skin == null ? 0 : skin.topDepth(width * .55f / scale, width / scale) * scale;
        var bottom = skin == null ? null : skin.get(Image.BOTTOM);
        return content(height, left, right, bottom == null ? 0 : bottom.logicalHeight() * scale);
    }
    static Content content(float height, float leftDepth, float rightDepth, float bottomDepth) {
        return new Content(height - Math.max(130, leftDepth + 8),
                height - Math.max(62, rightDepth + 4), Math.max(bottomHeight(height), bottomDepth));
    }
    static float bottomHeight(float height) { return height * (84f / 720); }
    static float cookieRadius(float height) { return height * .135f; }
    static float cookieX(float width, float radius) { return width - radius * .32f; }
    static float cookieY(float radius) { return radius * .42f; }
    static boolean procedural(SongSelectSkinAssets skin, Image image) {
        return skin == null || skin.get(image) == null;
    }
    static Bounds top(float width, float height, SongSelectSkinAssets.SkinTexture asset) {
        float scale = height / 768f;
        return new Bounds(0, height - asset.logicalHeight() * scale,
                asset.logicalWidth() * scale, asset.logicalHeight() * scale);
    }
}
