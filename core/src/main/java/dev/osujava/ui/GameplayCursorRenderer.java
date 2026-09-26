package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.OsuSkinAssets.Image;
import dev.osujava.skin.OsuSkinAssets.SkinTexture;

/** Foreground cursor layer, independent of the HitObject render plan and HUD. No input/gameplay writes. */
public final class GameplayCursorRenderer {
    private final OsuSkinAssets assets;
    public GameplayCursorRenderer(OsuSkinAssets assets) { this.assets = assets; }
    public LegacyCursorVisual createVisual() {
        var trail = assets.get(Image.CURSOR_TRAIL);
        return new LegacyCursorVisual(assets.cursorConfiguration(), assets.get(Image.CURSOR) != null,
                assets.get(Image.CURSOR_MIDDLE) != null, trail == null ? 0 : trail.logicalWidth());
    }
    public void draw(SpriteBatch batch, ShapeRenderer shapes, LegacyCursorVisual visual,
                     double now, PlayfieldViewport viewport) {
        if (!visual.positioned()) return;
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
        Color previous = new Color(batch.getColor());
        try {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, visual.disjoint() ? GL20.GL_ONE_MINUS_SRC_ALPHA : GL20.GL_ONE);
            batch.begin();
            var trail = assets.get(Image.CURSOR_TRAIL);
            if (trail != null) for (var part : visual.parts()) {
                batch.setColor(1, 1, 1, part.alpha(now, visual.fadeDuration()));
                drawPiece(batch, trail, new LegacyCursorVisual.Piece(part.x(), part.y(), visual.trailCentered(),
                        part.scale(), visual.trailRotation(now)), viewport);
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(Color.WHITE);
            var cursor = assets.get(Image.CURSOR);
            if (cursor != null) {
                drawPiece(batch, cursor, visual.cursor(now), viewport);
                var middle = assets.get(Image.CURSOR_MIDDLE);
                if (middle != null) drawPiece(batch, middle, visual.middle(), viewport);
            }
            batch.end();
            if (cursor == null) drawFallback(shapes, visual.cursor(now), viewport);
        } finally {
            if (batch.isDrawing()) batch.end();
            batch.setColor(previous);
            batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
        }
    }
    private static void drawPiece(SpriteBatch batch, SkinTexture asset, LegacyCursorVisual.Piece piece,
                                  PlayfieldViewport viewport) {
        // NonPlayfieldSprite: native logical dimensions / STABLE_MAGIC_SCALE_FACTOR, never stretch to 50.
        float w = screenSize(asset.logicalWidth(), viewport);
        float h = screenSize(asset.logicalHeight(), viewport);
        float ox = piece.centred() ? w / 2 : 0, oy = piece.centred() ? h / 2 : h;
        batch.draw(asset.texture(), viewport.toScreenX(piece.x()) - ox, viewport.toScreenY(piece.y()) - oy,
                ox, oy, w, h, piece.scale(), piece.scale(), -piece.rotation(),
                0, 0, asset.texture().getWidth(), asset.texture().getHeight(), false, false);
    }
    static float screenSize(float nativeLogicalSize, PlayfieldViewport viewport) {
        return viewport.toScreenLength(nativeLogicalSize / LegacyCursorVisual.MAGIC_SCALE);
    }
    private static void drawFallback(ShapeRenderer shapes, LegacyCursorVisual.Piece piece, PlayfieldViewport viewport) {
        float x = viewport.toScreenX(piece.x()), y = viewport.toScreenY(piece.y());
        float radius = viewport.toScreenLength(14 * piece.scale()); // OsuCursor.SIZE=28.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1, 1, 1, 1); ring(shapes, x, y, radius, radius * 2 / 3);
        shapes.setColor(1, 1, 1, .5f); ring(shapes, x, y, radius * 2 / 3, radius / 3);
        // DefaultCursor's centre dot is a separate child and does not expand.
        shapes.setColor(34 / 255f, 93 / 255f, 204 / 255f, 1);
        shapes.circle(x, y, viewport.toScreenLength(28 * .14 / 2), 32);
        shapes.end(); Gdx.gl.glDisable(GL20.GL_BLEND);
    }
    private static void ring(ShapeRenderer shapes, float x, float y, float outer, float inner) {
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            float ax = (float) Math.cos(a), ay = (float) Math.sin(a), bx = (float) Math.cos(b), by = (float) Math.sin(b);
            shapes.triangle(x + ax * outer, y + ay * outer, x + bx * outer, y + by * outer, x + ax * inner, y + ay * inner);
            shapes.triangle(x + ax * inner, y + ay * inner, x + bx * outer, y + by * outer, x + bx * inner, y + by * inner);
        }
    }
}
