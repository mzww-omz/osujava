package dev.osujava.gameplay;

public interface GameplaySession {
    GameplayState update();

    void click(double x, double y);

    void finish();

    GameplayState state();
}
