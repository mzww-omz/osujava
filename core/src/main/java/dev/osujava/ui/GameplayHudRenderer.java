package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import dev.osujava.skin.LegacyHudLayout;
import dev.osujava.skin.OsuSkinAssets.HudFont;
import dev.osujava.OsuJavaGame;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.GameplaySkin;
import dev.osujava.gameplay.GameplaySkinComponent;
import dev.osujava.gameplay.GameplayVisualTiming;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.SpinnerVisual;
import dev.osujava.skin.HitCircleNumberLayout;
import dev.osujava.skin.OsuSkinAssets;

import java.util.Locale;

/** HUD text, separate from object geometry and skinned judgement pieces. */
final class GameplayHudRenderer {
    private final OsuJavaGame game;
    private final GameplaySkin visuals;
    private final OsuSkinAssets skinAssets;
    private final float bitmapCapHeight;
    private final LegacyHudLayout initialScore, initialAccuracy;
    private final float comboOriginHeight;

    GameplayHudRenderer(OsuJavaGame game, GameplaySkin visuals, OsuSkinAssets skinAssets) {
        this.game = game;
        this.visuals = visuals;
        this.skinAssets = skinAssets;
        this.bitmapCapHeight = game.font().getData().capHeight / game.font().getData().scaleY;
        // DefaultSkinComponentsContainer applies relative positions once after LoadComplete.
        this.initialScore = layout(LegacyHudLayout.scoreText(0), HudFont.SCORE, true);
        this.initialAccuracy = layout(LegacyHudLayout.accuracyText(1), HudFont.SCORE, true);
        this.comboOriginHeight = layout("0x", HudFont.COMBO, false).height();
    }

    void draw(SpriteBatch batch, GameplayState state, PlayfieldViewport viewport, String notice) {
        drawHud(batch, state);
        drawNotice(batch, viewport, notice);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
    }

    void drawComboNumber(SpriteBatch batch, int comboNumber, double x, double y,
                                 double logicalRadius, double alpha, PlayfieldViewport viewport) {
        if (alpha <= 0.01) return;
        if (skinAssets != null && skinAssets.hasHitCircleDigits()) {
            drawSkinnedComboNumber(batch, comboNumber, x, y, logicalRadius, alpha, viewport);
            return;
        }
        String text = Integer.toString(comboNumber);
        float radius = viewport.toScreenLength(logicalRadius);
        BitmapFont font = game.font();
        float scale = Math.max(0.5f, Math.min(1.25f, radius * 0.72f / font.getData().capHeight));
        font.getData().setScale(scale);
        float textWidth = measureTextWidth(text, scale);
        float centerX = viewport.toScreenX(x);
        float centerY = viewport.toScreenY(y);
        float baseline = centerY + font.getData().capHeight * scale * 0.34f;
        font.setColor(0.04f, 0.035f, 0.07f, (float) alpha * 0.78f);
        font.draw(batch, text, centerX - textWidth * 0.5f + 1.2f, baseline - 1.2f);
        font.setColor(1, 1, 1, (float) alpha);
        font.draw(batch, text, centerX - textWidth * 0.5f, baseline);
    }

    private void drawSkinnedComboNumber(SpriteBatch batch, int number, double x, double y,
                                        double radius, double alpha, PlayfieldViewport viewport) {
        HitCircleNumberLayout layout = HitCircleNumberLayout.create(number, digit -> {
            var asset = skinAssets.hitCircleDigit(digit);
            return new HitCircleNumberLayout.Size(asset.logicalWidth(), asset.logicalHeight());
        }, skinAssets.hitCircleOverlap());
        float scale = viewport.toScreenLength(HitCircleNumberLayout.scale(radius));
        float centreX = viewport.toScreenX(x);
        float centreY = viewport.toScreenY(y);
        batch.setColor(1, 1, 1, (float) alpha);
        for (var glyph : layout.glyphs()) {
            batch.draw(skinAssets.hitCircleDigit(glyph.digit()).texture(),
                    centreX + glyph.x() * scale, centreY + glyph.y() * scale,
                    glyph.width() * scale, glyph.height() * scale);
        }
        batch.setColor(Color.WHITE);
    }

