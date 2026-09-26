package dev.osujava.gameplay;

import java.util.List;

public interface GameplaySession {
    /** Authoritative input snapshot; visual consumers never maintain another action set. */
    record PointerState(double x, double y, boolean pressed) { }

    default PointerState pointerState() { return null; }

    GameplayState update();

    void click(double x, double y);

    default void press(GameInputAction action, double x, double y) {
        click(x, y);
    }

    default void release(GameInputAction action) {
        pointerReleased();
    }

    default void pointerMoved(double x, double y) {
    }

    default void pointerReleased() {
    }

    void finish();

    GameplayState state();

    default List<GameplayAudioCue> drainAudioCues() {
        return List.of();
    }
}
