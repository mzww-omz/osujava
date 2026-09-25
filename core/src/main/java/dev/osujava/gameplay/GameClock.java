package dev.osujava.gameplay;

public interface GameClock {
    long nowMs();

    default boolean finished() {
        return false;
    }
}
