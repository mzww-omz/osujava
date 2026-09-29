package dev.osujava.ui;

import com.badlogic.gdx.Input;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/** Song Select's supported repeating keys, driven by the shared 06002afb frame counter. */
final class SongSelectKeyRepeat {
    // Native row-navigation key order: PageUp=33, PageDown=34, Up=38, Down=40.
    private static final int[] REPEATING = {Input.Keys.PAGE_UP,
            Input.Keys.PAGE_DOWN, Input.Keys.UP, Input.Keys.DOWN};
    private final Set<Integer> held = new HashSet<>(), previous = new HashSet<>();
    private double elapsedMs = -120;

    boolean press(int key) {
        if (!held.add(key)) return false;
        elapsedMs = -120;
        return true;
    }
    void release(int key) { held.remove(key); }
    void reconcile(IntPredicate physicallyHeld) { held.removeIf(key -> !physicallyHeld.test(key)); }
    void clear() { held.clear(); previous.clear(); elapsedMs = -120; }

    void advance(double milliseconds, IntConsumer repeat) {
        previous.retainAll(held);
        if (previous.stream().anyMatch(key -> !modifier(key))) {
            // Test before adding this frame, and dispatch at most one pulse per update.
            if (elapsedMs >= 100) {
                elapsedMs -= 100;
                for (int key : REPEATING) if (held.contains(key)) repeat.accept(key);
            }
            if (Double.isFinite(milliseconds) && milliseconds > 0) elapsedMs += milliseconds;
        } else elapsedMs = -120;
        previous.clear(); previous.addAll(held);
    }
    private static boolean modifier(int key) {
        return key == Input.Keys.SHIFT_LEFT || key == Input.Keys.SHIFT_RIGHT
                || key == Input.Keys.CONTROL_LEFT || key == Input.Keys.CONTROL_RIGHT
                || key == Input.Keys.ALT_LEFT || key == Input.Keys.ALT_RIGHT;
    }
}
