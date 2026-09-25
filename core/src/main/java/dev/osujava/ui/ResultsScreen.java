package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.ScoreState;

import java.util.Locale;

public final class ResultsScreen extends ScreenAdapter {
    private static final Color PANEL = new Color(0.16f, 0.13f, 0.23f, 1);
    private static final Color ACCENT = new Color(0.96f, 0.31f, 0.58f, 1);
    private final OsuJavaGame game;
    private final BeatmapSet set;
    private final BeatmapDifficulty difficulty;
    private final ScoreState score;
    private float replayX, replayY, replayW, replayH, songsX, songsY, songsW, songsH;

    public ResultsScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty, ScoreState score) {
        this.game = game;
        this.set = set;
        this.difficulty = difficulty;
        this.score = score;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                float x = screenX;
                float y = Gdx.graphics.getHeight() - screenY;
                if (contains(x, y, replayX, replayY, replayW, replayH)) {
                    game.navigate(new GameplayScreen(game, set, difficulty));
                    return true;
                }
                if (contains(x, y, songsX, songsY, songsW, songsH)) {
                    game.navigate(new SongSelectScreen(game));
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (AppShortcuts.handleQuit(keycode)) return true;
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    game.navigate(new SongSelectScreen(game));
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        float panelW = Math.min(600, width - 48);
        float panelH = Math.min(560, height - 48);
        float x = (width - panelW) / 2;
        float y = (height - panelH) / 2;
        replayW = 180;
        replayH = 54;
        songsW = 220;
        songsH = 54;
        float gap = 18;
        replayX = width / 2f - (replayW + gap + songsW) / 2;
        replayY = y + 24;
        songsX = replayX + replayW + gap;
        songsY = replayY;

        Gdx.gl.glClearColor(0.065f, 0.055f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        ShapeRenderer shapes = game.shapes();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(x, y, panelW, panelH);
        shapes.setColor(ACCENT);
        shapes.rect(x, y + panelH - 6, panelW, 6);
        shapes.rect(replayX, replayY, replayW, replayH);
        shapes.setColor(0.3f, 0.26f, 0.37f, 1);
        shapes.rect(songsX, songsY, songsW, songsH);
        shapes.end();

        game.batch().begin();
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1.5f);
        game.font().draw(game.batch(), "RESULTS", x + 32, y + panelH - 44);
        game.font().getData().setScale(1.05f);
        game.font().draw(game.batch(), set.title() + "  ·  " + difficulty.version(), x + 34, y + panelH - 92);
        game.font().getData().setScale(4.5f);
        game.font().setColor(ACCENT);
        game.font().draw(game.batch(), grade(score.accuracy()), x + panelW - 140, y + panelH - 180);
        game.font().getData().setScale(1.2f);
        game.font().setColor(Color.WHITE);
        game.font().draw(game.batch(), "Score   " + score.score(), x + 40, y + panelH - 170);
        game.font().draw(game.batch(), "Accuracy   " + String.format(Locale.ROOT, "%.2f%%", score.accuracy() * 100),
                x + 40, y + panelH - 215);
        game.font().getData().setScale(0.95f);
        game.font().setColor(new Color(0.82f, 0.79f, 0.88f, 1));
        game.font().draw(game.batch(), "300   " + score.count300(), x + 42, y + panelH - 285);
        game.font().draw(game.batch(), "100   " + score.count100(), x + 42, y + panelH - 320);
        game.font().draw(game.batch(), "50     " + score.count50(), x + 42, y + panelH - 355);
        game.font().draw(game.batch(), "Miss   " + score.misses(), x + 42, y + panelH - 390);
        game.font().draw(game.batch(), "Max combo   " + score.maxCombo(), x + 42, y + panelH - 435);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
        game.font().draw(game.batch(), "Retry", replayX + 56, replayY + 35);
        game.font().draw(game.batch(), "Song Select", songsX + 45, songsY + 35);
        game.batch().end();
    }

    private String grade(double accuracy) {
        if (accuracy >= 0.99) return "S";
        if (accuracy >= 0.90) return "A";
        if (accuracy >= 0.75) return "B";
        if (accuracy >= 0.60) return "C";
        return "D";
    }

    private boolean contains(float x, float y, float rectX, float rectY, float rectW, float rectH) {
        return x >= rectX && x <= rectX + rectW && y >= rectY && y <= rectY + rectH;
    }
}
