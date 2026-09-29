package dev.osujava.gameplay;

import com.badlogic.gdx.audio.Music;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Audio position while playing, monotonic time after EOF. EOF does not end the ruleset. */
public final class MusicGameClock implements GameClock {
    private final Music music;
    private final LongSupplier nanos;
    private final int leadInMs;
    private boolean started, audioStarted, completed, paused;
    private long originNanos, anchorNanos, positionMs, pauseNanos;

    public MusicGameClock(Music music) { this(music, 0); }
    public MusicGameClock(Music music, int leadInMs) { this(music, leadInMs, System::nanoTime); }

    public MusicGameClock(Music music, int leadInMs, LongSupplier nanos) {
        if (leadInMs < 0) throw new IllegalArgumentException("Negative lead-in");
        this.music = Objects.requireNonNull(music);
        this.nanos = Objects.requireNonNull(nanos);
        this.leadInMs = leadInMs;
        music.setOnCompletionListener(ignored -> completed());
    }

    public synchronized void start() {
        started = true;
        audioStarted = completed = paused = false;
        originNanos = anchorNanos = nanos.getAsLong();
        positionMs = -leadInMs;
        nowMs();
    }

    private synchronized void completed() {
        if (!started || !audioStarted || completed) return;
        long now = paused ? pauseNanos : nanos.getAsLong();
        positionMs += Math.max(0, (now - anchorNanos) / 1_000_000);
        anchorNanos = now;
        completed = true;
    }

    @Override public synchronized long nowMs() {
        if (!started || paused) return positionMs;
        long now = nanos.getAsLong();
        if (!audioStarted) {
            positionMs = Math.min(0, (now - originNanos) / 1_000_000 - leadInMs);
            if (positionMs < 0) return positionMs;
            audioStarted = true;
            anchorNanos = now;
            music.play();
        }
        if (completed) return positionMs + Math.max(0, (now - anchorNanos) / 1_000_000);
        if (music.isPlaying()) {
            positionMs = Math.max(positionMs, Math.round(music.getPosition() * 1000.0));
            anchorNanos = now;
        }
        return positionMs;
    }

    public synchronized void pause() {
        if (!started || paused) return;
        positionMs = nowMs();
        pauseNanos = nanos.getAsLong();
        paused = true;
        if (audioStarted && !completed) music.pause();
    }

    public synchronized void resume() {
        if (!paused) return;
        long now = nanos.getAsLong();
        originNanos += now - pauseNanos;
        anchorNanos = now;
        paused = false;
        if (audioStarted && !completed) music.play();
    }

    /** Transport status only; gameplay completion is owned by the ruleset. */
    @Override public synchronized boolean finished() { return completed; }
}
