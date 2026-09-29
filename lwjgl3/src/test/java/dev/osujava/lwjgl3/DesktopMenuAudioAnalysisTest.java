package dev.osujava.lwjgl3;

import dev.osujava.ui.MenuBeatTiming;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.List;
import javax.sound.sampled.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopMenuAudioAnalysisTest {
    @TempDir Path temp;
    private Path tone(String name, boolean oppositeStereo) throws Exception {
        int rate = 44100, channels = oppositeStereo ? 2 : 1;
        byte[] bytes = new byte[rate * 6 * channels * 2];
        for (int i = 0; i < rate * 6; i++) {
            int bin = i < rate * 2 ? 32 : 128;
            short pcm = (short) (24000 * Math.sin(2 * Math.PI * bin * i / 2048));
            for (int ch = 0; ch < channels; ch++) {
                short value = ch == 1 ? (short) -pcm : pcm;
                int p = (i * channels + ch) * 2; bytes[p] = (byte) value; bytes[p + 1] = (byte) (value >> 8);
            }
        }
        Path path = temp.resolve(name);
        try (var pcm = new AudioInputStream(new ByteArrayInputStream(bytes), new AudioFormat(rate,16,channels,true,false), rate * 6)) {
            AudioSystem.write(pcm, AudioFileFormat.Type.WAVE, path.toFile());
        }
        return path;
    }
    private static void await(DesktopMenuAudioAnalysis analysis, double time, int expectedBin) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        do {
            analysis.sample(time, MenuBeatTiming.at(List.of(),time,true));
            if (analysis.available() && analysis.frequencyAmplitudes()[expectedBin] > .6f) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("PCM analysis did not reach requested playback position " + time);
    }
    @Test void analysisFollowsPlaybackPositionAndLoopWithBoundedLookahead() throws Exception {
        try (var analysis = new DesktopMenuAudioAnalysis()) {
            analysis.select(tone("tone.wav",false)); await(analysis,0,32);
            float[] paused = analysis.frequencyAmplitudes().clone();
            analysis.sample(0,MenuBeatTiming.at(List.of(),0,true)); assertArrayEquals(paused,analysis.frequencyAmplitudes());
            await(analysis,2500,128); assertTrue(analysis.frequencyAmplitudes()[32] < .0001);
            await(analysis,5200,128); assertTrue(analysis.bufferedFrames() <= 400);
            await(analysis,0,32); assertTrue(analysis.frequencyAmplitudes()[128] < .0001);
            analysis.select(null); assertFalse(analysis.available()); assertEquals(0,analysis.maximumAmplitude());
        }
    }
    @Test void stereoIsMixedForFftButOppositePolarityDoesNotEraseChannelLevels() throws Exception {
        try (var analysis = new DesktopMenuAudioAnalysis()) {
            analysis.select(tone("stereo.wav",true));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!analysis.available() && System.nanoTime() < deadline) {
                analysis.sample(0,MenuBeatTiming.at(List.of(),0,true)); Thread.sleep(5);
            }
            assertTrue(analysis.available()); assertTrue(analysis.maximumAmplitude() > .7f);
            assertArrayEquals(new float[1024],analysis.frequencyAmplitudes());
        }
    }
    @Test void cancellationAndMalformedTracksCannotPublishPreviousTrackData() throws Exception {
        var analysis = new DesktopMenuAudioAnalysis();
        analysis.select(tone("one.wav",false)); await(analysis,0,32);
        Path broken = temp.resolve("broken.ogg"); Files.write(broken,new byte[]{1,2,3});
        analysis.select(broken);
        analysis.sample(0,MenuBeatTiming.at(List.of(),0,true)); assertFalse(analysis.available());
        assertArrayEquals(new float[1024],analysis.frequencyAmplitudes());
        analysis.select(tone("two.wav",false)); await(analysis,0,32);
        analysis.close(); analysis.close();
        analysis.select(temp.resolve("missing.mp3")); assertFalse(analysis.available());
        assertEquals(0,analysis.bufferedFrames());
        assertTrue(analysis.awaitTermination(), "Analysis worker leaked after close");
    }
}
