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
        float scale = Math.min(w / asset.logicalWidth(), h / asset.logicalHeight());
        float width = asset.logicalWidth() * scale, height = asset.logicalHeight() * scale;
        draw(batch, skin, image, x + (w - width) / 2, y + (h - height) / 2, width, height, tint);
    }
    static void draw(SpriteBatch batch, SongSelectSkinAssets skin, Image image,
                     float x, float y, float w, float h, Color tint) {
        if (!present(skin, image)) return;
        float previous = batch.getPackedColor();
        batch.setColor(tint);
        batch.draw(skin.get(image).texture(), x, y, w, h);
        batch.setPackedColor(previous);
    }
}
