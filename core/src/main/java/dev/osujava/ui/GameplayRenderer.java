package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.HitCircleVisual;

public final class GameplayRenderer {
    private static final Color BACKGROUND = new Color(0.035f, 0.032f, 0.055f, 1);
    private static final Color CIRCLE = new Color(0.95f, 0.31f, 0.58f, 1);
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
        for (HitCircleVisual circle : state.circles()) {
            float x = viewport.toScreenX(circle.x());
            float y = viewport.toScreenY(circle.y());
            float radius = (float) circle.radius() * viewport.scale();
            shapes.setColor(CIRCLE);
            shapes.circle(x, y, radius);
            shapes.setColor(0.14f, 0.11f, 0.2f, 1);
            shapes.circle(x, y, radius * 0.76f);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
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
}
