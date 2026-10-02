package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets.SkinTexture;

/** Native skin canvases; alpha padding never determines image scale or origin. */
final class SongSelectArtwork {
    /** Same centre-left canvas for drawing and input; logical body remains the navigation pitch. */
    static SongSelectChrome.Bounds row(float x, float y, float width, float height, SkinTexture image) {
        return image == null ? new SongSelectChrome.Bounds(x, y, width, height)
                : card(x, y + height / 2,
                        height * SongSelectMetrics.CAROUSEL_HEIGHT / SongSelectMetrics.ROW_PITCH, 1, image);
    }
    static SongSelectChrome.Bounds card(float x, float centreY, float canvasHeight, float multiplier, SkinTexture image) {
        float scale = canvasHeight / SongSelectMetrics.LEGACY_CANVAS_HEIGHT * multiplier;
        float height = image.logicalHeight() * scale;
        return new SongSelectChrome.Bounds(x, centreY - height / 2, image.logicalWidth() * scale, height);
    }
}
