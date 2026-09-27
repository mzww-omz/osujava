package dev.osujava.audio;

import com.badlogic.gdx.audio.Music;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static dev.osujava.audio.AudioVolumes.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioVolumesTest {
    @Test void clampsEachIndependentChannelIncludingNonFiniteValues() {
        var volumes = new AudioVolumes();
        for (var channel : AudioVolumes.Channel.values()) {
            volumes.set(channel, -1); assertEquals(0, volumes.get(channel));
            volumes.set(channel, 2); assertEquals(1, volumes.get(channel));
            volumes.set(channel, Float.NaN); assertEquals(0, volumes.get(channel));
            volumes.set(channel, Float.POSITIVE_INFINITY); assertEquals(1, volumes.get(channel));
        }
        volumes.set(MASTER, .5f); volumes.set(MUSIC, .2f); volumes.set(EFFECT, .8f);
        assertEquals(.5f, volumes.get(MASTER)); assertEquals(.2f, volumes.get(MUSIC));
        assertEquals(.8f, volumes.get(EFFECT));
        assertEquals(.1f, volumes.musicOutput(), 1e-6); assertEquals(.4f, volumes.effectOutput(), 1e-6);
        volumes.set(MASTER, 0);
        assertEquals(0, volumes.musicOutput()); assertEquals(0, volumes.effectOutput());
        assertEquals(.2f, volumes.get(MUSIC)); assertEquals(.8f, volumes.get(EFFECT));
    }
    @Test void registeredGameplayMusicChangesImmediatelyAndNeverSeeksOrPauses() {
        var volumes = new AudioVolumes(); float[] gain = {-1}; int[] calls = {0};
        Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class[]{Music.class}, (p,m,a) -> {
            assertEquals("setVolume", m.getName(), "Volume changes must not touch playback or its clock");
            gain[0] = (float) a[0]; calls[0]++; return null;
        });
        volumes.musicGain(music, 1); assertEquals(1, gain[0]);
        volumes.set(MUSIC, .4f); assertEquals(.4f, gain[0]);
        volumes.set(MASTER, .5f); assertEquals(.2f, gain[0]);
        volumes.set(EFFECT, 0); assertEquals(3, calls[0]);
        volumes.removeMusic(music); volumes.set(MASTER, 1); assertEquals(3, calls[0]);
    }
    @Test void aFailedStreamDoesNotBreakVolumeInputOrOtherStreams() {
        var volumes = new AudioVolumes(); boolean[] failed = {false}; float[] healthyGain = {1};
        Music broken = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(),new Class[]{Music.class},(p,m,a) -> {
            if (failed[0]) throw new IllegalArgumentException("Audio device lost");
            return null;
        });
        Music healthy = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(),new Class[]{Music.class},(p,m,a) -> {
            healthyGain[0] = (float)a[0]; return null;
        });
        volumes.musicGain(broken,1); volumes.musicGain(healthy,1); failed[0] = true;
        assertDoesNotThrow(() -> volumes.set(MASTER,.5f)); assertEquals(.5f,healthyGain[0]);
        assertDoesNotThrow(() -> volumes.set(MUSIC,.2f)); assertEquals(.1f,healthyGain[0],1e-6);
    }
}
