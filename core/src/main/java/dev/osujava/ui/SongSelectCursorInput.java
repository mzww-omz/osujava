package dev.osujava.ui;

import com.badlogic.gdx.Input;
import java.util.ArrayDeque;

/** Bounded presentation-only event queue; button edges survive between rendered frames. */
final class SongSelectCursorInput {
    record Event(long nanos, int x, int y, boolean held, boolean press) { }
    interface Sink { void input(double timeMs, int x, int y, boolean held, boolean press); }
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private int buttons;
    private boolean held;
    private double previousMs;

    void event(long nanos, int x, int y, int button, boolean down) {
        boolean press = false;
        if (button == Input.Buttons.LEFT || button == Input.Buttons.RIGHT) {
            int mask = 1 << button;
            press = down && (buttons & mask) == 0;
            buttons = down ? buttons | mask : buttons & ~mask;
        }
        if (events.size() == 2048) events.removeFirst();
        events.addLast(new Event(nanos, x, y, buttons != 0, press));
    }

    void advance(double nowMs, long frameNanos, int x, int y, boolean left, boolean right, Sink sink) {
        nowMs = Math.max(previousMs, nowMs);
        while (!events.isEmpty()) {
            var event = events.removeFirst();
            double time = Math.max(previousMs, Math.min(nowMs, nowMs - (frameNanos - event.nanos()) / 1_000_000.0));
            sink.input(time, event.x(), event.y(), event.held(), event.press());
            previousMs = time; held = event.held();
        }
        boolean current = left || right;
        sink.input(nowMs, x, y, current, current && !held);
        held = current;
        buttons = (left ? 1 << Input.Buttons.LEFT : 0) | (right ? 1 << Input.Buttons.RIGHT : 0);
        previousMs = nowMs;
    }

    void clear() { events.clear(); buttons = 0; held = false; }
}
