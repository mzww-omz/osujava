package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import java.util.HashSet;
import java.util.Set;

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
        default void group(int direction) { }
        default void parentGroup() { }
        default void confirm() { perform(SongSelectAction.PLAY); }
        boolean scroll(float amount);
        default boolean repeatEnabled() { return true; }
        default void cursor(int x, int y, int button, boolean down) { }
        default boolean modalOpen() { return false; }
        default void modalKey(int key) { }
        default void modalTyped(char character) { }
        default void modalScroll(float amount) { }
    }
    private final SongSelectToolboxState toolbox;
    private final SongBrowserControls controls;
    private final Target target;
    private final SongSelectKeyRepeat repeat = new SongSelectKeyRepeat();
    private final Set<Integer> suppressedTextKeys = new HashSet<>();
    private char suppressedTyped;
    private char pendingHighSurrogate;

    SongSelectInput(SongSelectToolboxState toolbox, SongBrowserControls controls, Target target) {
        this.toolbox = toolbox; this.controls = controls; this.target = target;
    }
    static boolean shift() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
    @Override public boolean keyDown(int key) {
        // GLFW repeats keyTyped, not keyDown; duplicate downs must not repeat one-shot actions.
        if (!repeat.press(key)) return true;
        return key(key, true);
    }
    private boolean key(int key, boolean initial) {
        pendingHighSurrogate = 0;
        if (initial && AppShortcuts.handleQuit(key)) return true;
        if(target.modalOpen()) {
            if(initial || key==Input.Keys.BACKSPACE || key==Input.Keys.UP || key==Input.Keys.DOWN) target.modalKey(key);
            if(!target.modalOpen() && key==Input.Keys.NUM_6) suppressedTyped='6';
            return true;
        }
        if (toolbox.open()) {
            if (!initial) return true;
            if (key == Input.Keys.ESCAPE || key == Input.Keys.NUM_2 && toolbox.overlay() == SongSelectToolboxState.Overlay.MODS
                    || key == Input.Keys.F1 && toolbox.overlay() == SongSelectToolboxState.Overlay.MODS) {
                suppressedTyped = key == Input.Keys.NUM_2 ? '2' : 0;
                if (key == Input.Keys.NUM_2) suppressedTextKeys.add(key);
                toolbox.close();
            } else if (key == Input.Keys.NUM_1 && toolbox.overlay() == SongSelectToolboxState.Overlay.MODS) toolbox.reset();
            else if(key==Input.Keys.NUM_1 && toolbox.overlay()==SongSelectToolboxState.Overlay.MODE
                    && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) { suppressedTyped='1';toolbox.close(); }
            return true;
        }
        if (key == Input.Keys.F1 || key == Input.Keys.F3 || key == Input.Keys.F2) {
            if (!initial) return true;
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
            if (initial && (key == Input.Keys.ESCAPE || key == Input.Keys.ENTER)) { target.searchActive(false); return true; }
            return false;
        }
        if (key == Input.Keys.ENTER) {
            if (!initial) return true;
            if (shift() && !Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    && !Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) target.parentGroup();
            else target.confirm();
            return true;
        }
        var action = SongSelectAction.shortcut(key, false);
        if (action != null) {
            if (!initial) return true;
            // GLFW sends keyTyped after keyDown even when the shortcut was consumed.
            // Do not let Import or Space-to-play also filter the Library.
            suppressedTyped = key == Input.Keys.I ? 'i' : key == Input.Keys.SPACE ? ' ' : 0;
            if (suppressedTyped != 0) suppressedTextKeys.add(key);
            target.perform(action);
            return true;
        }
        // Stable 06003247: arrows/page traverse rows; Shift+Left/Right traverses Groups.
        switch (key) {
            case Input.Keys.UP -> target.difficulty(-1);
            case Input.Keys.DOWN -> target.difficulty(1);
            case Input.Keys.LEFT -> { if (initial) { if (shift()) target.group(-1); else target.set(-1); } }
            case Input.Keys.RIGHT -> { if (initial) { if (shift()) target.group(1); else target.set(1); } }
            case Input.Keys.PAGE_UP -> target.page(-1);
            case Input.Keys.PAGE_DOWN -> target.page(1);
            default -> { return false; }
        }
        return true;
    }
    void advanceKeys(float seconds) {
        repeat.reconcile(key -> Gdx.input.isKeyPressed(key));
        suppressedTextKeys.removeIf(key -> !Gdx.input.isKeyPressed(key));
        repeat.advance(seconds * 1000.0, key -> { if (target.repeatEnabled()) key(key, false); });
    }
    void cancelKeys() {
        repeat.clear(); suppressedTextKeys.clear(); suppressedTyped = pendingHighSurrogate = 0;
    }
    @Override public boolean keyTyped(char character) {
        char suppressed = suppressedTyped;
        suppressedTyped = 0;
        if (suppressed != 0 && Character.toLowerCase(character) == suppressed) return true;
        if (Character.toLowerCase(character) == 'i' && suppressedTextKeys.contains(Input.Keys.I)
                || character == ' ' && suppressedTextKeys.contains(Input.Keys.SPACE)
                || character == '2' && suppressedTextKeys.contains(Input.Keys.NUM_2)) return true;
        if (toolbox.open()) { pendingHighSurrogate = 0; return true; }
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.SYM)) { pendingHighSurrogate = 0; return true; }
        if (Character.isISOControl(character)) { pendingHighSurrogate = 0; return false; }
        if(target.modalOpen()) { target.modalTyped(character); pendingHighSurrogate=0; return true; }
        if (Character.isHighSurrogate(character)) { pendingHighSurrogate = character; return true; }
        String appended;
        if (Character.isLowSurrogate(character)) {
            if (pendingHighSurrogate == 0) return true;
            appended = new String(new char[]{pendingHighSurrogate, character});
        } else appended = String.valueOf(character);
        pendingHighSurrogate = 0;
        controls.close();
        target.searchActive(true);
        String search = target.search();
        if (search.codePointCount(0, search.length()) < 80) target.search(search + appended);
        return true;
    }
    @Override public boolean keyUp(int key) {
        repeat.release(key); suppressedTextKeys.remove(key);
        if (key == Input.Keys.I || key == Input.Keys.SPACE || key == Input.Keys.NUM_1 || key == Input.Keys.NUM_2 || key == Input.Keys.NUM_6) suppressedTyped = 0;
        return false;
    }
    @Override public boolean touchDown(int x, int y, int pointer, int button) {
        if (pointer == 0) target.cursor(x, y, button, true);
        return false;
    }
    @Override public boolean touchUp(int x, int y, int pointer, int button) {
        if (pointer == 0) target.cursor(x, y, button, false);
        return false;
    }
    @Override public boolean mouseMoved(int x, int y) { target.cursor(x, y, -1, false); return false; }
    @Override public boolean touchDragged(int x, int y, int pointer) {
        if (pointer == 0) target.cursor(x, y, -1, false);
        return false;
    }
    @Override public boolean scrolled(float amountX, float amountY) {
        if(target.modalOpen()) { if(Float.isFinite(amountY) && amountY!=0) target.modalScroll(amountY); return true; }
        if (toolbox.open()) return true;
        return Float.isFinite(amountY) && amountY != 0 && target.scroll(amountY);
    }
}
