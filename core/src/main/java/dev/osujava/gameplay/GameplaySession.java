package dev.osujava.gameplay;

import java.util.List;

public interface GameplaySession {
    /** Authoritative input snapshot; visual consumers never maintain another action set. */
    record PointerState(double x, double y, boolean pressed) { }

    default PointerState pointerState() { return null; }

    GameplayState update();

    /** Sample the GameClock once, then pass that time unchanged to input(). */
    long inputTimeMs();

    /** Chronological physical input, shared by manual input, Debug Auto, and local replay. */
    void input(GameplayInput input);

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

    default dev.osujava.score.ScoreDetails resultDetails() { return dev.osujava.score.ScoreDetails.LEGACY; }

    default List<GameplayAudioCue> drainAudioCues() {
        return List.of();
    }
}
