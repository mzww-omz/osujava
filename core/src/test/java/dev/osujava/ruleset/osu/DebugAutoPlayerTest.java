package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.JudgementWindows;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugAutoPlayerTest {
    @Test
    void movesToCircleBeforeItsTimeAndClicksThroughTheSessionForA300() {
        ManualClock clock = new ManualClock();
        BeatmapDifficulty difficulty = difficulty(List.of(circle(312, 144, 1000)));
        OsuGameplaySession session = new OsuGameplaySession(difficulty, clock,
                JudgementWindows.fromOverallDifficulty(difficulty.settings().overallDifficulty()));
        DebugAutoPlayer auto = new DebugAutoPlayer(difficulty, clock, session);

        auto.update();
        assertEquals(312, auto.cursorX());
        assertEquals(144, auto.cursorY());
        assertEquals(0, session.state().score().count300());

        for (long now = 16; now <= 1104; now += 16) {
            clock.set(now);
            auto.update();
            session.update();
        }

        GameplayState result = session.state();
        assertTrue(auto.finished());
        assertEquals(1, result.score().count300());
        assertEquals(0, result.score().misses());
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects) {
        return new BeatmapDifficulty("Song", "Artist", "Creator", "Auto fixture", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 1), List.of(), objects, null, null);
    }

    private HitObject circle(double x, double y, long time) {
        return new HitObject(x, y, time, HitObject.Type.CIRCLE, 1, 0);
    }

    private static final class ManualClock implements GameClock {
        private long now;

        @Override
        public long nowMs() {
            return now;
        }

        void set(long now) {
            this.now = now;
        }
    }
}
