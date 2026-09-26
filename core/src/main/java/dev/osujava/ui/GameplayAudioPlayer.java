package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.gameplay.GameplayAudioCue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Plays only local beatmap samples; a short generated click covers missing default samples. */
final class GameplayAudioPlayer implements AutoCloseable {
    private static final String[] EXTENSIONS = {".wav", ".ogg", ".mp3"};
    private final Path beatmapDirectory;
    private final Map<String, Sound> loaded = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private Path fallbackPath;
    private Sound fallback;
    private boolean fallbackAttempted;

    GameplayAudioPlayer(Path beatmapDirectory) {
        this.beatmapDirectory = beatmapDirectory;
    }

    void play(List<GameplayAudioCue> cues) {
        for (GameplayAudioCue cue : cues) {
            if (cue.volume() <= 0) continue;
            boolean played = false;
            for (String name : cue.sampleNames()) {
                Sound sound = load(name);
                if (sound == null) continue;
                try {
                    sound.play(cue.volume());
                    played = true;
                } catch (GdxRuntimeException ignored) {
                    // A failed local sample must not stop gameplay.
                }
            }
            if (!played) {
                Sound click = fallback();
                if (click != null) {
                    try {
                        click.play(cue.volume() * 0.5f);
                    } catch (GdxRuntimeException ignored) {
                        // Audio hardware can disappear while a local game is running.
                    }
                }
            }
        }
    }

    private Sound load(String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,64}") || missing.contains(name)) return null;
        Sound cached = loaded.get(name);
        if (cached != null) return cached;
        if (beatmapDirectory != null) {
            for (String extension : EXTENSIONS) {
                Path file = beatmapDirectory.resolve(name + extension);
                if (!Files.isRegularFile(file)) continue;
                try {
                    Sound sound = Gdx.audio.newSound(Gdx.files.absolute(file.toString()));
                    loaded.put(name, sound);
                    return sound;
                } catch (GdxRuntimeException | IllegalArgumentException ignored) {
                    // Try another local encoding, then the generated fallback.
                }
            }
        }
        missing.add(name);
        return null;
    }

    private Sound fallback() {
        if (fallback != null) return fallback;
        if (fallbackAttempted) return null;
        fallbackAttempted = true;
        try {
            fallbackPath = Files.createTempFile("osujava-hit-", ".wav");
            Files.write(fallbackPath, fallbackWave());
            fallback = Gdx.audio.newSound(Gdx.files.absolute(fallbackPath.toString()));
        } catch (IOException | GdxRuntimeException | IllegalArgumentException ignored) {
            return null;
        }
        return fallback;
    }

    private static byte[] fallbackWave() {
        int sampleRate = 44_100;
        int frames = sampleRate / 14;
        ByteBuffer wave = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN);
        wave.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + frames * 2);
        wave.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16);
        wave.putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2);
        wave.putShort((short) 2).putShort((short) 16);
        wave.put(new byte[]{'d', 'a', 't', 'a'}).putInt(frames * 2);
        Random noise = new Random(0x05A);
        for (int i = 0; i < frames; i++) {
            double time = (double) i / sampleRate;
            double envelope = Math.exp(-55 * time);
            double value = envelope * (0.45 * Math.sin(2 * Math.PI * 880 * time)
                    + 0.18 * (noise.nextDouble() * 2 - 1));
            wave.putShort((short) Math.round(Math.max(-1, Math.min(1, value)) * Short.MAX_VALUE));
        }
        return wave.array();
    }

    @Override
    public void close() {
        for (Sound sound : loaded.values()) sound.dispose();
        if (fallback != null) fallback.dispose();
        if (fallbackPath != null) {
            try {
                Files.deleteIfExists(fallbackPath);
            } catch (IOException ignored) {
                // The OS will clean its temporary directory.
            }
        }
    }
}
