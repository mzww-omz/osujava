package dev.osujava.ui;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.gameplay.GameplayAudioCue;
import dev.osujava.skin.SkinAssetResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameplaySkinAudioTest {
    @TempDir Path root;

    @Test void indexedBeatmapSampleWinsThenCustomSkinThenBundleWithCachedOwnership() throws Exception {
        Path beatmap = Files.createDirectory(root.resolve("map"));
        Path custom = Files.createDirectory(root.resolve("skin"));
        Path beatmapSound = Files.write(beatmap.resolve("normal-hitnormal2.wav"), new byte[]{1});
        Path customSound = Files.write(custom.resolve("soft-hitnormal.wav"), new byte[]{1});
        var loaded = new ArrayList<SkinAssetResolver.AssetFile>();
        int[] plays = {0}, disposals = {0};
        Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class[]{Sound.class}, (p, m, a) -> {
            if (m.getName().equals("play")) { plays[0]++; return 1L; }
            if (m.getName().equals("dispose")) disposals[0]++;
            return null;
        });
        var resolver = SkinAssetResolver.withBundledDefault(custom, null);
        try (var audio = new GameplayAudioPlayer(beatmap, resolver, file -> { loaded.add(file); return sound; })) {
            var cues = List.of(new GameplayAudioCue(0, List.of("normal-hitnormal2", "soft-hitnormal3", "drum-hitnormal"), 1));
            audio.play(cues); audio.play(cues);
            assertEquals(3, loaded.size());
            assertEquals(beatmapSound, loaded.get(0).path());
            assertEquals(customSound, loaded.get(1).path());
            assertEquals("skins/default/drum-hitnormal.wav", loaded.get(2).classpathResource());
            assertEquals(6, plays[0]);
        }
        assertEquals(3, disposals[0]);
    }

    @Test void corruptOrEmptyCustomSampleContinuesToBundledSound() throws Exception {
        Files.createFile(root.resolve("normal-hitnormal.wav"));
        var attempts = new ArrayList<SkinAssetResolver.AssetFile>();
        Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class[]{Sound.class}, (p, m, a) ->
                m.getName().equals("play") ? 1L : null);
        try (var audio = new GameplayAudioPlayer(null, SkinAssetResolver.withBundledDefault(root, null), file -> {
            attempts.add(file);
            if (file.classpathResource() == null) throw new GdxRuntimeException("invalid WAV");
            return sound;
        })) {
            audio.play(List.of(new GameplayAudioCue(0, List.of("normal-hitnormal"), 1)));
            assertEquals(2, attempts.size());
            assertNotNull(attempts.getLast().classpathResource());
        }
    }
}
