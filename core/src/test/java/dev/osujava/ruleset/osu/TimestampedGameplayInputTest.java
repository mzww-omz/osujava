package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.gameplay.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimestampedGameplayInputTest {
    @Test void judgementUsesCapturedTimeInsteadOfLaterDispatchClock() throws Exception {
        long[] time = {0};
        GameplaySession session = session(time);
        time[0] = 1100;
        session.input(new GameplayInput(1000, 0, 100, 100, GameplayInput.KEY_LEFT));
        assertEquals(1, session.state().score().count300());
        assertEquals(1000, session.state().currentTimeMs());
        assertEquals(1100, session.inputTimeMs());
    }

    @Test void physicalSourcesRetainLogicalHoldWithoutAnotherPress() throws Exception {
        long[] time = {0};
        GameplaySession session = session(time);
        time[0] = 1000;
        session.input(new GameplayInput(1000, 0, 100, 100, GameplayInput.MOUSE_LEFT));
        session.input(new GameplayInput(1000, 1, 300, 100, GameplayInput.MOUSE_LEFT | GameplayInput.KEY_LEFT));
        session.input(new GameplayInput(1000, 2, 300, 100, GameplayInput.KEY_LEFT));
        assertTrue(session.pointerState().pressed());
        assertEquals(1, session.state().score().count300());
        session.input(new GameplayInput(1000, 3, 300, 100, 0));
        session.input(new GameplayInput(1000, 4, 300, 100, GameplayInput.KEY_LEFT));
        assertEquals(2, session.state().score().count300());
    }

    @Test void lateOrReorderedFramesAreRejectedBeforeChangingState() throws Exception {
        long[] time = {0};
        GameplaySession session = session(time);
        time[0] = 1000;
        session.input(new GameplayInput(1000, 1, 100, 100, GameplayInput.MOUSE_LEFT));
        var before = session.state();
        assertThrows(IllegalArgumentException.class, () -> session.input(new GameplayInput(1000, 1, 300, 100, 0)));
        assertThrows(IllegalArgumentException.class, () -> session.input(new GameplayInput(999, 2, 300, 100, 0)));
        assertEquals(before, session.state());
    }

    private GameplaySession session(long[] time) throws Exception {
        var map = new BeatmapFileParser().parse("osu file format v14\n[HitObjects]\n100,100,1000,1,0\n300,100,1000,1,0", "input.osu");
        return new OsuRuleset().createSession(map.difficulty(), () -> time[0]);
    }
}
