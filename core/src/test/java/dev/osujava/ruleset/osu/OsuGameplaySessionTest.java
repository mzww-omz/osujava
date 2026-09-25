package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.ScoreState;
import dev.osujava.beatmap.SliderData;
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
    void playsSliderHeadTrackingRepeatsAndTailAgainstGameClock() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 2))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        assertFalse(session.state().completed());
        clock.set(1000);
        GameplayState visible = session.update();
        assertEquals(1, visible.sliders().size());
        assertEquals(0, visible.sliders().getFirst().progress(), 1e-6);
        assertFalse(visible.sliders().getFirst().headJudged());

        session.click(100, 100);
        assertTrue(session.state().sliders().getFirst().headHit());
        assertEquals(1, session.state().score().count300());

        clock.set(1500);
        session.pointerMoved(240, 100);
        assertEquals(600, session.update().score().score());
        clock.set(2000);
        session.pointerMoved(380, 100);
        assertEquals(900, session.update().score().score());
        clock.set(2500);
        session.pointerMoved(240, 100);
        assertEquals(1200, session.update().score().score());
        clock.set(2964);
        session.pointerMoved(110.08, 100);
        GameplayState complete = session.update();

        assertTrue(complete.completed());
        assertEquals(1500, complete.score().score());
        assertEquals(1, complete.score().accuracy(), 1e-6);
        assertEquals(5, complete.score().count300());
    }

    @Test
    void sliderTrackingLossMissesNestedEventsAndReducesAccuracy() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 2))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        session.click(100, 100);
        session.pointerMoved(20, 20);
        clock.set(1500);
        GameplayState state = session.update();

        assertEquals(1, state.score().count300());
        assertEquals(1, state.score().misses());
        assertEquals(0.5, state.score().accuracy(), 1e-6);
    }

    @Test
    void releasingPointerStopsSliderTracking() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 2))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        session.click(100, 100);
        session.pointerReleased();
        clock.set(1500);
        session.pointerMoved(240, 100);
        GameplayState state = session.update();

        assertFalse(state.sliders().getFirst().tracking());
        assertEquals(1, state.score().misses());
        assertEquals(0.5, state.score().accuracy(), 1e-6);
    }

    @Test
    void sliderTailWaitsForLastTickAtTheEndLeniencyBoundary() {
        ManualClock clock = new ManualClock();
        HitObject slider = sliderObject(100, 100, 1000, 100, 1);
        BeatmapDifficulty denseTicks = new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 50),
                List.of(new dev.osujava.beatmap.TimingPoint(0, 500, 4, 0, 0, 100, true, 0)),
                List.of(slider), null, null);
        OsuGameplaySession session = new OsuGameplaySession(denseTicks, clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        SliderPath path = new SliderPath(slider.x(), slider.y(), slider.sliderData());
        SliderTiming timing = SliderTiming.calculate(denseTicks, slider, path);
        List<SliderEvent> events = SliderEventGenerator.generate(timing, path);
        double tailStart = SliderEventGenerator.tailJudgementStartTime(timing);

        clock.set(1000);
        session.click(100, 100);
        clock.set((long) Math.ceil(tailStart));
        double progress = timing.progressAt(clock.nowMs());
        BeatmapPoint initialBall = path.positionAt(progress);
        session.pointerMoved(initialBall.x(), initialBall.y());
        GameplayState atLeniencyStart = session.update();
        long ticksDueAtLeniencyStart = events.stream()
                .filter(event -> event.type() == SliderEvent.Type.TICK && event.timeMs() <= clock.nowMs())
                .count();

        assertEquals(1 + ticksDueAtLeniencyStart, atLeniencyStart.score().count300(),
                "The tail must wait while an earlier tick is still pending");

        clock.set((long) Math.ceil(timing.endTimeMs() - 17));
        progress = timing.progressAt(clock.nowMs());
        BeatmapPoint ball = path.positionAt(progress);
        session.pointerMoved(ball.x(), ball.y());
        GameplayState afterLastTick = session.update();

        assertEquals(1 + events.stream().filter(event -> event.type() == SliderEvent.Type.TICK).count() + 1,
                afterLastTick.score().count300());
        assertTrue(afterLastTick.completed());
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects) {
        return new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0, "", "",
                new DifficultySettings(5, 5, 5, 5, 1.4, 1), List.of(), objects, null, null);
    }

    private HitObject object(double x, double y, long time, int type) {
        return new HitObject(x, y, time, HitObject.typeFromBits(type), type, 0);
    }

    private HitObject sliderObject(double x, double y, long time, double length, int slides) {
        return new HitObject(x, y, time, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(x, y), new BeatmapPoint(x + length, y)))), slides - 1, length));
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
