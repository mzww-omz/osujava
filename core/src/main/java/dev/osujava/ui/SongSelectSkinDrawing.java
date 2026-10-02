package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Resident skin images only. Call inside an active sprite batch. */
final class SongSelectSkinDrawing {
    static void additiveFit(SpriteBatch batch, SongSelectSkinAssets skin, Image image,
                            float x, float y, float w, float h, Color tint) {
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
        try {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA,GL20.GL_ONE);
            fit(batch,skin,image,x,y,w,h,tint);
        } finally { batch.setBlendFunctionSeparate(src,dst,srcAlpha,dstAlpha); }
    }
    static boolean present(SongSelectSkinAssets skin, Image image) { return skin != null && skin.get(image) != null; }
    static void fit(SpriteBatch batch, SongSelectSkinAssets skin, Image image,
                    float x, float y, float w, float h, Color tint) {
        if (!present(skin, image)) return;
        var asset = skin.get(image);
        if (asset.logicalWidth() <= 0 || asset.logicalHeight() <= 0) return;
        float scale = Math.min(w / asset.logicalWidth(), h / asset.logicalHeight());
        float width = asset.logicalWidth() * scale, height = asset.logicalHeight() * scale;
        draw(batch, skin, image, x + (w - width) / 2, y + (h - height) / 2, width, height, tint);
    }
    static void draw(SpriteBatch batch, SongSelectSkinAssets skin, Image image,
                     float x, float y, float w, float h, Color tint) {
        if (!present(skin, image)) return;
        float previous = batch.getPackedColor();
        batch.setColor(tint);
        drawTexture(batch,skin.get(image),x,y,w,h);
        batch.setPackedColor(previous);
    }
    static void drawTexture(SpriteBatch batch, SongSelectSkinAssets.SkinTexture asset,
                            float x, float y, float w, float h) {
        if (asset.logicalWidth() <= 0 || asset.logicalHeight() <= 0 || w <= 0 || h <= 0) return;
        batch.draw(asset.texture(),x,y,w,h,0,asset.cropV2(),asset.cropU2(),0);
    }
    /** Animated Back caches its logical crop before changing texture/density (06001b19/40b1). */
    static void drawBackTexture(SpriteBatch batch, SongSelectSkinAssets.SkinTexture asset,
                                SongSelectToolboxLayout.Bounds bounds, float scale) {
        if (bounds.empty()) return;
        float u2 = bounds.width() / scale * asset.density() / asset.texture().getWidth();
        float v2 = bounds.height() / scale * asset.density() / asset.texture().getHeight();
        batch.draw(asset.texture(),bounds.x(),bounds.y(),bounds.width(),bounds.height(),0,v2,u2,0);
    }
}
