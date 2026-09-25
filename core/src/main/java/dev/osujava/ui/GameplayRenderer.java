package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.SliderVisual;

import java.util.List;

public final class GameplayRenderer {
    private static final Color BACKGROUND = new Color(0.035f, 0.032f, 0.055f, 1);
    private static final Color CIRCLE = new Color(0.95f, 0.31f, 0.58f, 1);
    private static final Color CIRCLE_INNER = new Color(0.14f, 0.11f, 0.2f, 1);
    private static final Color SLIDER_TRACK = new Color(0.16f, 0.14f, 0.24f, 1);
    private static final Color SLIDER_TRACK_INNER = new Color(0.38f, 0.29f, 0.5f, 1);
    private static final Color SLIDER_REPEAT = new Color(1f, 0.78f, 0.36f, 1);
    private static final Color SLIDER_REPEAT_DONE = new Color(0.55f, 0.5f, 0.6f, 1);
    private static final Color SLIDER_TAIL = new Color(0.76f, 0.68f, 0.88f, 1);
    private static final Color SLIDER_HEAD_HIT = new Color(0.52f, 0.78f, 0.69f, 1);
    private static final Color SLIDER_HEAD_MISS = new Color(0.42f, 0.35f, 0.47f, 1);
    private static final Color SLIDER_BALL_TRACKING = new Color(0.58f, 0.94f, 0.77f, 1);
    private static final Color SLIDER_BALL_INNER = new Color(0.3f, 0.23f, 0.43f, 1);
    private static final Color APPROACH = new Color(0.98f, 0.75f, 0.86f, 0.92f);
    private final OsuJavaGame game;

    public GameplayRenderer(OsuJavaGame game) {
        this.game = game;
    }

    public void render(BeatmapSet set, BeatmapDifficulty difficulty, GameplayState state,
                       PlayfieldViewport viewport, Texture background, String notice) {
        com.badlogic.gdx.Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1);
        com.badlogic.gdx.Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        SpriteBatch batch = game.batch();
        if (background != null) {
            batch.setColor(Color.WHITE);
            batch.begin();
            batch.draw(background, viewport.left(), viewport.bottom(), viewport.width(), viewport.height());
            batch.end();
        }

