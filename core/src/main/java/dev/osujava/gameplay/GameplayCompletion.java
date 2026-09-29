package dev.osujava.gameplay;

/** Presentation delay is independent of audio EOF and never forces pending objects to MISS. */
public record GameplayCompletion(long resultsAtMs) {
    public boolean ready(GameplayState state) {
        return state.completed() && state.currentTimeMs() >= resultsAtMs;
    }
}
