package dev.osujava.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import dev.osujava.ui.theme.UiNavigation;

/** One navigation gate for keyboard and pointer actions, including Escape. */
final class MainMenuInput extends InputAdapter {
    private final UiNavigation navigation;
    private final Runnable play, exit;
    MainMenuInput(UiNavigation navigation, Runnable play, Runnable exit) {
        this.navigation = navigation; this.play = play; this.exit = exit;
    }
    @Override public boolean keyDown(int key) {
        if (key == Input.Keys.P || key == Input.Keys.ENTER || key == Input.Keys.SPACE) {
            navigation.request(play); return true;
        }
        if (key == Input.Keys.ESCAPE) { navigation.request(exit); return true; }
        return AppShortcuts.handleQuit(key);
    }
    void click(boolean cookie, int row) {
        if (cookie || row == 0) navigation.request(play);
        else if (row == 1) navigation.request(exit);
    }
}
