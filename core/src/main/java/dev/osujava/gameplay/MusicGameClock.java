package dev.osujava.gameplay;

import com.badlogic.gdx.audio.Music;

public final class MusicGameClock implements GameClock {
    private final Music music;
    private volatile boolean completed;
    private volatile long lastPositionMs;

    public MusicGameClock(Music music) {
        this.music = music;
        music.setOnCompletionListener(ignored -> completed = true);
    }

    public void start() {
        completed = false;
        lastPositionMs = 0;
        music.play();
    }

    @Override
    public long nowMs() {
        if (!completed && music.isPlaying()) {
            lastPositionMs = Math.max(lastPositionMs, Math.round(music.getPosition() * 1000));
        }
        return lastPositionMs;
    }

    @Override
    public boolean finished() {
        return completed;
    }
}
