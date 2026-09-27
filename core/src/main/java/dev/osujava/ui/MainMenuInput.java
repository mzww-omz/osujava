package dev.osujava.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Keyboard bypasses entrance delay. Pointer actions use the visible logo and wedge geometry. */
final class MainMenuInput extends InputAdapter {
    private final MainMenuModel model;
    private final Runnable play, exit;
    MainMenuInput(MainMenuModel model, Runnable play, Runnable exit) { this.model = model; this.play = play; this.exit = exit; }
    @Override public boolean keyDown(int key) {
        if (model.pending()) return true;
        if (key == Input.Keys.P || key == Input.Keys.ENTER || key == Input.Keys.SPACE) { model.request(0, play); return true; }
        if (key == Input.Keys.ESCAPE) {
            if (model.state() == MainMenuState.CLOSED) model.request(1, exit);
            else if (model.state() != MainMenuState.CLOSING) model.toggle();
            return true;
        }
        return AppShortcuts.handleQuit(key);
    }
    void click(boolean logo, int button) {
        if (model.pending()) return;
        if (logo) model.toggle();
        else if (model.buttonsEnabled() && button >= 0) model.request(button, button == 0 ? play : exit);
    }
}
