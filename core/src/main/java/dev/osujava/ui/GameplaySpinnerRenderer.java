package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.osujava.gameplay.SpinnerVisual;
import dev.osujava.skin.LegacyHudLayout;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.OsuSkinAssets.Image;
import dev.osujava.skin.SkinConfiguration.Rgb;

import static dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.*;

/** Skin pieces stay inside the existing Spinner render-plan command. Missing pieces draw nothing. */
final class GameplaySpinnerRenderer {
    private final OsuSkinAssets assets;
    GameplaySpinnerRenderer(OsuSkinAssets assets) { this.assets = assets; }
    boolean available() { return assets != null && assets.spinnerStyle() != Style.FALLBACK; }

    void draw(SpriteBatch batch, SpinnerVisual s, long now, float width, float height) {
        var c = LegacySpinnerCoordinates.fit(width, height);
        float alpha = (float) wholeAlpha(s, now);
        if (alpha <= 0) return;
        if (assets.spinnerStyle() == Style.OLD) {
            image(batch, c, Image.SPINNER_BACKGROUND, 320, Y_CENTRE, SPRITE_SCALE, 0,
                    assets.spinnerConfiguration().background(), alpha, .5f, .5f);
            image(batch, c, Image.SPINNER_CIRCLE, 320, Y_CENTRE, SPRITE_SCALE, s.rotationDegrees(), null, alpha, .5f, .5f);
            metre(batch, c, s, now, alpha);
        } else {
            double scale = progressScale(s.progress());
            // Only glow uses additive blending. Flush before and after changing batch blend state.
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            image(batch, c, Image.SPINNER_GLOW, 320, Y_CENTRE, scale, 0, glowColour(s, now),
                    alpha * (float) glowAlpha(s.progress()), .5f, .5f);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            boolean middle2 = assets.get(Image.SPINNER_MIDDLE2) != null;
            image(batch, c, Image.SPINNER_BOTTOM, 320, Y_CENTRE, scale, bottomRotation(s.rotationDegrees(), middle2), null, alpha, .5f, .5f);
            image(batch, c, Image.SPINNER_TOP, 320, Y_CENTRE, scale, topRotation(s.rotationDegrees(), middle2), null, alpha, .5f, .5f);
            image(batch, c, Image.SPINNER_MIDDLE2, 320, Y_CENTRE, scale, middle2Rotation(s.rotationDegrees()), null, alpha, .5f, .5f);
            image(batch, c, Image.SPINNER_MIDDLE, 320, Y_CENTRE, scale, fixedRotation(), fixedColour(s, now), alpha, .5f, .5f);
        }
        // A single custom skin is the top provider: use its approach asset for either style.
        image(batch, c, Image.SPINNER_APPROACH, 320, Y_CENTRE, approachScale(s, now), 0, null, alpha, .5f, .5f);
        // LegacySpinner common container has Depth=float.MinValue, above the style's body/approach.
        var bonus = lastBonus(s, now, false);
        if (bonus != null) text(batch, c, Long.toString(bonus.legacyBonusScore()), 320, TOP_OFFSET + 299,
                bonusScale(bonus, now), alpha * (float) bonusAlpha(bonus, now), .5f, .5f);
        double offset = spmOffset(s, now);
        image(batch, c, Image.SPINNER_RPM, 320 - 87, 445 + offset, SPRITE_SCALE, 0, null, alpha, 0, 0);
        text(batch, c, spmText(s.spinsPerMinute()), 320 + 80, 448 + offset, SPRITE_SCALE * .9,
                alpha, 1, 0);
        image(batch, c, Image.SPINNER_SPIN, 320, TOP_OFFSET + 335, SPRITE_SCALE, 0, null,
                alpha * (float) spinAlpha(s, now), .5f, .5f);
        image(batch, c, Image.SPINNER_CLEAR, 320, TOP_OFFSET + 115, clearScale(s, now), 0, null,
                alpha * (float) clearAlpha(s, now), .5f, .5f);
        batch.setColor(Color.WHITE);
    }

    private void image(SpriteBatch batch, LegacySpinnerCoordinates c, Image image, double x, double y,
                       double scale, double rotation, Rgb tint, float alpha, float originX, float originY) {
        var asset = assets.get(image);
        if (asset == null || alpha <= 0) return;
        float w = c.length(asset.logicalWidth() * scale), h = c.length(asset.logicalHeight() * scale);
        batch.setColor(tint == null ? 1 : tint.r(), tint == null ? 1 : tint.g(), tint == null ? 1 : tint.b(), alpha);
        batch.draw(new TextureRegion(asset.texture()), c.x(x) - w * originX, c.y(y) - h * (1 - originY),
                w * originX, h * (1 - originY), w, h, 1, 1, LegacySpinnerCoordinates.screenRotation(rotation));
    }
    private void metre(SpriteBatch batch, LegacySpinnerCoordinates c, SpinnerVisual s, long now, float alpha) {
        var asset = assets.get(Image.SPINNER_METRE);
        if (asset == null) return;
        double visibleHeight = metreBars(s.progress(), assets.spinnerConfiguration().noBlink(), now, s.beatmapIndex()) / 10.0 * METRE_HEIGHT;
        // Container Y=final-height, sprite Y=-container.Y; retain the native texture's absolute position.
        double cut = METRE_HEIGHT - visibleHeight;
        double fullHeight = asset.logicalHeight() * SPRITE_SCALE;
        double retained = Math.max(0, fullHeight - cut);
        if (retained <= 0) return;
        TextureRegion region = new TextureRegion(asset.texture());
        region.setV((float) (cut / fullHeight));
        batch.setColor(1, 1, 1, alpha);
        batch.draw(region, c.x(0), c.y(TOP_OFFSET + fullHeight),
                c.length(asset.logicalWidth() * SPRITE_SCALE), c.length(retained));
    }
    private void text(SpriteBatch batch, LegacySpinnerCoordinates c, String value, double x, double y,
                      double scale, float alpha, float originX, float originY) {
        if (alpha <= 0) return;
        var font = OsuSkinAssets.HudFont.SCORE;
        var layout = LegacyHudLayout.create(value, ch -> {
            var glyph = assets.hudGlyph(font, ch);
            return glyph == null ? null : new LegacyHudLayout.Size(glyph.logicalWidth(), glyph.logicalHeight());
        }, assets.hudOverlap(font), false);
        float unit = c.length(scale);
        float left = c.x(x) - layout.width() * unit * originX;
        float top = c.y(y) + layout.height() * unit * originY;
        batch.setColor(1, 1, 1, alpha);
        for (var glyph : layout.glyphs()) {
            var texture = assets.hudGlyph(font, glyph.character());
            batch.draw(texture.texture(), left + glyph.x() * unit, top - (glyph.y() + glyph.height()) * unit,
                    glyph.width() * unit, glyph.height() * unit);
        }
    }
}
