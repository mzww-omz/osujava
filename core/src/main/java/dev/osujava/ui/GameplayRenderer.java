package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.FollowCircleAnimation;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.GameplayVisualConfig;
import dev.osujava.gameplay.GameplayVisualTiming;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import dev.osujava.gameplay.SliderVisual;
import dev.osujava.gameplay.SpinnerVisual;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Draws immutable gameplay snapshots. All object geometry is transformed from osu! playfield space. */
public final class GameplayRenderer {
    private static final int CIRCLE_SEGMENTS = 36;
    private static final int BODY_CAP_SEGMENTS = 14;
    private static final double JUDGEMENT_LIFETIME_MS = 680;
    private static final double JUDGEMENT_FADE_START_MS = 320;
    private static final double CIRCLE_HIT_FADE_MS = 180;
    private static final double SLIDER_POST_FADE_MS = 150;
    private static final double SPINNER_POST_FADE_MS = 150;

    private final OsuJavaGame game;
    private final GameplayVisualConfig visuals = GameplayVisualConfig.defaults();
    private final Map<List<BeatmapPoint>, SliderRenderData> sliderRenderData = new IdentityHashMap<>();
    private final GameplayHudRenderer hud;

    public GameplayRenderer(OsuJavaGame game) {
        this.game = game;
        this.hud = new GameplayHudRenderer(game, visuals);
    }

    public void render(BeatmapSet set, BeatmapDifficulty difficulty, GameplayState state,
                       PlayfieldViewport viewport, Texture background, String notice) {
        Gdx.gl.glClearColor(visuals.background.r, visuals.background.g, visuals.background.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        SpriteBatch batch = game.batch();
        if (background != null) {
            batch.setColor(Color.WHITE);
            batch.begin();
            batch.draw(background, viewport.left(), viewport.bottom(), viewport.width(), viewport.height());
            batch.end();
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ShapeRenderer shapes = game.shapes();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        setColor(shapes, visuals.playfieldTint, 1);
        shapes.rect(viewport.left(), viewport.bottom(), viewport.width(), viewport.height());

        for (SpinnerVisual spinner : state.spinners()) drawSpinnerField(shapes, spinner, state, viewport);
        for (SliderVisual slider : state.sliders()) drawSliderBody(shapes, slider, state, viewport);
        for (SliderVisual slider : state.sliders()) drawSliderObjects(shapes, slider, state, viewport);
        for (HitCircleVisual circle : state.circles()) drawHitCircle(shapes, circle, state, viewport);
        for (SliderVisual slider : state.sliders()) drawSliderBallAndFollow(shapes, slider, state, viewport);
        drawJudgementMissBodies(shapes, state, viewport);
        hud.drawPanels(shapes, viewport);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawApproachCircles(shapes, state, viewport);
        drawCircleOverlays(shapes, state, viewport);
        drawSliderOverlays(shapes, state, viewport);
        drawSpinnerOverlays(shapes, state, viewport);
        drawJudgementRings(shapes, state, viewport);
        shapes.end();

        batch.begin();
        hud.draw(batch, set, difficulty, state, viewport, notice);
        batch.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        pruneSliderRenderData(state.currentTimeMs());
    }

    private void drawHitCircle(ShapeRenderer shapes, HitCircleVisual circle,
                               GameplayState state, PlayfieldViewport viewport) {
        float alpha = (float) GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), circle.timeMs(),
                circle.preemptMs(), 180);
        drawCircleBody(shapes, viewport.toScreenX(circle.x()), viewport.toScreenY(circle.y()),
                viewport.toScreenLength(circle.radius()), visuals.comboColor(circle.comboNumber()), alpha);
    }

