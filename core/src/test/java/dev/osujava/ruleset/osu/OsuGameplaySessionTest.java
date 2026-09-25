package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.ScoreState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsuGameplaySessionTest {
    @Test
    void judgesClicksByClockOffsetAndIgnoresUnsupportedHitObjects() {
        ManualClock clock = new ManualClock();
        BeatmapDifficulty difficulty = difficulty(List.of(
                object(256, 192, 1000, 1),
                object(256, 192, 2000, 1),
                object(256, 192, 3000, 1),
                object(256, 192, 4000, 1),
                object(100, 100, 4100, 2)));
        OsuGameplaySession session = new OsuGameplaySession(difficulty, clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        session.click(256, 192);
        clock.set(2080);
        session.click(256, 192);
        clock.set(3140);
        session.click(256, 192);
        clock.set(4150);
        session.click(256, 192);

        GameplayState state = session.update();
        assertTrue(state.completed());
        assertEquals(1, state.score().count300());
        assertEquals(1, state.score().count100());
        assertEquals(1, state.score().count50());
        assertEquals(1, state.score().misses());
        assertEquals(0.375, state.score().accuracy());
    }

    @Test
    void requiresBothTimingAndPointerPositionAndExpiresAsMiss() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(object(256, 192, 1000, 1))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        session.click(400, 300);
        assertEquals(0, session.state().score().score());
        clock.set(1151);
        GameplayState state = session.update();
        assertTrue(state.completed());
        assertEquals(Judgement.MISS.scoreValue(), state.score().score());
        assertEquals(1, state.score().misses());
    }

    @Test
    void doesNotRunSliderGameplayInOsuMilestone() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(object(256, 192, 1000, 2))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        assertTrue(session.state().completed());
        assertTrue(session.state().circles().isEmpty());
        assertFalse(session.state().score().misses() > 0);
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects) {
        return new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0, "", "",
                new DifficultySettings(5, 5, 5, 5, 1.4, 1), List.of(), objects, null, null);
    }

    private HitObject object(double x, double y, long time, int type) {
        return new HitObject(x, y, time, HitObject.typeFromBits(type), type, 0);
    }

    private static final class ManualClock implements GameClock {
        private long timeMs;

        @Override
        public long nowMs() {
            return timeMs;
        }

        void set(long timeMs) {
            this.timeMs = timeMs;
        }
    }
}
