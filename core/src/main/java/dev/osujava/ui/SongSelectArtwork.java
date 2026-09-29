package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets.SkinTexture;

/** Native skin canvases; alpha padding never determines image scale or origin. */
final class SongSelectArtwork {
    static SongSelectChrome.Bounds card(float x, float centreY, float canvasHeight, float multiplier, SkinTexture image) {
        float scale = canvasHeight / SongSelectMetrics.LEGACY_CANVAS_HEIGHT * multiplier;
        float height = image.logicalHeight() * scale;
        return new SongSelectChrome.Bounds(x, centreY - height / 2, image.logicalWidth() * scale, height);
    }
}