        ShapeRenderer shapes = game.shapes();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, background == null ? 1 : 0.48f);
        shapes.rect(viewport.left(), viewport.bottom(), viewport.width(), viewport.height());
        for (SliderVisual slider : state.sliders()) {
            drawSliderTrack(shapes, slider, viewport, (float) slider.radius() * viewport.scale());
            float x = viewport.toScreenX(slider.tailPosition().x());
            float y = viewport.toScreenY(slider.tailPosition().y());
            shapes.setColor(SLIDER_TRACK);
            shapes.circle(x, y, (float) slider.radius() * viewport.scale() * 0.53f);
            shapes.setColor(SLIDER_TAIL);
            shapes.circle(x, y, (float) slider.radius() * viewport.scale() * 0.28f);

            for (SliderVisual.RepeatMarker repeat : slider.repeats()) {
                float repeatX = viewport.toScreenX(repeat.position().x());
                float repeatY = viewport.toScreenY(repeat.position().y());
                float repeatRadius = (float) slider.radius() * viewport.scale() * 0.34f;
                shapes.setColor(repeat.judged() ? SLIDER_REPEAT_DONE : SLIDER_REPEAT);
                shapes.circle(repeatX, repeatY, repeatRadius);
                shapes.setColor(CIRCLE_INNER);
                shapes.circle(repeatX, repeatY, repeatRadius * 0.45f);
            }

            float headX = viewport.toScreenX(slider.headPosition().x());
            float headY = viewport.toScreenY(slider.headPosition().y());
            float headRadius = (float) slider.radius() * viewport.scale();
            shapes.setColor(slider.headHit() ? SLIDER_HEAD_HIT
                    : slider.headJudged() ? SLIDER_HEAD_MISS : CIRCLE);
            shapes.circle(headX, headY, headRadius);
            shapes.setColor(CIRCLE_INNER);
            shapes.circle(headX, headY, headRadius * 0.76f);

            if (state.currentTimeMs() >= slider.startTimeMs() && state.currentTimeMs() <= slider.endTimeMs()) {
                float ballX = viewport.toScreenX(slider.ballPosition().x());
                float ballY = viewport.toScreenY(slider.ballPosition().y());
                shapes.setColor(slider.tracking() ? SLIDER_BALL_TRACKING : Color.WHITE);
                shapes.circle(ballX, ballY, headRadius * 0.58f);
                shapes.setColor(SLIDER_BALL_INNER);
                shapes.circle(ballX, ballY, headRadius * 0.33f);
            }
        }
        for (HitCircleVisual circle : state.circles()) {
            float x = viewport.toScreenX(circle.x());
            float y = viewport.toScreenY(circle.y());
            float radius = (float) circle.radius() * viewport.scale();
            shapes.setColor(CIRCLE);
            shapes.circle(x, y, radius);
            shapes.setColor(CIRCLE_INNER);
            shapes.circle(x, y, radius * 0.76f);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (SliderVisual slider : state.sliders()) {
            if (slider.headJudged()) continue;
            float x = viewport.toScreenX(slider.headPosition().x());
            float y = viewport.toScreenY(slider.headPosition().y());
            shapes.setColor(APPROACH);
            shapes.circle(x, y, (float) slider.approachRadius() * viewport.scale());
        }
        for (HitCircleVisual circle : state.circles()) {
            float x = viewport.toScreenX(circle.x());
            float y = viewport.toScreenY(circle.y());
            shapes.setColor(Color.WHITE);
            shapes.circle(x, y, (float) circle.radius() * viewport.scale());
            shapes.setColor(0.98f, 0.75f, 0.86f, 0.92f);
            shapes.circle(x, y, (float) circle.approachRadius() * viewport.scale());
        }
        shapes.end();

        batch.begin();
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1.05f);
        game.font().draw(batch, set.title() + "  ·  " + difficulty.version(), 26, com.badlogic.gdx.Gdx.graphics.getHeight() - 28);
        game.font().getData().setScale(0.82f);
        game.font().setColor(new Color(0.86f, 0.83f, 0.9f, 1));
        game.font().draw(batch, "Score " + state.score().score() + "     Combo " + state.score().combo()
                + "     Accuracy " + String.format(java.util.Locale.ROOT, "%.2f%%", state.score().accuracy() * 100),
                28, com.badlogic.gdx.Gdx.graphics.getHeight() - 54);
        if (notice != null && !notice.isBlank()) {
            game.font().setColor(new Color(1f, 0.83f, 0.66f, 1));
            game.font().draw(batch, notice, 28, 28);
        }
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
        batch.end();
    }

    private void drawSliderTrack(ShapeRenderer shapes, SliderVisual slider, PlayfieldViewport viewport, float radius) {
        List<BeatmapPoint> points = slider.pathPoints();
        if (points.isEmpty()) return;
        for (int i = 0; i < points.size(); i++) {
            if (i == 0) {
                drawTrackDot(shapes, points.get(i), viewport, radius);
                continue;
            }
            BeatmapPoint previous = points.get(i - 1);
            BeatmapPoint current = points.get(i);
            double distance = Math.hypot(current.x() - previous.x(), current.y() - previous.y());
            int steps = Math.max(1, (int) Math.ceil(distance / Math.max(1, slider.radius() * 0.55)));
            for (int step = 1; step <= steps; step++) {
                double progress = (double) step / steps;
                drawTrackDot(shapes, new BeatmapPoint(previous.x() + (current.x() - previous.x()) * progress,
                        previous.y() + (current.y() - previous.y()) * progress), viewport, radius);
            }
        }
    }

    private void drawTrackDot(ShapeRenderer shapes, BeatmapPoint point, PlayfieldViewport viewport, float radius) {
        float x = viewport.toScreenX(point.x());
        float y = viewport.toScreenY(point.y());
        shapes.setColor(SLIDER_TRACK);
        shapes.circle(x, y, radius);
        shapes.setColor(SLIDER_TRACK_INNER);
        shapes.circle(x, y, radius * 0.78f);
    }
}
