package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public final class AppShortcuts {
    private AppShortcuts() {
    }

    public static boolean handleQuit(int keycode) {
        if (keycode != Input.Keys.Q) return false;
        boolean commandOrSymbol = Gdx.input.isKeyPressed(Input.Keys.SYM);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (!commandOrSymbol && !control) return false;
        Gdx.app.exit();
        return true;
    }
}
