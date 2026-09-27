package dev.osujava.ui;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import dev.osujava.audio.AudioVolumes;
import dev.osujava.gameplay.GameplayAudioCue;
import dev.osujava.skin.SkinAssetResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static dev.osujava.audio.AudioVolumes.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

class VolumeAudioIntegrationTest {
    @TempDir Path temp;
    @Test void menuFadeAndGameplaySamplesUseSharedMasterAndTheirOwnChannel() throws Exception {
        var volumes = new AudioVolumes(); float[] musicGain = {-1}; List<Float> effects = new ArrayList<>();
        Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(),new Class[]{Music.class},(p,m,a) -> {
            if (m.getName().equals("setVolume")) musicGain[0] = (float)a[0];
            return null;
        });
        Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),new Class[]{Sound.class},(p,m,a) -> {
            if (m.getName().equals("play")) { effects.add((float)a[0]); return 1L; }
            return null;
        });
        Files.write(temp.resolve("normal-hitnormal.wav"),new byte[]{1});
        var cues = List.of(new GameplayAudioCue(0,List.of("normal-hitnormal"),.8f));
        try (var menu = new MenuAmbientAudio(Path.of("fixture.ogg"),p -> music,volumes);
             var player = new GameplayAudioPlayer(temp,new SkinAssetResolver(null,null),p -> sound,volumes)) {
            menu.enter(); volumes.set(MASTER,.5f); volumes.set(MUSIC,.4f); volumes.set(EFFECT,.2f);
            assertEquals(.65f * .5f * .4f,musicGain[0],1e-6);
            player.play(cues); assertEquals(.8f * .5f * .2f,effects.getLast(),1e-6);
            menu.advance(100,.5f); assertEquals(.65f * .5f * .4f * .5f,musicGain[0],1e-6);
            volumes.set(MASTER,1); assertEquals(.65f * .4f * .5f,musicGain[0],1e-6);
            player.play(cues); assertEquals(.8f * .2f,effects.getLast(),1e-6);
            volumes.set(MUSIC,0); player.play(cues); assertEquals(.8f * .2f,effects.getLast(),1e-6);
            volumes.set(EFFECT,0); player.play(cues); assertEquals(3,effects.size());
        }
        volumes.set(MASTER,0); // Disposed menu stream was unregistered.
    }
}
