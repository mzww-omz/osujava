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
import dev.osujava.gameplay.ApproachTimeCalculator;
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
    private static final double CIRCLE_HIT_FADE_MS = 100;
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
        render(set, difficulty, state, viewport, background, notice, null);
    }

    public void render(BeatmapSet set, BeatmapDifficulty difficulty, GameplayState state,
                       PlayfieldViewport viewport, Texture background, String notice, BeatmapPoint debugCursor) {
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
        drawJudgementBodies(shapes, state, viewport);
        hud.drawPanels(shapes, viewport);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawApproachCircles(shapes, state, viewport);
        drawCircleOverlays(shapes, state, viewport);
        drawSliderOverlays(shapes, state, viewport);
        drawSpinnerOverlays(shapes, state, viewport);
        drawJudgementRings(shapes, state, viewport);
        if (debugCursor != null) drawDebugCursor(shapes, debugCursor, viewport);
        shapes.end();

        batch.begin();
        hud.draw(batch, set, difficulty, state, viewport, notice);
        batch.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        pruneSliderRenderData(state.currentTimeMs());
    }

    private void drawDebugCursor(ShapeRenderer shapes, BeatmapPoint cursor, PlayfieldViewport viewport) {
        float x = viewport.toScreenX(cursor.x());
        float y = viewport.toScreenY(cursor.y());
        float radius = viewport.toScreenLength(8);
        shapes.setColor(.42f, 1f, .79f, .96f);
        shapes.circle(x, y, radius, 24);
        shapes.line(x - radius * 1.6f, y, x + radius * 1.6f, y);
        shapes.line(x, y - radius * 1.6f, x, y + radius * 1.6f);
    }

    private void drawHitCircle(ShapeRenderer shapes, HitCircleVisual circle,
                               GameplayState state, PlayfieldViewport viewport) {
        float alpha = (float) GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), circle.timeMs(),
                circle.preemptMs(), ApproachTimeCalculator.fadeInMs(circle.preemptMs()));
        drawCircleBody(shapes, viewport.toScreenX(circle.x()), viewport.toScreenY(circle.y()),
                viewport.toScreenLength(circle.radius()), visuals.comboColor(circle.comboColorIndex()), alpha);
    }

    private void drawSliderBody(ShapeRenderer shapes, SliderVisual slider,
                                GameplayState state, PlayfieldViewport viewport) {
        SliderRenderData renderData = renderData(slider, state.currentTimeMs());
        double fadeIn = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                slider.preemptMs(), ApproachTimeCalculator.fadeInMs(slider.preemptMs()));
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.endTimeMs(), SLIDER_POST_FADE_MS);
        float alpha = (float) (fadeIn * fadeOut);
        float radius = viewport.toScreenLength(slider.radius());
        Color comboColor = visuals.comboColor(slider.comboColorIndex());
        double snake = GameplayVisualTiming.sliderSnakeProgress(state.currentTimeMs(),
                slider.startTimeMs(), slider.preemptMs());
        drawThickPath(shapes, renderData, viewport, radius * 2.02f, visuals.sliderBorder, alpha, snake);
        drawThickPath(shapes, renderData, viewport, radius * 1.94f, comboColor, alpha, snake);
        drawThickPath(shapes, renderData, viewport, radius * 1.70f, visuals.sliderInner, alpha, snake);
    }

    private void drawThickPath(ShapeRenderer shapes, SliderRenderData geometry, PlayfieldViewport viewport,
                               float width, Color color, float alpha, double progress) {
        double[] points = geometry.points;
        if (points.length < 2 || progress <= 0) return;
        setColor(shapes, color, alpha);
        float firstX = viewport.toScreenX(points[0]);
        float firstY = viewport.toScreenY(points[1]);
        float capRadius = width * 0.5f;
        double limit = geometry.totalDistance * GameplayVisualTiming.clamp(progress);
        shapes.circle(firstX, firstY, capRadius, BODY_CAP_SEGMENTS);
        for (int index = 2; index < points.length; index += 2) {
            int pointIndex = index / 2;
            double before = geometry.cumulativeDistance[pointIndex - 1];
            if (before >= limit) break;
            double segment = geometry.cumulativeDistance[pointIndex] - before;
            double fraction = segment <= 0 ? 1 : Math.min(1, (limit - before) / segment);
            float x = viewport.toScreenX(points[index - 2] + (points[index] - points[index - 2]) * fraction);
            float y = viewport.toScreenY(points[index - 1] + (points[index + 1] - points[index - 1]) * fraction);
            shapes.rectLine(firstX, firstY, x, y, width);
            shapes.circle(x, y, capRadius, BODY_CAP_SEGMENTS);
            firstX = x;
            firstY = y;
            if (fraction < 1) break;
        }
    }

    private void drawSliderObjects(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        double fadeIn = GameplayVisualTiming.fadeInProgress(now, slider.startTimeMs(), slider.preemptMs(),
                ApproachTimeCalculator.fadeInMs(slider.preemptMs()));
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), SLIDER_POST_FADE_MS);
        float bodyAlpha = (float) (fadeIn * fadeOut);
        float radius = viewport.toScreenLength(slider.radius());
        Color comboColor = visuals.comboColor(slider.comboColorIndex());

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
        for (SliderVisual.TickMarker tick : slider.ticks()) {
            double appearAt = Math.min(slider.startTimeMs(), tick.timeMs() - slider.preemptMs() * 0.66);
            double tickFade = GameplayVisualTiming.progress(now, appearAt, 150);
            if (tick.judged()) tickFade *= GameplayVisualTiming.fadeOutAlpha(now, tick.timeMs(), 120);
            float tickRadius = radius * (tick.judged() && tick.hit() ? 0.22f : 0.17f);
            setColor(shapes, tick.judged() && !tick.hit() ? visuals.spinnerMiss : visuals.circleBorder,
                    (float) (bodyAlpha * tickFade));
            shapes.circle(viewport.toScreenX(tick.position().x()), viewport.toScreenY(tick.position().y()),
                    tickRadius, 16);
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
        drawCircleBody(shapes, x, y, ballRadius, visuals.comboColor(slider.comboColorIndex()), endFade);
        setColor(shapes, visuals.sliderBall, endFade * (slider.tracking() ? 0.7f : 0.38f));
        shapes.circle(x, y, ballRadius * 0.48f, CIRCLE_SEGMENTS);
    }

    private void drawSpinnerField(ShapeRenderer shapes, SpinnerVisual spinner,
                                  GameplayState state, PlayfieldViewport viewport) {
        double fadeIn = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), spinner.startTimeMs(),
                spinner.preemptMs(), ApproachTimeCalculator.fadeInMs(spinner.preemptMs()));
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
        float alpha = (float) GameplayVisualTiming.approachAlpha(now, objectTimeMs, preemptMs);
        float approachRadius = viewport.toScreenLength(GameplayVisualTiming.approachRadius(radius, progress));
        setColor(shapes, visuals.approachCircle, alpha);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y), approachRadius, CIRCLE_SEGMENTS + 4);
    }

    private void drawCircleOverlays(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        for (HitCircleVisual circle : state.circles()) {
            float alpha = (float) GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), circle.timeMs(),
                    circle.preemptMs(), ApproachTimeCalculator.fadeInMs(circle.preemptMs()));
            drawCircleOverlay(shapes, circle.x(), circle.y(), circle.radius(), alpha, viewport);
        }
        for (SliderVisual slider : state.sliders()) {
            double headFade = slider.headJudged() && slider.headJudgementTimeMs() != Long.MIN_VALUE
                    ? GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.headJudgementTimeMs(), CIRCLE_HIT_FADE_MS) : 1;
            double alpha = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                    slider.preemptMs(), ApproachTimeCalculator.fadeInMs(slider.preemptMs())) * headFade;
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
            double bodyFade = GameplayVisualTiming.fadeInProgress(now, slider.startTimeMs(), slider.preemptMs(),
                    ApproachTimeCalculator.fadeInMs(slider.preemptMs()))
                    * GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), SLIDER_POST_FADE_MS);
            Color comboColor = visuals.comboColor(slider.comboColorIndex());
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
            double fadeIn = GameplayVisualTiming.fadeInProgress(now, spinner.startTimeMs(), spinner.preemptMs(),
                    ApproachTimeCalculator.fadeInMs(spinner.preemptMs()));
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

    private void drawJudgementBodies(ShapeRenderer shapes, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (JudgementVisual judgement : state.judgementVisuals()) {
            boolean miss = judgement.judgement() == Judgement.MISS;
            double alpha = miss ? GameplayVisualTiming.fadeOutAlpha(now, judgement.timeMs(), 100)
                    : GameplayVisualTiming.hitCircleAlpha(now, judgement.timeMs());
            if (alpha <= 0) continue;
            double scale = miss ? 1 : GameplayVisualTiming.hitCircleScale(now, judgement.timeMs());
            float radius = viewport.toScreenLength(judgement.radius() * scale);
            setColor(shapes, miss ? visuals.circleMiss : visuals.circleBorder, (float) alpha * (miss ? 0.4f : 0.2f));
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
        private final double[] cumulativeDistance;
        private final double totalDistance;
        private final FollowCircleAnimation follow = new FollowCircleAnimation();
        private long lastUsedTimeMs;

        private SliderRenderData(double[] points) {
            this.points = points;
            cumulativeDistance = new double[points.length / 2];
            for (int i = 1; i < cumulativeDistance.length; i++) {
                cumulativeDistance[i] = cumulativeDistance[i - 1]
                        + Math.hypot(points[2 * i] - points[2 * i - 2], points[2 * i + 1] - points[2 * i - 1]);
            }
            totalDistance = cumulativeDistance.length == 0 ? 0 : cumulativeDistance[cumulativeDistance.length - 1];
        }
    }

}