    void drawSpinnerText(SpriteBatch batch, GameplayState state, PlayfieldViewport viewport) {
        if (skinAssets != null && skinAssets.spinnerStyle() != dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.Style.FALLBACK) return;
        for (SpinnerVisual spinner : state.spinners()) {
            long now = state.currentTimeMs();
            if (now < spinner.startTimeMs() - spinner.preemptMs()
                    || now > spinner.endTimeMs() + 320) continue;
            double fade = GameplayVisualTiming.fadeInProgress(now, spinner.startTimeMs(), spinner.preemptMs(),
                    ApproachTimeCalculator.fadeInMs(spinner.preemptMs()))
                    * GameplayVisualTiming.fadeOutAlpha(now, spinner.endTimeMs(), 320);
            float unit = viewport.toScreenLength(1);
            float centerX = viewport.toScreenX(spinner.centerX());
            float centerY = viewport.toScreenY(spinner.centerY());
            String title = spinner.judgement() == null
                    ? spinner.completionTimeMs() != Long.MIN_VALUE ? "CLEAR!" : "SPIN!"
                    : spinner.judgement() == Judgement.MISS ? "MISSED" : "COMPLETE";
            String spins = spinner.requiredSpins() == 0 ? "CLEAR"
                    : spinner.completedSpins() + " / " + spinner.requiredSpins() + " SPINS";
            String progress = Math.round(GameplayVisualTiming.clamp(spinner.progress()) * 100) + "%";
            drawCentered(batch, title, centerX, centerY + viewport.toScreenLength(30), unit * 1.25f,
                    visuals.component(GameplaySkinComponent.HUD_TEXT), (float) fade);
            drawCentered(batch, spins, centerX, centerY + viewport.toScreenLength(7), unit * 0.86f,
                    visuals.component(GameplaySkinComponent.HUD_SECONDARY), (float) fade);
            drawCentered(batch, progress, centerX, centerY - viewport.toScreenLength(15), unit * 0.74f,
                    visuals.judgementColor(spinner.judgement() == null ? Judgement.HIT300 : spinner.judgement()),
                    (float) fade);
            String spm = String.format(Locale.ROOT, "%.0f SPM", spinner.spinsPerMinute());
            drawCentered(batch, spm, centerX, centerY - viewport.toScreenLength(41), unit * 0.72f,
                    visuals.component(GameplaySkinComponent.HUD_SECONDARY), (float) fade);
            if (spinner.bonusScore() > 0) {
                drawCentered(batch, "+" + spinner.bonusScore(), centerX,
                        centerY + viewport.toScreenLength(58), unit * 0.72f, visuals.component(GameplaySkinComponent.SPINNER_COMPLETE), (float) fade);
            }
        }
    }

    private LegacyHudLayout layout(String text, HudFont font, boolean fixed) {
        boolean skinned = skinAssets != null && skinAssets.hasHudText(font, text);
        return LegacyHudLayout.create(text, character -> {
            if (skinned) {
                var glyph = skinAssets.hudGlyph(font, character);
                return new LegacyHudLayout.Size(glyph.logicalWidth(), glyph.logicalHeight());
            }
            // No built-in legacy image pack: coherent readable bitmap fallback per counter.
            var glyph = game.font().getData().getGlyph(character);
            float scale = 40 / bitmapCapHeight;
            return new LegacyHudLayout.Size(glyph == null ? 0 : glyph.xadvance * scale, 40);
        }, skinned ? skinAssets.hudOverlap(font) : 0, fixed);
    }

