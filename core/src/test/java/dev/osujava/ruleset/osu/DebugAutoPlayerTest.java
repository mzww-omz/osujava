package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import dev.osujava.beatmap.SpinnerData;
import dev.osujava.beatmap.TimingPoint;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementWindows;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            auto.afterSessionUpdate();
        }

        GameplayState result = session.state();
        assertTrue(auto.finished());
        assertEquals(1, result.score().count300());
        assertEquals(0, result.score().misses());
    }

    @Test
    void tracksRepeatingSliderThroughNormalEventsAndReleasesAfterItsTail() {
        ManualClock clock = new ManualClock();
        HitObject slider = slider(100, 100, 1000, 380, 1);
        BeatmapDifficulty difficulty = difficulty(List.of(slider));
        OsuGameplaySession session = session(difficulty, clock);
        DebugAutoPlayer auto = new DebugAutoPlayer(difficulty, clock, session);
        SliderPath path = new SliderPath(slider.x(), slider.y(), slider.sliderData());
        SliderTiming timing = SliderTiming.calculate(difficulty, slider, path);

        for (long now = 0; now <= 3024; now += 16) {
            clock.set(now);
            auto.update();
            GameplayState state = session.update();
            if (now == 1504 || now == 2504) {
                BeatmapPoint expected = path.positionAt(timing.progressAt(now));
                assertEquals(expected.x(), auto.cursorX(), 1e-6);
                assertEquals(expected.y(), auto.cursorY(), 1e-6);
            }
            auto.afterSessionUpdate();
        }

        GameplayState result = session.state();
        assertTrue(auto.finished());
        assertFalse(auto.primaryPressed());
        assertTrue(result.completed());
        assertEquals(5, result.score().count300(), "Head, two ticks, repeat, and tail use standard Slider tracking");
        assertEquals(0, result.score().misses());
        assertEquals(1, result.score().accuracy(), 1e-6);
        assertFalse(result.sliders().getFirst().tracking());
    }

    @Test
    void rotatesSpinnerAtAbsoluteTimeAndClearsTheExistingRequirement() {
        ManualClock clock = new ManualClock();
        HitObject spinner = spinner(256, 192, 1000, 4000);
        BeatmapDifficulty difficulty = difficulty(List.of(spinner));
        OsuGameplaySession session = session(difficulty, clock);
        DebugAutoPlayer auto = new DebugAutoPlayer(difficulty, clock, session);
        int requiredSpins = SpinnerRequirements.calculate(spinner.durationMs(),
                difficulty.settings().overallDifficulty()).spinsRequired();
        double peakProgress = 0;
        boolean sawSpinnerPosition = false;

        for (long now = 0; now <= 4016; now += 16) {
            clock.set(now);
            auto.update();
            GameplayState state = session.update();
            if (now == 1008) {
                assertEquals(300, DebugAutoPlayer.SPINNER_RPM, 0);
                assertEquals(80, Math.hypot(auto.cursorX() - spinner.x(), auto.cursorY() - spinner.y()), 1e-6);
                sawSpinnerPosition = true;
            }
            if (!state.spinners().isEmpty()) peakProgress = Math.max(peakProgress, state.spinners().getFirst().progress());
            auto.afterSessionUpdate();
        }

        GameplayState result = session.state();
        assertTrue(sawSpinnerPosition);
        assertTrue(peakProgress >= 1);
        assertTrue(result.spinners().getFirst().completedSpins() >= requiredSpins);
        assertEquals(Judgement.HIT300, result.spinners().getFirst().judgement());
        assertEquals(1, result.score().count300());
        assertEquals(0, result.score().misses());
        assertTrue(auto.finished());
        assertFalse(auto.primaryPressed());
    }

    @Test
    void completesMixedCircleSliderSpinnerMapOnSixteenMillisecondSteps() {
        ManualClock clock = new ManualClock();
        BeatmapDifficulty difficulty = difficulty(List.of(
                circle(120, 130, 1000),
                slider(100, 200, 2000, 200, 1),
                spinner(256, 192, 3000, 5000)));
        OsuGameplaySession session = session(difficulty, clock);
        DebugAutoPlayer auto = new DebugAutoPlayer(difficulty, clock, session);

        for (long now = 0; now <= 5024; now += 16) {
            clock.set(now);
            auto.update();
            session.update();
            auto.afterSessionUpdate();
        }

        GameplayState result = session.state();
        assertTrue(auto.finished());
        assertTrue(result.completed());
        assertEquals(5, result.score().count300());
        assertEquals(0, result.score().misses());
        assertEquals(1, result.score().accuracy(), 1e-6);
        assertTrue(result.score().score() > 0);
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects) {
        return new BeatmapDifficulty("Song", "Artist", "Creator", "Auto fixture", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 1),
                List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)), objects, null, null);
    }

    private HitObject circle(double x, double y, long time) {
        return new HitObject(x, y, time, HitObject.Type.CIRCLE, 1, 0);
    }

    private HitObject slider(double x, double y, long time, double endX, int repeats) {
        SliderData data = new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                List.of(new BeatmapPoint(x, y), new BeatmapPoint(endX, y)))), repeats, Math.abs(endX - x));
        return new HitObject(x, y, time, HitObject.Type.SLIDER, 2, 0, data);
    }

    private HitObject spinner(double x, double y, long start, long end) {
        return new HitObject(x, y, start, HitObject.Type.SPINNER, 8, 0, null, new SpinnerData(end));
    }

    private OsuGameplaySession session(BeatmapDifficulty difficulty, ManualClock clock) {
        return new OsuGameplaySession(difficulty, clock,
                JudgementWindows.fromOverallDifficulty(difficulty.settings().overallDifficulty()));
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
