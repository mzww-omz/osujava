package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import dev.osujava.gameplay.GameplaySession;

public final class GameplayInputProcessor extends InputAdapter {
    private final GameplaySession session;
    private final Runnable onBack;
    private PlayfieldViewport viewport;

    public GameplayInputProcessor(GameplaySession session, Runnable onBack) {
        this.session = session;
        this.onBack = onBack;
    }

    public void setViewport(PlayfieldViewport viewport) {
        this.viewport = viewport;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT || viewport == null) return false;
        float x = screenX;
        float y = Gdx.graphics.getHeight() - screenY;
        if (!viewport.containsScreenPoint(x, y)) return false;
        session.click(viewport.toOsuX(x), viewport.toOsuY(y));
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (AppShortcuts.handleQuit(keycode)) return true;
        if (keycode == Input.Keys.ESCAPE) {
            onBack.run();
            return true;
        }
        return false;
    }
}
