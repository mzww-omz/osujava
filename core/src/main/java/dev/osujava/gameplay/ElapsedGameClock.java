package dev.osujava.gameplay;

public final class ElapsedGameClock implements GameClock {
    private final long startNanos = System.nanoTime();
    private final long endAtMs;
    private final int leadInMs;

    public ElapsedGameClock(long endAtMs) {
        this(endAtMs, 0);
    }

    public ElapsedGameClock(long endAtMs, int leadInMs) {
        this.endAtMs = Math.max(0, endAtMs);
        this.leadInMs = Math.max(0, leadInMs);
    }

    @Override
    public long nowMs() {
        return (System.nanoTime() - startNanos) / 1_000_000 - leadInMs;
    }

    @Override
    public boolean finished() {
        return nowMs() >= endAtMs;
    }
}