    private void drawHud(SpriteBatch batch, GameplayState state) {
        var visual = state.hud();
        LegacyHudLayout score = layout(LegacyHudLayout.scoreText(visual.score()), HudFont.SCORE, true);
        LegacyHudLayout accuracy = layout(LegacyHudLayout.accuracyText(visual.accuracy()), HudFont.SCORE, true);
        LegacyHudPlacement placement = LegacyHudPlacement.fit(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), initialScore, initialAccuracy);
        float unit = placement.unit();
        float scale = unit * LegacyHudPlacement.SCORE_SCALE;
        drawText(batch, score, HudFont.SCORE, placement.scoreRight() - score.width() * scale,
                placement.scoreTop(), scale, 1);
        scale = unit * LegacyHudPlacement.ACCURACY_SCALE;
        drawText(batch, accuracy, HudFont.SCORE, placement.accuracyRight() - accuracy.width() * scale,
                placement.accuracyTop(), scale, 1);
        LegacyHudLayout combo = layout(visual.combo() + "x", HudFont.COMBO, false);
        float base = unit * LegacyHudPlacement.COMBO_SCALE;
        drawText(batch, combo, HudFont.COMBO, placement.comboLeft(),
                LegacyHudPlacement.comboTop(placement.comboBottom(), comboOriginHeight, base, (float) visual.comboScale()),
                base * (float) visual.comboScale(), (float) visual.comboAlpha());
        LegacyHudLayout pop = layout(visual.popCombo() + "x", HudFont.COMBO, false);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        drawText(batch, pop, HudFont.COMBO, placement.comboLeft() - 3 * base * (float) visual.popScale(),
                LegacyHudPlacement.comboTop(placement.comboBottom(), comboOriginHeight, base, (float) visual.popScale()),
                base * (float) visual.popScale(), (float) visual.popAlpha());
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawText(SpriteBatch batch, LegacyHudLayout layout, HudFont font,
                          float left, float top, float scale, float alpha) {
        if (alpha <= 0) return;
        boolean skinned = skinAssets != null && skinAssets.hasHudText(font, layout.text());
        batch.setColor(1, 1, 1, alpha);
        for (var glyph : layout.glyphs()) {
            float x = left + glyph.x() * scale;
            if (skinned) {
                batch.draw(skinAssets.hudGlyph(font, glyph.character()).texture(), x,
                        top - (glyph.y() + glyph.height()) * scale, glyph.width() * scale, glyph.height() * scale);
            } else {
                float bitmapScale = scale * 40 / bitmapCapHeight;
                game.font().getData().setScale(bitmapScale);
                game.font().setColor(1, 1, 1, alpha);
                game.font().draw(batch, String.valueOf(glyph.character()), x, top);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** Shape pass in the existing foremost HUD layer. No fake audio duration is used. */
    void drawSongProgress(ShapeRenderer shapes, GameplayState state) {
        if (state.songProgress() == null) return;
        var placement = LegacyHudPlacement.fit(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), initialScore, initialAccuracy);
        float unit = placement.unit();
        float x = placement.progressRight() - 16.5f * unit, y = placement.progressCentreY();
        var progress = state.songProgress().at(state.currentTimeMs());
        float opacity = (float) state.songProgress().alphaAt(state.currentTimeMs());
        double fraction = progress.intro() ? 1 - progress.progress() : progress.progress();
        if (progress.intro()) shapes.setColor(199 / 255f, 1, 47 / 255f, 153 / 255f * opacity);
        else shapes.setColor(1, 1, 1, 153 / 255f * opacity);
        double direction = progress.intro() ? 1 : -1;
        for (int i = 0; i < Math.ceil(fraction * 96); i++) {
            double a = Math.PI / 2 + direction * i * Math.PI * 2 / 96;
            double b = Math.PI / 2 + direction * Math.min(i + 1, fraction * 96) * Math.PI * 2 / 96;
            float radius = 33 * 0.92f / 2 * unit;
            shapes.triangle(x, y, x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius,
                    x + (float) Math.cos(b) * radius, y + (float) Math.sin(b) * radius);
        }
        shapes.setColor(1, 1, 1, opacity);
        // CircularContainer's 33px outer diameter and 2px border, with a 4px centre dot.
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI * 2 / 96, b = (i + 1) * Math.PI * 2 / 96;
            float outer = 16.5f * unit, inner = 14.5f * unit;
            float ax = x + (float) Math.cos(a) * outer, ay = y + (float) Math.sin(a) * outer;
            float bx = x + (float) Math.cos(b) * outer, by = y + (float) Math.sin(b) * outer;
            float cx = x + (float) Math.cos(a) * inner, cy = y + (float) Math.sin(a) * inner;
            float dx = x + (float) Math.cos(b) * inner, dy = y + (float) Math.sin(b) * inner;
            shapes.triangle(ax, ay, bx, by, cx, cy);
            shapes.triangle(bx, by, dx, dy, cx, cy);
        }
        shapes.circle(x, y, 2 * unit, 24);

    }

    /** Operational notices are separate from the legacy counters. */
    private void drawNotice(SpriteBatch batch, PlayfieldViewport viewport, String notice) {
        if (notice == null || notice.isBlank()) return;
        float unit = viewport.toScreenLength(1);
        game.font().getData().setScale(unit * 0.6f);
        game.font().setColor(1f, 0.82f, 0.62f, 1);
        game.font().draw(batch, notice, viewport.left() + unit * 12, viewport.bottom() + unit * 57);
    }

    private void drawCentered(SpriteBatch batch, String text, float centerX, float baseline,
                              float scale, Color color, float alpha) {
        game.font().getData().setScale(scale);
        float width = measureTextWidth(text, scale);
        game.font().setColor(color.r, color.g, color.b, color.a * alpha);
        game.font().draw(batch, text, centerX - width * 0.5f, baseline);
    }

    private float measureTextWidth(String text, float scale) {
        float width = 0;
        for (int index = 0; index < text.length(); index++) {
            BitmapFont.Glyph glyph = game.font().getData().getGlyph(text.charAt(index));
            if (glyph == null) glyph = game.font().getData().getGlyph('?');
            if (glyph != null) width += glyph.xadvance * scale;
        }
        return width;
    }

}
