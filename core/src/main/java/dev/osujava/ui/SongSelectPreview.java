package dev.osujava.ui;

import com.badlogic.gdx.audio.Music;
import dev.osujava.audio.AudioVolumes;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/** Owns only the selected local preview stream, independently of the gameplay clock. */
final class SongSelectPreview implements AutoCloseable {
    private final Function<Path, Music> factory;
    private final AudioVolumes volumes;
    private Path selected;
    private Music music;
    private double positionMs;
    private long attemptedSelection = Long.MIN_VALUE;

    SongSelectPreview(Function<Path, Music> factory, AudioVolumes volumes) {
        this.factory = factory;
        this.volumes = volumes;
    }

    void select(Path path, int previewTimeMs) {
        select(path, previewTimeMs, 0);
    }

    /** Healthy shared audio continues; a new chart selection may retry a failed stream once. */
    void select(Path path, int previewTimeMs, long selection) {
        if (Objects.equals(selected, path)) {
            if (music != null) { attemptedSelection = selection; return; }
            if (attemptedSelection == selection) return;
        }
        release();
        selected = path;
        attemptedSelection = selection;
        positionMs = 0;
        if (path == null) return;
        try {
            music = factory.apply(path);
            if (music == null) return;
            music.setLooping(true);
            volumes.musicGain(music, 1);
            music.play();
            // An unspecified PreviewTime starts at zero; no unverified stable heuristic.
            if (previewTimeMs > 0) music.setPosition(previewTimeMs / 1000f);
            positionMs = Math.max(0, previewTimeMs);
        } catch (RuntimeException ignored) { release(); }
    }

    void advance(float fade) {
        if (music == null) return;
        try {
            volumes.musicGain(music, 1 - AudioVolumes.clamp(fade));
            double current = music.getPosition() * 1000.0;
            if (Double.isFinite(current) && current >= 0) positionMs = current;
        } catch (RuntimeException ignored) { release(); }
    }

    boolean available() { return music != null; }
    double positionMs() { return positionMs; }

    private void release() {
        Music old = music;
        music = null;
        if (old == null) return;
        volumes.removeMusic(old);
        try { old.stop(); } catch (RuntimeException ignored) { }
        try { old.dispose(); } catch (RuntimeException ignored) { }
    }

    @Override public void close() { release(); selected = null; attemptedSelection = Long.MIN_VALUE; positionMs = 0; }
}
