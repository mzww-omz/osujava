package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Keyboard/search priority and wheel routing, independent of drawing and row geometry. */
class SongSelectInput extends InputAdapter {
    interface Target {
        String search();
        boolean searchActive();
        void searchActive(boolean active);
        void search(String query);
        void perform(SongSelectAction action);
        void difficulty(int direction);
        void set(int direction);
        void page(int direction);
        boolean scroll(float amount);
    }
    private final SongSelectToolboxState toolbox;
    private final SongBrowserControls controls;
    private final Target target;
    private char suppressedTyped;

    SongSelectInput(SongSelectToolboxState toolbox, SongBrowserControls controls, Target target) {
        this.toolbox = toolbox; this.controls = controls; this.target = target;
    }
    static boolean shift() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
    @Override public boolean keyDown(int key) {
        if (AppShortcuts.handleQuit(key)) return true;
        if (toolbox.open()) {
            if (key == Input.Keys.ESCAPE || key == Input.Keys.NUM_2
                    || key == Input.Keys.F1 && toolbox.overlay() == SongSelectToolboxState.Overlay.MODS) {
                suppressedTyped = key == Input.Keys.NUM_2 ? '2' : 0;
                toolbox.close();
            } else if (key == Input.Keys.NUM_1 && toolbox.overlay() == SongSelectToolboxState.Overlay.MODS) toolbox.reset();
            return true;
        }
        if (key == Input.Keys.F1 || key == Input.Keys.F3 || key == Input.Keys.F2) {
            target.perform(SongSelectAction.shortcut(key, key == Input.Keys.F2 && shift()));
            return true;
        }
        if (key == Input.Keys.ESCAPE && controls.open()) { controls.close(); return true; }
        String search = target.search();
        if (key == Input.Keys.BACKSPACE && !search.isEmpty()) {
            target.search(search.substring(0, search.offsetByCodePoints(search.length(), -1)));
            return true;
        }
        if (target.searchActive()) {
            if (key == Input.Keys.ESCAPE || key == Input.Keys.ENTER) { target.searchActive(false); return true; }
            return false;
        }
        var action = SongSelectAction.shortcut(key, false);
        if (action != null) {
            // GLFW sends keyTyped after keyDown even when the shortcut was consumed.
            // Do not let Import or Space-to-play also filter the Library.
            suppressedTyped = key == Input.Keys.I ? 'i' : key == Input.Keys.SPACE ? ' ' : 0;
            target.perform(action);
            return true;
        }
        // Arrow roles follow the public shortcuts; page distance remains a provisional local policy.
        switch (key) {
            case Input.Keys.UP -> target.difficulty(-1);
            case Input.Keys.DOWN -> target.difficulty(1);
            case Input.Keys.LEFT -> target.set(-1);
            case Input.Keys.RIGHT -> target.set(1);
            case Input.Keys.PAGE_UP -> target.page(-1);
            case Input.Keys.PAGE_DOWN -> target.page(1);
            default -> { return false; }
        }
        return true;
    }
    @Override public boolean keyTyped(char character) {
        char suppressed = suppressedTyped;
        suppressedTyped = 0;
        if (suppressed != 0 && Character.toLowerCase(character) == suppressed) return true;
        if (toolbox.open()) return true;
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.SYM)) return true;
        if (Character.isISOControl(character)) return false;
        controls.close();
        target.searchActive(true);
        String search = target.search();
        if (search.codePointCount(0, search.length()) < 80
                || Character.isLowSurrogate(character) && !search.isEmpty() && Character.isHighSurrogate(search.charAt(search.length() - 1)))
            target.search(search + character);
        return true;
    }
    @Override public boolean keyUp(int key) {
        if (key == Input.Keys.I || key == Input.Keys.SPACE || key == Input.Keys.NUM_2) suppressedTyped = 0;
        return false;
    }
    @Override public boolean scrolled(float amountX, float amountY) {
        if (toolbox.open()) return true;
        return Float.isFinite(amountY) && amountY != 0 && target.scroll(amountY);
    }
}
