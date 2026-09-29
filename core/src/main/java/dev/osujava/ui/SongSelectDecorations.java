package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiView;

/** Deterministic menu decoration. No audio, gameplay state, or texture loads in drawing. */
final class SongSelectDecorations {
    private static final Color tint = new Color();
    static double beat(double seconds, BeatmapDifficulty difficulty) {
        double period = 1000, offset = 0, latest = Double.NEGATIVE_INFINITY;
        double position = Double.isFinite(seconds) ? seconds * 1000 : 0;
        if (difficulty != null) for (var point : difficulty.timingPoints()) {
            if (point.uninherited() && point.beatLength() > 0 && Double.isFinite(point.beatLength())
                    && Double.isFinite(point.timeMs()) && point.timeMs() <= position && point.timeMs() >= latest) {
                period = point.beatLength(); offset = point.timeMs(); latest = offset;
            }
        }
        return Math.pow(Math.max(0, Math.cos((position - offset) / period * Math.PI * 2)), 4);
    }

    static void draw(UiView view, SpriteBatch batch, SongSelectSkinAssets skin, UiLayout layout,
                     double seconds, double previewSeconds, BeatmapDifficulty difficulty) {
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
        view.beginText();
        try {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA,GL20.GL_ONE);
            float scale = layout.height()/768, size = 256 * scale;
            tint.set(1,1,1,(float)(.045 + .065 * beat(previewSeconds,difficulty)));
            SongSelectSkinDrawing.fit(batch,skin,SongSelectSkinAssets.modeImage(difficulty == null ? 0 : difficulty.mode(),0),
                    (layout.width()-size)/2,(layout.height()-size)/2,size,size,tint);
            for (int i = 0; i < 14; i++) {
                double phase = (seconds * (18+i%4*4) * scale + i * 137.0 * scale) % (layout.width()+40);
                float x = layout.width() + 20 - (float)phase;
                float y = layout.height() * (.19f + (i*37%67)/100f);
                float edge = Math.min(1,Math.max(0,Math.min(x,layout.width()-x)/80));
                tint.set(1,1,1,.13f * edge);
                float diameter = (12 + i%3*4) * scale;
                SongSelectSkinDrawing.fit(batch,skin,Image.PARTICLE,x,y,diameter,diameter,tint);
            }
        } finally {
            view.endText();
            batch.setBlendFunctionSeparate(src,dst,srcAlpha,dstAlpha);
        }
    }
}
