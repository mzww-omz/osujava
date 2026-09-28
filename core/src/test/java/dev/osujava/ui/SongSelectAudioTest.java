package dev.osujava.ui;

import com.badlogic.gdx.audio.Sound;
import dev.osujava.audio.AudioVolumes;
import dev.osujava.skin.SkinAssetResolver;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectAudioTest {
    @TempDir Path root;
    @Test void exactNumberedSamplesFallbackAndEventsAreCachedAndDisposedOnce() throws Exception {
        Path fallback = Files.createDirectory(root.resolve("fallback"));
        for (var cue : SongSelectAudio.Cue.values()) Files.createFile(root.resolve(cue.filename + ".wav"));
        Files.createFile(fallback.resolve("select-expand.ogg"));
        List<String> loads = new ArrayList<>(), played = new ArrayList<>(), disposed = new ArrayList<>();
        var volumes = new AudioVolumes();
        volumes.set(AudioVolumes.Channel.MASTER, .5f);
        volumes.set(AudioVolumes.Channel.EFFECT, .4f);
        var audio = new SongSelectAudio(new SkinAssetResolver(root,fallback), file -> {
            String name = file.path().getFileName().toString(); loads.add(name);
            if (name.equals("select-expand.wav")) throw new IllegalArgumentException("broken");
            return (Sound)Proxy.newProxyInstance(Sound.class.getClassLoader(),new Class[]{Sound.class},(p,m,a) -> {
                if (m.getName().equals("play")) { assertEquals(.2f,(float)a[0],.0001); played.add(name); return 1L; }
                if (m.getName().equals("dispose")) disposed.add(name);
                return null;
            });
        },volumes);
        int loaded = loads.size();
        audio.typed(); audio.typed();
        audio.hover("row:a", SongSelectAudio.Cue.HOVER_ROW);
        audio.hover("row:a", SongSelectAudio.Cue.HOVER_ROW);
        var a = new SongBrowserModel.Selection("a","1");
        audio.selection(a,a);
        audio.selection(a,new SongBrowserModel.Selection("a","2"));
        audio.selection(a,new SongBrowserModel.Selection("b","1"));
        assertEquals(List.of("key-press-1.wav","key-press-2.wav","menuclick.wav","select-difficulty.wav","select-expand.ogg"),played);
        volumes.set(AudioVolumes.Channel.MASTER,0);
        audio.play(SongSelectAudio.Cue.PLAY);
        assertEquals(5,played.size()); assertEquals(loaded,loads.size());
        audio.close(); audio.close();
        assertEquals(SongSelectAudio.Cue.values().length,disposed.size());
        audio.play(SongSelectAudio.Cue.PLAY); assertEquals(5,played.size());
    }
    @Test void missingOrUnavailableAudioNeverStopsSelection() throws Exception {
        Files.createFile(root.resolve("menuhit.wav"));
        var audio = new SongSelectAudio(new SkinAssetResolver(root), file -> { throw new IllegalStateException("device lost"); },new AudioVolumes());
        assertDoesNotThrow(() -> audio.play(SongSelectAudio.Cue.PLAY));
        assertDoesNotThrow(audio::close);
    }
}
