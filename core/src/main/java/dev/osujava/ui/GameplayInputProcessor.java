package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import dev.osujava.gameplay.GameplaySession;

import java.util.HashSet;
import java.util.Set;

public final class GameplayInputProcessor extends InputAdapter {
    private final GameplaySession session;
    private final Runnable onBack;
    private final boolean manualGameplayInputEnabled;
    private PlayfieldViewport viewport;
    private final Set<Integer> heldMouseButtons = new HashSet<>();
    private final Set<Integer> heldHitKeys = new HashSet<>();

    public GameplayInputProcessor(GameplaySession session, Runnable onBack) {
        this(session, onBack, true);
    }

    public GameplayInputProcessor(GameplaySession session, Runnable onBack, boolean manualGameplayInputEnabled) {
        this.session = session;
        this.onBack = onBack;
        this.manualGameplayInputEnabled = manualGameplayInputEnabled;
    }

    public void setViewport(PlayfieldViewport viewport) {
        this.viewport = viewport;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!isHitButton(button)) return false;
        if (!manualGameplayInputEnabled) return true;
        if (heldMouseButtons.add(button)) pressAt(screenX, screenY);
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!isHitButton(button)) return false;
        if (!manualGameplayInputEnabled) return true;
        heldMouseButtons.remove(button);
        releaseIfIdle();
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!manualGameplayInputEnabled) return viewport != null;
        updatePointer(screenX, screenY);
        return viewport != null;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (!manualGameplayInputEnabled) return viewport != null;
        updatePointer(screenX, screenY);
        return viewport != null;
    }

    private void updatePointer(int screenX, int screenY) {
        if (viewport == null) return;
        Vector2 osuPosition = toOsuPosition(screenX, screenY);
        session.pointerMoved(osuPosition.x, osuPosition.y);
    }

    private void pressAt(int screenX, int screenY) {
        if (viewport == null) return;
        float x = screenX;
        float y = Gdx.graphics.getHeight() - screenY;
        Vector2 osuPosition = toOsuPosition(screenX, screenY);
        if (viewport.containsScreenPoint(x, y)) session.click(osuPosition.x, osuPosition.y);
        else session.pointerMoved(osuPosition.x, osuPosition.y);
    }

    private Vector2 toOsuPosition(int screenX, int screenY) {
        float x = screenX;
        float y = Gdx.graphics.getHeight() - screenY;
        return new Vector2((float) viewport.toOsuX(x), (float) viewport.toOsuY(y));
    }

    @Override
    public boolean keyDown(int keycode) {
        if (AppShortcuts.handleQuit(keycode)) return true;
        if (keycode == Input.Keys.ESCAPE) {
            onBack.run();
            return true;
        }
        if (!manualGameplayInputEnabled) return isHitKey(keycode);
        if (isHitKey(keycode)) {
            if (heldHitKeys.add(keycode)) pressAt(Gdx.input.getX(), Gdx.input.getY());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (!isHitKey(keycode)) return false;
        if (!manualGameplayInputEnabled) return true;
        heldHitKeys.remove(keycode);
        releaseIfIdle();
        return true;
    }

    private void releaseIfIdle() {
        if (heldMouseButtons.isEmpty() && heldHitKeys.isEmpty()) session.pointerReleased();
    }

    private boolean isHitButton(int button) {
        return button == Input.Buttons.LEFT || button == Input.Buttons.RIGHT;
    }

    private boolean isHitKey(int keycode) {
        return keycode == Input.Keys.Z || keycode == Input.Keys.X;
    }
}
