package dev.osujava.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Opt-in real desktop playback smoke test. Fixtures stay outside Git and need no user Library. */
public final class MainMenuAudioSmoke extends ApplicationAdapter {
    private final List<Path> fixtures;
    private MenuAmbientAudio audio;
    private int index, pass, stage;
    private double elapsed, pausedPosition;
    private MainMenuAudioSmoke(Path directory) {
        fixtures = List.of(directory.resolve("tone.mp3"),directory.resolve("tone.ogg"),directory.resolve("tone.wav"));
    }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java ambient audio smoke"); config.setWindowedMode(320,200); config.setForegroundFPS(60);
        new Lwjgl3Application(new MainMenuAudioSmoke(Path.of(args[0])),config);
    }
    @Override public void create() {
        for (Path fixture : fixtures) if (!Files.isRegularFile(fixture)) throw new IllegalArgumentException("Missing fixture: " + fixture);
        start();
    }
    private void start() {
        audio = new MenuAmbientAudio(fixtures.get(index)); audio.enter(); elapsed = 0; stage = 0;
        if (!audio.available()) throw new AssertionError("Cannot open/play " + fixtures.get(index));
        if (audio.positionMs() > 100) throw new AssertionError("Fresh stream did not start at zero");
    }
    @Override public void render() {
        elapsed += Gdx.graphics.getDeltaTime() * 1000;
        float fade = stage == 3 ? MainMenuMotion.clamp(elapsed / 200) : 0;
        audio.advance(Gdx.graphics.getDeltaTime() * 1000,fade);
        if (!audio.available()) throw new AssertionError("Playback became unavailable: " + fixtures.get(index));
        if (stage == 0) {
            if (elapsed < 300) return;
            if (audio.positionMs() <= 100) throw new AssertionError("Playback clock did not progress: " + fixtures.get(index));
            audio.togglePause(); pausedPosition = audio.positionMs();
            if (!audio.paused()) throw new AssertionError("Pause failed");
            elapsed = 0; stage = 1; return;
        }
        if (stage == 1) {
            if (audio.positionMs() != pausedPosition) throw new AssertionError("Paused clock advanced");
            if (elapsed < 200) return;
            audio.togglePause(); if (audio.paused()) throw new AssertionError("Resume failed");
            if (Math.abs(audio.positionMs() - pausedPosition) > 80) throw new AssertionError("Resume lost position");
            elapsed = 0; stage = 2; return;
        }
        if (stage == 2) {
            if (elapsed < 200) return;
            if (audio.positionMs() <= pausedPosition + 50) throw new AssertionError("Resumed clock did not progress");
            elapsed = 0; stage = 3; return;
        }
        if (elapsed < 200) return;
        audio.close(); audio.close(); audio.advance(0,1); audio.positionMs();
        if (audio.available()) throw new AssertionError("Stream survived close");
        if (pass++ == 0) { start(); return; } // Restart must create a new stream at zero.
        System.out.println("Ambient audio smoke passed: " + fixtures.get(index).getFileName());
        pass = 0;
        if (++index == fixtures.size()) Gdx.app.exit(); else start();
    }
    @Override public void dispose() { if (audio != null) audio.close(); }
}
