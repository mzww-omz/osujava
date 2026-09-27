package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectBodyBounds;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Song selection artwork geometry, in UiLayout coordinates (bottom-left in libGDX).
 * The legacy chrome canvas is 768 high; unlike selection buttons, bottom art stretches only in X.
 * Wiki: https://osu.ppy.sh/wiki/en/Skinning/Interface#song-selection
 */
final class SongSelectChrome {
    private SongSelectChrome() { }
    record Bounds(float x, float y, float width, float height) { }
    record Bottom(Bounds skinBounds, float controlBaseline, float actionHeight,
                  float intentionalOverlap, Bounds back, Bounds importAction, Bounds random,
                  Bounds cookie) { }
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
    static Bottom bottom(float width, float height, SongSelectSkinAssets skin) {
        return bottom(width, height, skin == null ? null : skin.get(Image.BOTTOM),
                skin == null ? null : skin.get(Image.BACK), skin == null ? null : skin.get(Image.RANDOM),
                skin == null ? SongSelectBodyBounds.FULL : skin.actionBody(Image.BACK),
                skin == null ? SongSelectBodyBounds.FULL : skin.actionBody(Image.RANDOM));
    }
    static Bottom bottom(float width, float height, SongSelectSkinAssets.SkinTexture asset,
                         SongSelectSkinAssets.SkinTexture back, SongSelectSkinAssets.SkinTexture random) {
        return bottom(width, height, asset, back, random, SongSelectBodyBounds.FULL, SongSelectBodyBounds.FULL);
    }
    static Bottom bottom(float width, float height, SongSelectSkinAssets.SkinTexture asset,
                         SongSelectSkinAssets.SkinTexture back, SongSelectSkinAssets.SkinTexture random,
                         SongSelectBodyBounds backBody, SongSelectBodyBounds randomBody) {
        float canvasScale = height / 720f;
        float skinHeight = asset == null ? bottomHeight(height) : asset.logicalHeight() * height / 768f;
        float actionHeight = 80 * canvasScale;
        // Share one image height. A narrow Back/Random slot constrains both, never just one action.
        var actions = new SongSelectSkinAssets.SkinTexture[]{back, random};
        var bodies = new SongSelectBodyBounds[]{backBody, randomBody};
        float[] slotWidths = {154 * canvasScale, 82 * canvasScale};
        for (int i = 0; i < actions.length; i++) {
            var action = actions[i];
            if (action != null) actionHeight = Math.min(actionHeight,
                    slotWidths[i] * action.logicalHeight() * bodies[i].height() / action.logicalWidth());
        }
        float baseline = 0;
        float radius = cookieRadius(height);
        return new Bottom(new Bounds(0, 0, width, skinHeight), baseline, actionHeight,
                Math.max(0, baseline + actionHeight - skinHeight),
                new Bounds(0, baseline, 154 * canvasScale, actionHeight),
                new Bounds(172 * canvasScale, baseline, 110 * canvasScale, actionHeight),
                new Bounds(298 * canvasScale, baseline, 82 * canvasScale, actionHeight),
                new Bounds(cookieX(width, radius) - radius, cookieY(radius) - radius, radius * 2, radius * 2));
    }
    static Bounds actionImage(Bounds slot, SongSelectSkinAssets.SkinTexture asset) {
        return actionImage(slot, asset, SongSelectBodyBounds.FULL);
    }
    static Bounds actionImage(Bounds slot, SongSelectSkinAssets.SkinTexture asset, SongSelectBodyBounds body) {
        float height = slot.height() / body.height();
        float width = height * asset.logicalWidth() / asset.logicalHeight();
        return new Bounds(slot.x() + (slot.width() - width) / 2, slot.y() - body.bottom() * height, width, height);
    }
}
