package dev.osujava.gameplay;

public interface GameplaySession {
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
}
