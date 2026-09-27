package dev.osujava.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import java.util.function.BooleanSupplier;

/** First in the application's input multiplexer; pointer and hit-key events pass through. */
public final class VolumeHudInput extends InputAdapter {
    private final VolumeHud hud;
    private final BooleanSupplier reserveWheel;
    private final BooleanSupplier altDown;
    public VolumeHudInput(VolumeHud hud, BooleanSupplier reserveWheel, BooleanSupplier altDown) {
        this.hud = hud;
        this.reserveWheel = reserveWheel;
        this.altDown = altDown;
    }
    @Override public boolean keyDown(int key) {
        if (key == Input.Keys.F4) {
            if (hud.active()) hud.close(); else hud.open();
            return true;
        }
        if (!hud.active()) return false;
        switch (key) {
            case Input.Keys.ESCAPE -> hud.close();
            case Input.Keys.TAB, Input.Keys.RIGHT -> hud.select(1);
            case Input.Keys.LEFT -> hud.select(-1);
            case Input.Keys.UP -> hud.adjust(1);
            case Input.Keys.DOWN -> hud.adjust(-1);
            default -> { return false; }
        }
        return true;
    }
    @Override public boolean scrolled(float amountX, float amountY) {
        if (!Float.isFinite(amountY) || amountY == 0) return false;
        if (!hud.active() && reserveWheel.getAsBoolean() && !altDown.getAsBoolean()) return false;
        hud.adjust(-amountY);
        return true;
    }
}
