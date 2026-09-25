package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import dev.osujava.OsuJavaGame;

public final class MainMenuScreen extends ScreenAdapter {
    private final OsuJavaGame game;

    public MainMenuScreen(OsuJavaGame game) {
        this.game = game;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.08f, 0.07f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch().begin();
        game.font().getData().setScale(2f);
        game.font().draw(game.batch(), "osu!java", 48, Gdx.graphics.getHeight() - 64);
        game.font().getData().setScale(1f);
        game.font().draw(game.batch(), "Local rhythm game", 50, Gdx.graphics.getHeight() - 104);
        game.batch().end();
    }
}

