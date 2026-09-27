package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/** Screen-owned stream. Each enter starts a fresh instance at zero; hide/close dispose exactly once. */
final class MenuAmbientAudio implements AutoCloseable {
    private final Path path;
    private final Function<Path, Music> factory;
    private Music music;
    private double silentMs;
    private boolean entered, paused;
    private double pausedPositionMs;
    MenuAmbientAudio(Path path) {
        this(path, p -> Files.isRegularFile(p) ? Gdx.audio.newMusic(Gdx.files.absolute(p.toString())) : null);
    }
    MenuAmbientAudio(Path path, Function<Path, Music> factory) { this.path = path; this.factory = factory; }
    void enter() { enter(false); }
    void enter(boolean startPaused) {
        if (entered) return;
        entered = true; silentMs = 0; paused = false;
        if (path == null) return;
        try {
            music = factory.apply(path);
            if (music != null) {
                music.setLooping(true); music.setVolume(.65f);
                paused = startPaused; pausedPositionMs = 0;
                if (!paused) music.play();
            }
        } catch (RuntimeException ex) { release(); }
    }
    void advance(double deltaMs, float fade) {
        if (!paused) silentMs += Math.max(0, deltaMs);
        if (music == null) return;
        try { music.setVolume(.65f * (1 - Math.max(0, Math.min(1, fade)))); }
        catch (RuntimeException ex) { release(); }
    }
    double positionMs() {
        if (paused) return pausedPositionMs;
        if (music != null) {
            try {
                double position = music.getPosition() * 1000.0;
                if (Double.isFinite(position)) return Math.max(0, position);
                release();
            }
            catch (RuntimeException ex) { release(); }
        }
        return silentMs;
    }
    boolean available() { return music != null; }
    boolean paused() { return paused; }
    void togglePause() {
        if (music == null) return;
        try {
            if (paused) { music.play(); paused = false; }
            else {
                double position = positionMs();
                if (music == null) return;
                music.pause(); pausedPositionMs = position; silentMs = position; paused = true;
            }
        } catch (RuntimeException ex) { release(); }
    }
    private void release() {
        Music old = music; music = null; paused = false;
        if (old == null) return;
        try { old.stop(); } catch (RuntimeException ignored) { }
        finally { try { old.dispose(); } catch (RuntimeException ignored) { } }
    }
    @Override public void close() { release(); entered = false; }
}
