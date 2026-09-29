package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectKeyboardTest {
    private Input oldInput;
    private final Set<Integer> held = new HashSet<>();
    private final List<String> commands = new ArrayList<>();
    private final SongSelectToolboxState toolbox = new SongSelectToolboxState();
    private SongSelectInput input;
    private String search = "";
    private boolean searching, enabled = true;

    @BeforeEach void setup() {
        oldInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},
                (p, m, a) -> m.getName().equals("isKeyPressed") ? held.contains((int)a[0])
                        : m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null);
        input = new SongSelectInput(toolbox, new SongBrowserControls(), new SongSelectInput.Target() {
            @Override public String search() { return search; }
            @Override public boolean searchActive() { return searching; }
            @Override public void searchActive(boolean active) { searching = active; }
            @Override public void search(String query) { search = query; }
            @Override public void perform(SongSelectAction action) {
                commands.add(action.name());
                if (action == SongSelectAction.MODS) toolbox.open(SongSelectToolboxState.Overlay.MODS);
            }
            @Override public void difficulty(int direction) { commands.add("row:" + direction); }
            @Override public void set(int direction) { commands.add("set:" + direction); }
            @Override public void group(int direction) { commands.add("group:" + direction); }
            @Override public void parentGroup() { commands.add("parent"); }
            @Override public void page(int direction) { commands.add("page:" + direction); }
            @Override public boolean scroll(float amount) { return true; }
            @Override public boolean repeatEnabled() { return enabled; }
        });
    }
    @AfterEach void cleanup() { Gdx.input = oldInput; }
    private boolean press(int key) { held.add(key); return input.keyDown(key); }
    private void release(int key) { held.remove(key); input.keyUp(key); }
    private void pulse() { input.advanceKeys(.25f); input.advanceKeys(0); }

    @Test void heldArrowsRepeatWithoutBackendKeyDownEventsAndStopOnRelease() {
        assertTrue(press(Keys.DOWN)); input.advanceKeys(0);
        assertEquals(List.of("row:1"), commands);
        pulse(); assertEquals(List.of("row:1", "row:1"), commands);
        release(Keys.DOWN); pulse(); assertEquals(2, commands.size());
        press(Keys.UP); input.advanceKeys(0); pulse();
        assertEquals(List.of("row:1", "row:1", "row:-1", "row:-1"), commands);
    }
    @Test void pagesRepeatAndUseNativeKeyOrderWhenHeldTogether() {
        press(Keys.PAGE_DOWN); press(Keys.PAGE_UP); input.advanceKeys(0); pulse();
        assertEquals(List.of("page:1", "page:-1", "page:-1", "page:1"), commands);
    }
    @Test void heldHorizontalAndEnterKeysRequireANewPressForAnotherAction() {
        for (int key : new int[]{Keys.LEFT, Keys.RIGHT, Keys.ENTER}) {
            press(key); input.advanceKeys(0); pulse(); press(key); pulse();
            release(key);
        }
        assertEquals(List.of("set:-1", "set:1", "PLAY"), commands);
        press(Keys.ENTER); assertEquals(4, commands.size());
    }
    @Test void modifiersRouteInitialCommandsAndRestartTheSharedWait() {
        press(Keys.DOWN); input.advanceKeys(0); input.advanceKeys(.25f);
        press(Keys.SHIFT_RIGHT); input.advanceKeys(0);
        assertEquals(List.of("row:1"), commands);
        press(Keys.LEFT); press(Keys.ENTER);
        release(Keys.LEFT); release(Keys.ENTER);
        input.advanceKeys(0); pulse();
        assertEquals(List.of("row:1", "group:-1", "parent", "row:1"), commands);
        press(Keys.CONTROL_RIGHT); press(Keys.ENTER);
        assertEquals("PLAY", commands.getLast());
    }
    @Test void overlayAndSearchConsumeHeldNavigationWithoutRepeatingTheirCloseKey() {
        press(Keys.DOWN); input.advanceKeys(0); press(Keys.F1); input.advanceKeys(0);
        release(Keys.F1); pulse(); pulse();
        assertTrue(toolbox.open()); assertEquals(List.of("row:1", "MODS"), commands);
        press(Keys.ESCAPE); input.advanceKeys(0); pulse();
        assertFalse(toolbox.open()); assertEquals("row:1", commands.getLast());
        assertFalse(commands.contains("BACK")); release(Keys.ESCAPE);
        searching = true; int before = commands.size(); pulse(); pulse();
        assertEquals(before, commands.size());
        press(Keys.ENTER); input.advanceKeys(0); pulse(); pulse();
        assertFalse(searching); assertFalse(commands.contains("PLAY"));
    }
    @Test void consumedShortcutCharactersStaySuppressedUntilTheirKeyIsReleased() {
        for (int i = 0; i < 2; i++) {
            press(Keys.I); input.keyTyped('i'); input.keyTyped('i');
            assertEquals("", search); assertFalse(searching);
            release(Keys.I);
        }
        assertEquals(List.of("IMPORT", "IMPORT"), commands);
        input.keyTyped('B'); assertFalse(press(Keys.I));
        input.keyTyped('i'); input.keyTyped('i');
        assertEquals("Bii", search);
    }
    @Test void repeatedPrintablePlayAndOverlayCloseCannotLeakIntoSearch() {
        press(Keys.SPACE); input.keyTyped(' '); input.keyTyped(' ');
        assertEquals("", search); release(Keys.SPACE);
        toolbox.open(SongSelectToolboxState.Overlay.MODS);
        press(Keys.NUM_2); input.keyTyped('2'); input.keyTyped('2');
        assertFalse(toolbox.open()); assertEquals("", search);
        release(Keys.NUM_2); input.keyTyped('2'); assertEquals("2", search);
    }
    @Test void blockedTargetLostReleaseAndCancellationCannotNavigate() {
        press(Keys.DOWN); input.advanceKeys(0); enabled = false; pulse(); pulse();
        assertEquals(1, commands.size()); enabled = true;
        held.clear(); pulse(); assertEquals(1, commands.size());
        press(Keys.DOWN); input.advanceKeys(0); input.cancelKeys(); pulse();
        assertEquals(2, commands.size());
        assertTrue(press(Keys.DOWN)); assertEquals(3, commands.size());
    }
}
