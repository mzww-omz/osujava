package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.GameplaySkin;
import dev.osujava.gameplay.GameplaySkinComponent;
import dev.osujava.gameplay.GameplayVisualTiming;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.SliderVisual;
import dev.osujava.gameplay.SpinnerVisual;
import dev.osujava.skin.HitCircleNumberLayout;
import dev.osujava.skin.OsuSkinAssets;

import java.util.Locale;

/** HUD text and transient judgement labels, separate from object geometry and scoring. */
final class GameplayHudRenderer {
    private static final double JUDGEMENT_LIFETIME_MS = 680;
    private static final double JUDGEMENT_FADE_START_MS = 320;

    private final OsuJavaGame game;
    private final GameplaySkin visuals;
    private final OsuSkinAssets skinAssets;

    GameplayHudRenderer(OsuJavaGame game, GameplaySkin visuals, OsuSkinAssets skinAssets) {
        this.game = game;
        this.visuals = visuals;
        this.skinAssets = skinAssets;
    }

    void drawPanels(ShapeRenderer shapes, PlayfieldViewport viewport) {
        float unit = viewport.toScreenLength(1);
        float panelHeight = unit * 50;
        float panelWidth = Math.min(unit * 310, viewport.width() * 0.58f);
        setColor(shapes, visuals.component(GameplaySkinComponent.HUD_PANEL), 1);
        shapes.rect(viewport.left(), viewport.bottom() + viewport.height() - panelHeight,
                panelWidth, panelHeight);
        float rightPanelWidth = Math.min(unit * 205, viewport.width() * 0.4f);
        shapes.rect(viewport.left() + viewport.width() - rightPanelWidth,
                viewport.bottom() + viewport.height() - panelHeight, rightPanelWidth, panelHeight);
        shapes.rect(viewport.left(), viewport.bottom(), Math.min(unit * 205, viewport.width() * 0.4f), unit * 37);
    }

    void draw(SpriteBatch batch, BeatmapSet set, BeatmapDifficulty difficulty,
              GameplayState state, PlayfieldViewport viewport, String notice) {
        drawComboNumbers(batch, state, viewport);
        drawJudgementText(batch, state, viewport);
        drawSpinnerText(batch, state, viewport);
        drawHud(batch, set, difficulty, state, viewport, notice);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
    }

    private void drawComboNumbers(SpriteBatch batch, GameplayState state, PlayfieldViewport viewport) {
        for (HitCircleVisual circle : state.circles()) {
            drawComboNumber(batch, circle.comboNumber(), circle.x(), circle.y(), circle.radius(),
                    GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), circle.timeMs(), circle.preemptMs(),
                            ApproachTimeCalculator.fadeInMs(circle.preemptMs())),
                    viewport);
        }
        for (SliderVisual slider : state.sliders()) {
            if (slider.headJudged()) continue;
            double alpha = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                    slider.preemptMs(), ApproachTimeCalculator.fadeInMs(slider.preemptMs()));
            drawComboNumber(batch, slider.comboNumber(), slider.headPosition().x(), slider.headPosition().y(),
                    slider.radius(), alpha, viewport);
        }
    }

    private void drawComboNumber(SpriteBatch batch, int comboNumber, double x, double y,
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

    private void drawJudgementText(SpriteBatch batch, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (JudgementVisual judgement : state.judgementVisuals()) {
            double age = now - judgement.timeMs();
            if (age < 0 || age > JUDGEMENT_LIFETIME_MS) continue;
            double fade = GameplayVisualTiming.fadeOutAlpha(now, judgement.timeMs() + JUDGEMENT_FADE_START_MS,
                    JUDGEMENT_LIFETIME_MS - JUDGEMENT_FADE_START_MS);
            double pop = 0.72 + 0.28 * GameplayVisualTiming.easeOutQuint(
                    GameplayVisualTiming.progress(now, judgement.timeMs(), 110));
            String label = judgementLabel(judgement.judgement());
            float unit = viewport.toScreenLength(1);
            float scale = unit * (float) pop * 0.9f;
            float centerX = viewport.toScreenX(judgement.x());
            float centerY = viewport.toScreenY(judgement.y()) + viewport.toScreenLength(judgement.radius() * 0.58)
                    + viewport.toScreenLength(Math.min(14, age * 0.025));
            float textWidth = measureTextWidth(label, scale);
            game.font().getData().setScale(scale);
            game.font().setColor(0.035f, 0.03f, 0.065f, (float) fade * 0.86f);
            game.font().draw(batch, label, centerX - textWidth * 0.5f + 1.3f, centerY - 1.3f);
            Color color = visuals.judgementColor(judgement.judgement());
            game.font().setColor(color.r, color.g, color.b, color.a * (float) fade);
            game.font().draw(batch, label, centerX - textWidth * 0.5f, centerY);
        }
    }

    private String judgementLabel(Judgement judgement) {
        return switch (judgement) {
            case HIT300 -> "300";
            case HIT100 -> "100";
            case HIT50 -> "50";
            case MISS -> "MISS";
        };
    }

    private void drawSpinnerText(SpriteBatch batch, GameplayState state, PlayfieldViewport viewport) {
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

    private void drawHud(SpriteBatch batch, BeatmapSet set, BeatmapDifficulty difficulty,
                         GameplayState state, PlayfieldViewport viewport, String notice) {
        float unit = viewport.toScreenLength(1);
        float uiScale = Math.max(0.72f, Math.min(1.45f, unit));
        float left = viewport.left() + unit * 12;
        float top = viewport.bottom() + viewport.height();
        game.font().getData().setScale(uiScale * 0.8f);
        game.font().setColor(visuals.component(GameplaySkinComponent.HUD_TEXT));
        game.font().draw(batch, set.title() + "  ·  " + difficulty.version(), left, top - unit * 16);

        String score = String.format(Locale.ROOT, "%08d", state.score().score());
        String accuracy = String.format(Locale.ROOT, "%.2f%% ACCURACY", state.score().accuracy() * 100);
        float right = viewport.left() + viewport.width() - unit * 12;
        game.font().getData().setScale(uiScale * 0.92f);
        game.font().setColor(visuals.component(GameplaySkinComponent.HUD_TEXT));
        game.font().draw(batch, score, right - measureTextWidth(score, uiScale * 0.92f), top - unit * 16);
        game.font().getData().setScale(uiScale * 0.62f);
        game.font().setColor(visuals.component(GameplaySkinComponent.HUD_SECONDARY));
        game.font().draw(batch, accuracy, right - measureTextWidth(accuracy, uiScale * 0.62f), top - unit * 36);

        String combo = state.score().combo() + "x";
        game.font().getData().setScale(uiScale * 1.15f);
        game.font().setColor(visuals.component(GameplaySkinComponent.HUD_TEXT));
        game.font().draw(batch, combo, left, viewport.bottom() + unit * 12);
        game.font().getData().setScale(uiScale * 0.56f);
        game.font().setColor(visuals.component(GameplaySkinComponent.HUD_SECONDARY));
        game.font().draw(batch, "COMBO", left + measureTextWidth(combo, uiScale * 1.15f) + unit * 6,
                viewport.bottom() + unit * 16);

        if (notice != null && !notice.isBlank()) {
            game.font().getData().setScale(uiScale * 0.72f);
            game.font().setColor(1f, 0.82f, 0.62f, 1);
            game.font().draw(batch, notice, viewport.left() + unit * 12, viewport.bottom() + unit * 57);
        }
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

    private void setColor(ShapeRenderer shapes, Color color, float alphaMultiplier) {
        shapes.setColor(color.r, color.g, color.b, color.a * alphaMultiplier);
    }
}
