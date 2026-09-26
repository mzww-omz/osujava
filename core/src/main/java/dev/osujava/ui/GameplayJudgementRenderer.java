package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.JudgementVisual;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.*;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.OsuSkinAssets.SkinTexture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Textured judgement pieces placed in the existing below/above proxy command slots. */
final class GameplayJudgementRenderer {
    private final OsuSkinAssets assets;
    private final Map<JudgementVisual, List<Particle>> particles = new HashMap<>();
    GameplayJudgementRenderer(OsuSkinAssets assets) { this.assets = assets; }

    void draw(SpriteBatch batch, GameplayState state, PlayfieldViewport viewport, boolean above) {
        if (assets == null) return;
        particles.keySet().removeIf(v -> !state.judgementVisuals().contains(v));
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        Color previous = new Color(batch.getColor());
        try {
            for (var visual : state.judgementVisuals()) {
                double age = state.currentTimeMs() - visual.timeMs();
                if (age < 0 || age >= LegacyJudgementAnimation.MAX_LIFETIME_MS) continue;
                Result result = Result.from(visual);
                var asset = assets.judgement(result);
                if (asset == null) continue;
                int count = asset.frames().size();
                SkinTexture frame = asset.frames().get(LegacyJudgementAnimation.frame(age, count));
                if (asset.style() == Style.NEW) {
                    if (above) {
                        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                        drawImage(batch, frame, visual, viewport, LegacyJudgementAnimation.temporary(age));
                    } else {
                        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                        for (Particle p : particles.computeIfAbsent(visual, LegacyJudgementAnimation::particles)) {
                            drawImage(batch, asset.particle(), visual, viewport,
                                    new Transform(LegacyJudgementAnimation.alpha(age) * p.alpha(age), 1, 0, 0), p.x(age), p.y(age));
                        }
                        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                        drawImage(batch, frame, visual, viewport, LegacyJudgementAnimation.main(age, count));
                    }
                } else if (above) {
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    drawImage(batch, frame, visual, viewport, asset.style() == Style.SLIDER_POINT
                            ? LegacyJudgementAnimation.sliderPoint(age)
                            : LegacyJudgementAnimation.old(result, age, count, assets.legacyVersion(),
                                    LegacyJudgementAnimation.missRotation(visual)));
                }
            }
        } finally {
            batch.setBlendFunction(src, dst);
            batch.setColor(previous);
        }
    }

    private void drawImage(SpriteBatch batch, SkinTexture image, JudgementVisual visual,
                           PlayfieldViewport viewport, Transform transform) {
        drawImage(batch, image, visual, viewport, transform, 0, 0);
    }
    private void drawImage(SpriteBatch batch, SkinTexture image, JudgementVisual visual,
                           PlayfieldViewport viewport, Transform transform, double offsetX, double offsetY) {
        if (transform.alpha() <= 0) return;
        double base = LegacyJudgementAnimation.baseScale(visual.radius());
        float scale = viewport.toScreenLength(base * transform.scale());
        float width = image.logicalWidth() * scale, height = image.logicalHeight() * scale;
        float x = viewport.toScreenX(visual.x() + offsetX * base);
        float y = viewport.toScreenY(visual.y() + (transform.y() + offsetY) * base);
        batch.setColor(1, 1, 1, (float) transform.alpha());
        batch.draw(image.texture(), x - width / 2, y - height / 2, width / 2, height / 2, width, height,
                1, 1, (float) -transform.rotation(), 0, 0, image.texture().getWidth(), image.texture().getHeight(), false, false);
    }
}
