package dev.osujava.gameplay;

public interface GameplaySession {
    GameplayState update();

    void click(double x, double y);

    default void pointerMoved(double x, double y) {
    }

    default void pointerReleased() {
    }

    void finish();

    GameplayState state();
}
