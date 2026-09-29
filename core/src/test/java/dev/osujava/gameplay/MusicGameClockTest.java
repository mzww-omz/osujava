package dev.osujava.gameplay;

import com.badlogic.gdx.audio.Music;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class MusicGameClockTest {
    @Test void leadInDelaysAudioAndPausePreservesRemainingSilence() {
        Fixture f = new Fixture(750);
        f.clock.start();
        assertEquals(-750, f.clock.nowMs());
        assertFalse(f.playing);
        f.time = 500;
        assertEquals(-250, f.clock.nowMs());
        f.clock.pause();
        f.time = 2500;
        assertEquals(-250, f.clock.nowMs());
        f.clock.resume();
        f.time = 2749;
        assertEquals(-1, f.clock.nowMs());
        assertFalse(f.playing);
        f.time = 2750;
        assertEquals(0, f.clock.nowMs());
        assertTrue(f.playing);
    }

    @Test void earlyAudioEofKeepsClockMovingThroughRemainingObjectsAndPause() {
        Fixture f = new Fixture(0);
        f.clock.start();
        f.time = 1000; f.position = 1;
        assertEquals(1000, f.clock.nowMs());
        f.time = 1100;
        f.playing = false; f.position = 0;
        f.completion.onCompletion(f.music);
        assertTrue(f.clock.finished());
        assertEquals(1100, f.clock.nowMs());
        f.time = 2000;
        assertEquals(2000, f.clock.nowMs());
        f.clock.pause();
        f.time = 5000;
        assertEquals(2000, f.clock.nowMs());
        f.clock.resume();
        f.time = 5250;
        assertEquals(2250, f.clock.nowMs());
        assertFalse(f.playing, "Resume after EOF must not restart the audio");
    }

    @Test void audioPositionRegressionDoesNotRewindJudgements() {
        Fixture f = new Fixture(0);
        f.clock.start();
        f.time = 1000; f.position = 1;
        assertEquals(1000, f.clock.nowMs());
        f.time = 1010; f.position = 0.99f;
        assertEquals(1000, f.clock.nowMs());
        f.clock.pause();
        f.time = 2010; f.clock.resume();
        f.time = 2110; f.position = 1.1f;
        assertEquals(1100, f.clock.nowMs());
    }

    @Test void completionRequiresAllObjectsAndItsOwnDeadline() {
        GameplayCompletion completion = new GameplayCompletion(3500);
        var score = new ScoreTracker().snapshot();
        assertFalse(completion.ready(new GameplayState(5000, java.util.List.of(), score, false)));
        assertFalse(completion.ready(new GameplayState(3499, java.util.List.of(), score, true)));
        assertTrue(completion.ready(new GameplayState(3500, java.util.List.of(), score, true)));
    }

    private static final class Fixture {
        long time;
        float position;
        boolean playing;
        Music.OnCompletionListener completion;
        final Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class[]{Music.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setOnCompletionListener" -> { completion = (Music.OnCompletionListener) args[0]; yield null; }
                    case "play" -> { playing = true; yield null; }
                    case "pause" -> { playing = false; yield null; }
                    case "isPlaying" -> playing;
                    case "getPosition" -> position;
                    default -> null;
                });
        final MusicGameClock clock;
        Fixture(int leadIn) { clock = new MusicGameClock(music, leadIn, () -> time * 1_000_000); }
    }
}
