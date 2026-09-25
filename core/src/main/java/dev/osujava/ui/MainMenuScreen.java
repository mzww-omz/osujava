package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import dev.osujava.OsuJavaGame;

public final class MainMenuScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.075f, 0.067f, 0.12f, 1f);
    private static final Color PANEL = new Color(0.18f, 0.14f, 0.25f, 1f);
    private static final Color ACCENT = new Color(0.94f, 0.29f, 0.55f, 1f);
    private final OsuJavaGame game;
    private float playX;
    private float playY;
    private float playW;
    private float playH;

    public MainMenuScreen(OsuJavaGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (AppShortcuts.handleQuit(keycode)) return true;
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    game.navigate(new SongSelectScreen(game));
                    return true;
                }
                if (keycode == Input.Keys.I) {
                    SongSelectScreen songSelect = new SongSelectScreen(game);
                    game.navigate(songSelect);
                    songSelect.requestImport();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    Gdx.app.exit();
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
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float panelW = Math.min(520, width * 0.76f);
        float panelH = Math.min(420, height * 0.76f);
        float panelX = (width - panelW) / 2;
        float panelY = (height - panelH) / 2;
        playW = Math.min(310, panelW - 70);
        playH = 72;
        playX = width / 2f - playW / 2;
        playY = panelY + 86;

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                && contains(Gdx.input.getX(), height - Gdx.input.getY(), playX, playY, playW, playH)) {
            game.navigate(new SongSelectScreen(game));
            return;
        }

        game.shapes().begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        game.shapes().setColor(new Color(0.14f, 0.12f, 0.21f, 1));
        game.shapes().rect(0, height * 0.83f, width, height * 0.17f);
        game.shapes().setColor(PANEL);
        game.shapes().rect(panelX, panelY, panelW, panelH);
        game.shapes().setColor(ACCENT);
        game.shapes().rect(panelX, panelY + panelH - 6, panelW, 6);
        game.shapes().rect(playX, playY, playW, playH);
        game.shapes().end();

        game.batch().begin();
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(3.1f);
        game.font().draw(game.batch(), "osu!java", panelX + 42, panelY + panelH - 74);
        game.font().getData().setScale(1.1f);
        game.font().setColor(new Color(0.78f, 0.76f, 0.84f, 1));
        game.font().draw(game.batch(), "LOCAL RHYTHM GAME", panelX + 46, panelY + panelH - 112);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1.8f);
        game.font().draw(game.batch(), "Play", playX + playW / 2 - 31, playY + 47);
        game.font().getData().setScale(0.9f);
        game.font().setColor(new Color(0.72f, 0.69f, 0.79f, 1));
        game.font().draw(game.batch(), "Import beatmaps from your computer", panelX + 45, panelY + 48);
        game.font().draw(game.batch(), "Press I to import directly", panelX + 45, panelY + 28);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
        game.batch().end();
    }

    private boolean contains(float x, float y, float rectX, float rectY, float rectW, float rectH) {
        return x >= rectX && x <= rectX + rectW && y >= rectY && y <= rectY + rectH;
    }
}
