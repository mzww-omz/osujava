package dev.osujava.gameplay;

public final class ElapsedGameClock implements GameClock {
    private final long startNanos = System.nanoTime();
    private final long endAtMs;

    public ElapsedGameClock(long endAtMs) {
        this.endAtMs = Math.max(0, endAtMs);
    }

    @Override
    public long nowMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @Override
    public boolean finished() {
        return nowMs() >= endAtMs;
    }
}