    private void drawSliderBody(ShapeRenderer shapes, SliderVisual slider,
                                GameplayState state, PlayfieldViewport viewport) {
        SliderRenderData renderData = renderData(slider, state.currentTimeMs());
        double fadeIn = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                slider.preemptMs(), 180);
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.endTimeMs(), SLIDER_POST_FADE_MS);
        float alpha = (float) (fadeIn * fadeOut);
        float radius = viewport.toScreenLength(slider.radius());
        Color comboColor = visuals.comboColor(slider.comboNumber());
        drawThickPath(shapes, renderData, viewport, radius * 2.18f, visuals.sliderBorder, alpha);
        drawThickPath(shapes, renderData, viewport, radius * 1.97f, visuals.sliderRim, alpha);
        drawThickPath(shapes, renderData, viewport, radius * 1.76f, comboColor, alpha);
    }

    private void drawThickPath(ShapeRenderer shapes, SliderRenderData geometry, PlayfieldViewport viewport,
                               float width, Color color, float alpha) {
        double[] points = geometry.points;
        if (points.length < 2) return;
        setColor(shapes, color, alpha);
        float firstX = viewport.toScreenX(points[0]);
        float firstY = viewport.toScreenY(points[1]);
        float capRadius = width * 0.5f;
        for (int index = 2; index < points.length; index += 2) {
            float x = viewport.toScreenX(points[index]);
            float y = viewport.toScreenY(points[index + 1]);
            shapes.rectLine(firstX, firstY, x, y, width);
            firstX = x;
            firstY = y;
        }
        for (int index = 0; index < points.length; index += 2) {
            shapes.circle(viewport.toScreenX(points[index]), viewport.toScreenY(points[index + 1]),
                    capRadius, BODY_CAP_SEGMENTS);
        }
    }

    private void drawSliderObjects(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        double fadeIn = GameplayVisualTiming.fadeInProgress(now, slider.startTimeMs(), slider.preemptMs(), 180);
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), SLIDER_POST_FADE_MS);
        float bodyAlpha = (float) (fadeIn * fadeOut);
        float radius = viewport.toScreenLength(slider.radius());
        Color comboColor = visuals.comboColor(slider.comboNumber());

        float tailAlpha = bodyAlpha;
        drawCircleBody(shapes, viewport.toScreenX(slider.tailPosition().x()),
                viewport.toScreenY(slider.tailPosition().y()), radius * 0.72f, comboColor, tailAlpha);
        for (SliderVisual.RepeatMarker repeat : slider.repeats()) {
            float alpha = (float) repeatAlpha(slider, repeat, now, bodyAlpha);
            float x = viewport.toScreenX(repeat.position().x());
            float y = viewport.toScreenY(repeat.position().y());
            drawCircleBody(shapes, x, y, radius * 0.37f,
                    repeat.judged() ? visuals.sliderInner : comboColor, alpha);
        }

        double headFade = slider.headJudged() && slider.headJudgementTimeMs() != Long.MIN_VALUE
                ? GameplayVisualTiming.fadeOutAlpha(now, slider.headJudgementTimeMs(), CIRCLE_HIT_FADE_MS) : 1;
        float headAlpha = (float) (bodyAlpha * headFade);
        Color headColor = slider.headJudged() && !slider.headHit() ? visuals.circleMiss : comboColor;
        drawCircleBody(shapes, viewport.toScreenX(slider.headPosition().x()),
                viewport.toScreenY(slider.headPosition().y()), radius, headColor, headAlpha);
        for (SliderVisual.RepeatMarker repeat : slider.repeats()) {
            float alpha = (float) repeatAlpha(slider, repeat, now, bodyAlpha);
            drawReverseArrow(shapes, slider, repeat, viewport.toScreenX(repeat.position().x()),
                    viewport.toScreenY(repeat.position().y()), radius, alpha, now, viewport);
        }
    }

    private double repeatAlpha(SliderVisual slider, SliderVisual.RepeatMarker repeat, long now, float bodyAlpha) {
        double spanDuration = Math.max(1, (slider.endTimeMs() - slider.startTimeMs()) / (slider.repeats().size() + 1));
        double fadeIn = GameplayVisualTiming.progress(now, slider.startTimeMs() - slider.preemptMs(), 150);
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(now, repeat.timeMs(), Math.min(300, spanDuration));
        return bodyAlpha * fadeIn * fadeOut;
    }

    private void drawReverseArrow(ShapeRenderer shapes, SliderVisual slider, SliderVisual.RepeatMarker repeat,
                                  float centerX, float centerY, float circleRadius, float alpha,
                                  long now, PlayfieldViewport viewport) {
        if (alpha <= 0.01f || slider.pathPoints().size() < 2) return;
        SliderRenderData geometry = renderData(slider, now);
        double[] points = geometry.points;
        boolean atEnd = repeat.spanIndex() % 2 == 0;
        int start = atEnd ? points.length - 4 : 0;
        int end = atEnd ? points.length - 2 : 2;
        double dx = points[end] - points[start];
        double dy = points[end + 1] - points[start + 1];
        if (atEnd) {
            dx = -dx;
            dy = -dy;
        }
        double angle = Math.atan2(-dy, dx);
        float size = circleRadius * 0.24f;
        float offset = circleRadius * 0.23f;
        float x = centerX - (float) Math.cos(angle) * offset;
        float y = centerY - (float) Math.sin(angle) * offset;
        setColor(shapes, visuals.circleBorder, alpha);
        drawArrowTriangle(shapes, x, y, size * 1.18f, angle);
        setColor(shapes, visuals.sliderInner, alpha);
        drawArrowTriangle(shapes, x, y, size * 0.72f, angle);
    }

    private void drawArrowTriangle(ShapeRenderer shapes, float centerX, float centerY, float size, double angle) {
        float tipX = centerX + (float) Math.cos(angle) * size;
        float tipY = centerY + (float) Math.sin(angle) * size;
        float baseX = centerX - (float) Math.cos(angle) * size * 0.68f;
        float baseY = centerY - (float) Math.sin(angle) * size * 0.68f;
        float sideX = (float) -Math.sin(angle) * size * 0.58f;
        float sideY = (float) Math.cos(angle) * size * 0.58f;
        shapes.triangle(tipX, tipY, baseX + sideX, baseY + sideY, baseX - sideX, baseY - sideY);
    }

    private void drawSliderBallAndFollow(ShapeRenderer shapes, SliderVisual slider,
                                         GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        SliderRenderData data = renderData(slider, now);
        data.follow.update(slider.tracking(), now, slider.endTimeMs());
        if (now < slider.startTimeMs() || now > slider.endTimeMs() + SLIDER_POST_FADE_MS) return;
        float endFade = (float) GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), SLIDER_POST_FADE_MS);
        float x = viewport.toScreenX(slider.ballPosition().x());
        float y = viewport.toScreenY(slider.ballPosition().y());
        float radius = viewport.toScreenLength(slider.radius());

        float followAlpha = (float) data.follow.alphaAt(now) * endFade;
        float followRadius = radius * (float) data.follow.scaleAt(now);
        if (followAlpha > 0.01f) {
            setColor(shapes, visuals.followFill, followAlpha);
            shapes.circle(x, y, followRadius, CIRCLE_SEGMENTS);
            setColor(shapes, visuals.followBorder, followAlpha);
            shapes.circle(x, y, followRadius * 0.98f, CIRCLE_SEGMENTS);
        }

        float ballRadius = radius * 0.54f;
        drawCircleBody(shapes, x, y, ballRadius, visuals.comboColor(slider.comboNumber()), endFade);
        setColor(shapes, visuals.sliderBall, endFade * (slider.tracking() ? 0.7f : 0.38f));
        shapes.circle(x, y, ballRadius * 0.48f, CIRCLE_SEGMENTS);
    }

    private void drawSpinnerField(ShapeRenderer shapes, SpinnerVisual spinner,
                                  GameplayState state, PlayfieldViewport viewport) {
        double fadeIn = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), spinner.startTimeMs(),
                spinner.preemptMs(), 180);
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), spinner.endTimeMs(), SPINNER_POST_FADE_MS);
        float alpha = (float) (fadeIn * fadeOut);
        float x = viewport.toScreenX(spinner.centerX());
        float y = viewport.toScreenY(spinner.centerY());
        float radius = viewport.toScreenLength(spinner.radius());
        setColor(shapes, visuals.spinnerField, alpha);
        shapes.circle(x, y, radius * 1.1f, CIRCLE_SEGMENTS + 12);
        setColor(shapes, visuals.spinnerRing, alpha * 0.25f);
        shapes.circle(x, y, radius * 0.92f, CIRCLE_SEGMENTS + 12);
        setColor(shapes, visuals.spinnerField, alpha * 0.88f);
        shapes.circle(x, y, radius * 0.84f, CIRCLE_SEGMENTS + 12);
        setColor(shapes, spinner.tracking() ? visuals.followFill : visuals.sliderInner, alpha * 0.9f);
        shapes.circle(x, y, radius * 0.19f, CIRCLE_SEGMENTS);
    }

    private void drawApproachCircles(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (HitCircleVisual circle : state.circles()) {
            drawApproach(shapes, circle.x(), circle.y(), circle.radius(), circle.timeMs(), circle.preemptMs(), now, viewport);
        }
        for (SliderVisual slider : state.sliders()) {
            if (slider.headJudged() || now >= slider.startTimeMs()) continue;
            drawApproach(shapes, slider.headPosition().x(), slider.headPosition().y(), slider.radius(),
                    slider.startTimeMs(), slider.preemptMs(), now, viewport);
        }
    }

    private void drawApproach(ShapeRenderer shapes, double x, double y, double radius,
                              long objectTimeMs, long preemptMs, long now, PlayfieldViewport viewport) {
        double progress = GameplayVisualTiming.approachProgress(now, objectTimeMs, preemptMs);
        double fadeIn = GameplayVisualTiming.fadeInProgress(now, objectTimeMs, preemptMs, 180);
        float alpha = (float) (0.84 * fadeIn);
        float approachRadius = viewport.toScreenLength(GameplayVisualTiming.approachRadius(radius, progress));
        setColor(shapes, visuals.approachCircle, alpha);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y), approachRadius, CIRCLE_SEGMENTS + 4);
    }

    private void drawCircleOverlays(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        for (HitCircleVisual circle : state.circles()) {
            float alpha = (float) GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), circle.timeMs(),
                    circle.preemptMs(), 180);
            drawCircleOverlay(shapes, circle.x(), circle.y(), circle.radius(), alpha, viewport);
        }
        for (SliderVisual slider : state.sliders()) {
            double headFade = slider.headJudged() && slider.headJudgementTimeMs() != Long.MIN_VALUE
                    ? GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.headJudgementTimeMs(), CIRCLE_HIT_FADE_MS) : 1;
            double alpha = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                    slider.preemptMs(), 180) * headFade;
            drawCircleOverlay(shapes, slider.headPosition().x(), slider.headPosition().y(), slider.radius(),
                    (float) alpha, viewport);
        }
    }

    private void drawCircleOverlay(ShapeRenderer shapes, double x, double y, double radius,
                                   float alpha, PlayfieldViewport viewport) {
        setColor(shapes, visuals.circleBorder, alpha * 0.65f);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y),
                viewport.toScreenLength(radius * 0.98), CIRCLE_SEGMENTS);
        setColor(shapes, visuals.circleOverlay, alpha);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y),
                viewport.toScreenLength(radius * 0.73), CIRCLE_SEGMENTS - 4);
    }

    private void drawSliderOverlays(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (SliderVisual slider : state.sliders()) {
            float radius = viewport.toScreenLength(slider.radius());
            double bodyFade = GameplayVisualTiming.fadeInProgress(now, slider.startTimeMs(), slider.preemptMs(), 180)
                    * GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), SLIDER_POST_FADE_MS);
            Color comboColor = visuals.comboColor(slider.comboNumber());
            setColor(shapes, comboColor, (float) bodyFade * 0.72f);
            shapes.circle(viewport.toScreenX(slider.tailPosition().x()), viewport.toScreenY(slider.tailPosition().y()),
                    radius * 0.72f, CIRCLE_SEGMENTS);
            for (SliderVisual.RepeatMarker repeat : slider.repeats()) {
                float alpha = (float) repeatAlpha(slider, repeat, now, (float) bodyFade);
                setColor(shapes, visuals.circleBorder, alpha);
                shapes.circle(viewport.toScreenX(repeat.position().x()), viewport.toScreenY(repeat.position().y()),
                        radius * 0.37f, CIRCLE_SEGMENTS - 8);
            }
        }
    }

    private void drawSpinnerOverlays(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (SpinnerVisual spinner : state.spinners()) {
            double fadeIn = GameplayVisualTiming.fadeInProgress(now, spinner.startTimeMs(), spinner.preemptMs(), 180);
            double fadeOut = GameplayVisualTiming.fadeOutAlpha(now, spinner.endTimeMs(), SPINNER_POST_FADE_MS);
            float alpha = (float) (fadeIn * fadeOut);
            float centerX = viewport.toScreenX(spinner.centerX());
            float centerY = viewport.toScreenY(spinner.centerY());
            float radius = viewport.toScreenLength(spinner.radius());
            Color progressColor = spinner.judgement() == Judgement.MISS ? visuals.spinnerMiss
                    : spinner.judgement() != null ? visuals.spinnerComplete : visuals.spinnerProgress;
            setColor(shapes, visuals.spinnerRing, alpha);
            shapes.circle(centerX, centerY, radius, CIRCLE_SEGMENTS + 12);
            double progress = GameplayVisualTiming.clamp(spinner.progress());
            if (progress > 0) {
                setColor(shapes, progressColor, alpha);
                int segments = Math.max(1, (int) Math.ceil(72 * progress));
                for (int stroke = 0; stroke < 5; stroke++) {
                    shapes.arc(centerX, centerY, radius * 1.045f - stroke * 1.7f, 90,
                            (float) (360 * progress), segments);
                }
            }

            // Gameplay angles use osu!'s downward-positive Y axis; the viewport is upward-positive.
            double angle = Math.toRadians(-spinner.rotationDegrees());
            float innerX = centerX + (float) Math.cos(angle) * radius * 0.25f;
            float innerY = centerY + (float) Math.sin(angle) * radius * 0.25f;
            float indicatorX = centerX + (float) Math.cos(angle) * radius * 0.86f;
            float indicatorY = centerY + (float) Math.sin(angle) * radius * 0.86f;
            setColor(shapes, spinner.tracking() ? visuals.spinnerComplete : visuals.spinnerRing, alpha);
            shapes.line(innerX, innerY, indicatorX, indicatorY);
        }
    }

    private void drawJudgementMissBodies(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (JudgementVisual judgement : state.judgementVisuals()) {
            if (judgement.judgement() != Judgement.MISS) continue;
            double alpha = GameplayVisualTiming.fadeOutAlpha(now, judgement.timeMs(), 220);
            if (alpha <= 0) continue;
            double age = GameplayVisualTiming.progress(now, judgement.timeMs(), 220);
            float radius = viewport.toScreenLength(judgement.radius() * (1 + 0.08 * age));
            setColor(shapes, visuals.circleMiss, (float) alpha * 0.4f);
            shapes.circle(viewport.toScreenX(judgement.x()), viewport.toScreenY(judgement.y()), radius, CIRCLE_SEGMENTS);
        }
    }

    private void drawJudgementRings(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (JudgementVisual judgement : state.judgementVisuals()) {
            double ageMs = now - judgement.timeMs();
            if (ageMs < 0 || ageMs > JUDGEMENT_LIFETIME_MS) continue;
            double progress = GameplayVisualTiming.progress(now, judgement.timeMs(), JUDGEMENT_LIFETIME_MS);
            double eased = GameplayVisualTiming.easeOutQuint(progress);
            double alpha = GameplayVisualTiming.fadeOutAlpha(now, judgement.timeMs(), JUDGEMENT_LIFETIME_MS);
            float ringRadius = viewport.toScreenLength(judgement.radius() * (0.72 + 0.72 * eased));
            setColor(shapes, visuals.judgementColor(judgement.judgement()), (float) alpha);
            shapes.circle(viewport.toScreenX(judgement.x()), viewport.toScreenY(judgement.y()), ringRadius,
                    CIRCLE_SEGMENTS);
        }
    }

    private void drawCircleBody(ShapeRenderer shapes, float x, float y, float radius,
                                Color fill, float alpha) {
        if (alpha <= 0.01f) return;
        setColor(shapes, visuals.sliderBorder, alpha);
        shapes.circle(x, y, radius * 1.08f, CIRCLE_SEGMENTS);
        setColor(shapes, visuals.circleBorder, alpha);
        shapes.circle(x, y, radius * 0.99f, CIRCLE_SEGMENTS);
        setColor(shapes, fill, alpha);
        shapes.circle(x, y, radius * 0.91f, CIRCLE_SEGMENTS);
    }

    private void setColor(ShapeRenderer shapes, Color color, float alphaMultiplier) {
        shapes.setColor(color.r, color.g, color.b, color.a * alphaMultiplier);
    }

    private SliderRenderData renderData(SliderVisual slider, long now) {
        List<BeatmapPoint> path = slider.pathPoints();
        SliderRenderData data = sliderRenderData.get(path);
        if (data == null) {
            double[] points = new double[path.size() * 2];
            int index = 0;
            for (BeatmapPoint point : path) {
                points[index++] = point.x();
                points[index++] = point.y();
            }
            data = new SliderRenderData(points);
            sliderRenderData.put(path, data);
        }
        data.lastUsedTimeMs = now;
        return data;
    }

    private void pruneSliderRenderData(long now) {
        Iterator<SliderRenderData> entries = sliderRenderData.values().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().lastUsedTimeMs > 5000) entries.remove();
        }
    }

    private static final class SliderRenderData {
        private final double[] points;
        private final FollowCircleAnimation follow = new FollowCircleAnimation();
        private long lastUsedTimeMs;

        private SliderRenderData(double[] points) {
            this.points = points;
        }
    }

}
