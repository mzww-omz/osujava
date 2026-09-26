package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.SpinnerData;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameInputAction;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import dev.osujava.gameplay.ScoreState;
import dev.osujava.beatmap.SliderData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsuGameplaySessionTest {
    @Test
    void judgementVisualSurvivesLegacyFadeAndParticleLifetimeWithoutChangingScore() {
        ManualClock clock = new ManualClock();
        var session = new OsuGameplaySession(difficulty(List.of(object(256, 192, 1000, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(50, 100, 150));
        clock.set(1000); session.click(256, 192);
        var original = session.state().judgementVisuals().getFirst();
        for (long time : new long[]{1901, 2100, 2499, 2500}) {
            clock.set(time);
            assertEquals(original, session.update().judgementVisuals().getFirst());
            assertEquals(1, session.state().score().count300());
        }
        clock.set(2501); assertTrue(session.update().judgementVisuals().isEmpty());
    }

    @Test
    void hudReceivesEveryInputEventBeforeRenderingAndSnapshotsStayImmutable() {
        ManualClock clock = new ManualClock();
        var session = new OsuGameplaySession(difficulty(List.of(object(100, 100, 1000, 1),
                object(300, 100, 1000, 1), sliderObject(100, 192, 2000, 280, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(50, 100, 150));
        assertEquals(1000, session.state().songProgress().firstHitTime());
        assertEquals(3000, session.state().songProgress().lastHitTime());
        clock.set(1000);
        session.click(100, 100);
        var frozen = session.state();
        session.click(300, 100);
        assertEquals(2, session.state().score().combo());
        assertEquals(1, session.state().hud().combo());
        assertEquals(2, session.state().hud().popCombo());
        clock.set(1210);
        assertEquals(2, session.update().hud().combo());
        assertEquals(1.1, session.state().hud().comboScale(), 1e-12);
        assertEquals(300, frozen.score().score());
        assertEquals(0, frozen.hud().score());
        assertEquals(0, frozen.hud().combo());
    }

    @Test
    void followSnapshotDistinguishesReleaseFromMissAndSchedulesSuccessfulTailEnd() {
        ManualClock clock = new ManualClock();
        var session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(50, 100, 150));
        clock.set(1000);
        session.press(GameInputAction.LEFT, 100, 100);
        var pressed = session.state().sliders().getFirst();
        assertEquals(dev.osujava.gameplay.FollowCircleAnimation.Kind.PRESS, pressed.followEvents().getFirst().kind());
        clock.set(1200);
        session.release(GameInputAction.LEFT);
        var released = session.update().sliders().getFirst();
        assertEquals(dev.osujava.gameplay.FollowCircleAnimation.Kind.RELEASE, released.followEvents().getLast().kind());
        assertEquals(2, dev.osujava.gameplay.FollowCircleAnimation.at(released.followEvents(), 1300, released.endTimeMs()).scale());
        clock.set(1500);
        var missed = session.update().sliders().getFirst();
        assertEquals(dev.osujava.gameplay.FollowCircleAnimation.Kind.BREAK, missed.followEvents().getLast().kind());
        assertTrue(pressed.followEvents().size() < missed.followEvents().size(), "Older snapshots stay immutable");
        clock.set(1700);
        session.press(GameInputAction.LEFT, 296, 100);
        clock.set(1964);
        session.pointerMoved(380, 100);
        var tail = session.update().sliders().getFirst();
        assertEquals(dev.osujava.gameplay.FollowCircleAnimation.Kind.END, tail.followEvents().getLast().kind());
        assertEquals(2000, tail.followEvents().getLast().timeMs());
    }

    @Test
    void circleHitAndMissKeepTheirOwnPiecesUntilAnimationEnds() {
        ManualClock clock = new ManualClock();
        var session = new OsuGameplaySession(difficulty(List.of(object(256, 192, 1000, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(50, 100, 150));
        clock.set(1000);
        session.click(256, 192);
        assertEquals(Judgement.HIT300, session.state().circles().getFirst().judgement());
        clock.set(1120);
        assertEquals(1000, session.update().circles().getFirst().judgementTimeMs());
        clock.set(1241);
        assertTrue(session.update().circles().isEmpty());
        session = new OsuGameplaySession(difficulty(List.of(object(256, 192, 2000, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(50, 100, 150));
        clock.set(2200);
        assertEquals(Judgement.MISS, session.update().circles().getFirst().judgement());
        assertEquals(2150, session.state().circles().getFirst().judgementTimeMs());
        clock.set(2251);
        assertTrue(session.update().circles().isEmpty());
    }

    @Test
    void sliderSnapshotExposesTimingVelocityWithoutChangingMovementOrRepeat() {
        for (double inheritedBeatLength : new double[]{-1000, -100, -50}) {
            HitObject object = sliderObject(100, 192, 1000, 280, 2);
            BeatmapDifficulty map = new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0, "", "",
                    new DifficultySettings(5, 5, 5, 5, 1.4, 1),
                    List.of(new dev.osujava.beatmap.TimingPoint(0, 500, 4, 0, 0, 100, true, 0),
                            new dev.osujava.beatmap.TimingPoint(0, inheritedBeatLength, 4, 0, 0, 100, false, 0)),
                    List.of(object), null, null);
            SliderPath path = new SliderPath(object.x(), object.y(), object.sliderData());
            SliderTiming timing = SliderTiming.calculate(map, object, path);
            ManualClock clock = new ManualClock();
            OsuGameplaySession session = new OsuGameplaySession(map, clock,
                    new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
            for (double spansElapsed : new double[]{0.25, 0.75, 1, 1.25, 1.75}) {
                clock.set((long) (1000 + timing.spanDurationMs() * spansElapsed));
                var snapshot = session.update().sliders().getFirst();
                assertEquals(timing.velocity(), snapshot.velocity());
                assertEquals(timing.progressAt(clock.nowMs()), snapshot.progress());
                assertEquals(path.positionAt(timing.progressAt(clock.nowMs())), snapshot.ballPosition());
                assertEquals(timing.endTimeMs(), snapshot.endTimeMs());
            }
        }
    }

    @Test
    void earlyClickWithinMissWindowJudgesMissWithoutPlayingHitSound() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(object(256, 192, 1000, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        clock.set(599);
        session.click(256, 192);
        assertEquals(0, session.state().score().misses());
        session.pointerReleased();
        clock.set(800);
        session.click(256, 192);
        assertEquals(1, session.state().score().misses());
        assertTrue(session.state().completed());
        assertTrue(session.drainAudioCues().isEmpty());
    }

    @Test
    void sliderCanRecoverNestedTickAfterEarlyHeadMiss() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        clock.set(800);
        session.click(100, 100);
        assertFalse(session.state().sliders().getFirst().headHit());
        clock.set(1500);
        session.pointerMoved(240, 100);
        GameplayState recovered = session.update();
        assertTrue(recovered.sliders().getFirst().tracking());
        assertEquals(30, recovered.score().score());
        assertEquals(1, recovered.score().misses());
    }

    @Test
    void lateSliderHeadCatchesPassedTicksWithinExpandedFollowArea() {
        ManualClock clock = new ManualClock();
        HitObject slider = sliderObject(100, 100, 1000, 280, 1);
        BeatmapDifficulty denseTicks = new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 10),
                List.of(new dev.osujava.beatmap.TimingPoint(0, 500, 4, 0, 0, 100, true, 0)),
                List.of(slider), null, null);
        OsuGameplaySession session = new OsuGameplaySession(denseTicks, clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        clock.set(1140);
        session.click(100, 100);
        GameplayState state = session.state();
        assertTrue(state.sliders().getFirst().tracking());
        assertTrue(state.score().score() > 50, "Passed ticks inside the expanded follow area are recovered");
        assertEquals(1, state.score().count50());
        assertEquals(1.0 / 6, state.score().accuracy(), 1e-6);
    }

    @Test
    void comboColourAdvancesOnNewComboAndAfterSpinnerNotEveryNumber() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(
                object(100, 100, 1000, 1), object(150, 100, 1100, 1),
                spinnerObject(1150, 1200), object(200, 100, 1300, 1),
                object(250, 100, 1400, 1 | 4 | (2 << 4)))),
                clock, new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        clock.set(300);
        GameplayState state = session.update();
        assertEquals(List.of(1, 2, 1, 1), state.circles().stream().map(c -> c.comboNumber()).toList());
        assertEquals(List.of(0, 0, 1, 4), state.circles().stream().map(c -> c.comboColorIndex()).toList());
    }

    @Test
    void sliderCannotBeHeldByAnActionPressedBeforeTheHeadInsteadOfTheHeadAction() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        clock.set(800);
        session.press(GameInputAction.LEFT, 0, 0);
        clock.set(1000);
        session.press(GameInputAction.RIGHT, 100, 100);
        assertTrue(session.state().sliders().getFirst().headHit());
        session.release(GameInputAction.RIGHT);
        clock.set(1100);
        session.pointerMoved(128, 100);
        assertFalse(session.update().sliders().getFirst().tracking());

        session.release(GameInputAction.LEFT);
        session.press(GameInputAction.LEFT, 128, 100);
        assertTrue(session.update().sliders().getFirst().tracking(), "Tracking can reacquire after the old hold is released");
    }

    @Test
    void startTimeOrderBlocksEarlyLaterObjectThenMissesEarlierObject() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(
                object(100, 100, 1000, 1), object(300, 100, 1010, 1))),
                clock, new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        clock.set(990);
        session.press(GameInputAction.LEFT, 300, 100);
        assertEquals(0, session.state().score().count300());
        session.release(GameInputAction.LEFT);
        clock.set(1010);
        session.press(GameInputAction.LEFT, 300, 100);
        assertEquals(1, session.state().score().count300());
        assertEquals(1, session.state().score().misses());
        assertEquals(JudgementVisual.Kind.CIRCLE, session.state().judgementVisuals().getLast().kind());
    }
    @Test
    void tracksHeldSpinnerRotationAndJudgesItAtItsEndTime() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(spinnerObject(1000, 4000)), 0), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        rotateClockwise(session, clock, 16, 1000);
        clock.set(2500);
        GameplayState progress = session.update();
        assertEquals(1, progress.spinners().getFirst().progress(), 1e-6);
        assertFalse(progress.completed(), "The spinner remains active until its end time");

        clock.set(4000);
        GameplayState completed = session.update();
        assertTrue(completed.completed());
        assertEquals(Judgement.HIT300, completed.spinners().getFirst().judgement());
        assertEquals(1, completed.score().count300());
        assertEquals(340, completed.score().score(), "Four spin ticks add 40 score without accuracy weight");
        assertEquals(1, completed.score().accuracy(), 1e-6);
    }

    @Test
    void givesPartialJudgementAboveThreeQuarterProgressAndMissAtThreeQuarterProgress() {
        ManualClock partialClock = new ManualClock();
        OsuGameplaySession partial = new OsuGameplaySession(difficulty(List.of(spinnerObject(1000, 4000)), 0), partialClock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        partialClock.set(1000);
        rotateClockwise(partial, partialClock, 14, 1000);
        partialClock.set(4000);
        GameplayState partialResult = partial.update();
        assertEquals(Judgement.HIT50, partialResult.spinners().getFirst().judgement());
        assertEquals(1, partialResult.score().count50());
        assertEquals(0, partialResult.score().misses());
        assertEquals(1.0 / 6, partialResult.score().accuracy(), 1e-6);
        assertEquals(80, partialResult.score().score());

        ManualClock missClock = new ManualClock();
        OsuGameplaySession miss = new OsuGameplaySession(difficulty(List.of(spinnerObject(1000, 4000)), 0), missClock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));
        missClock.set(1000);
        rotateClockwise(miss, missClock, 12, 1000);
        missClock.set(4000);
        GameplayState missed = miss.update();
        assertEquals(Judgement.MISS, missed.spinners().getFirst().judgement());
        assertEquals(1, missed.score().misses());
        assertEquals(0, missed.score().accuracy());
        assertEquals(30, missed.score().score(), "Only the three completed small spin ticks score");
    }

    @Test
    void spinnerTickBonusScoreDoesNotChangeAccuracyAndNextObjectStillWorksAfterCleanup() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(
                spinnerObject(1000, 4000), object(256, 192, 4500, 1)), 0), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        rotateClockwise(session, clock, 16, 1000);
        clock.set(4000);
        session.update();
        clock.set(4321);
        GameplayState cleaned = session.update();
        assertTrue(cleaned.spinners().isEmpty());
        assertFalse(cleaned.completed());

        clock.set(4500);
        session.click(256, 192);
        assertTrue(session.state().completed());
        assertEquals(2, session.state().score().count300());
        assertEquals(1, session.state().score().accuracy(), 1e-6);
    }

    @Test
    void awardsLargeBonusScoreOnlyAfterRequiredSpinsAndBonusGap() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(spinnerObject(1000, 4000)), 0), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        rotateClockwise(session, clock, 28, 1000);
        clock.set(4000);
        GameplayState result = session.update();

        assertEquals(1, result.score().count300());
        assertEquals(7, result.spinners().getFirst().completedSpins());
        assertEquals(410, result.score().score(), "Six small ticks and one large bonus tick are awarded");
        assertEquals(1, result.score().accuracy(), 1e-6);
    }

    @Test
    void spinnerRotationRequiresOneOfTheExistingHitInputsToBeHeld() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(spinnerObject(1000, 4000)), 0), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        moveAround(session, clock, 16, 1000);
        clock.set(4000);
        GameplayState state = session.update();
        assertEquals(Judgement.MISS, state.spinners().getFirst().judgement());
        assertEquals(0, state.score().score());
    }

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
        assertEquals(2, visible.sliders().getFirst().ticks().size());
        assertFalse(visible.sliders().getFirst().headJudged());

        session.click(100, 100);
        assertTrue(session.state().sliders().getFirst().headHit());
        assertEquals(1, session.state().score().count300());

        clock.set(1500);
        session.pointerMoved(240, 100);
        assertEquals(330, session.update().score().score());
        clock.set(2000);
        session.pointerMoved(380, 100);
        assertEquals(360, session.update().score().score());
        clock.set(2500);
        session.pointerMoved(240, 100);
        assertEquals(390, session.update().score().score());
        clock.set(2964);
        session.pointerMoved(110.08, 100);
        GameplayState complete = session.update();

        assertTrue(complete.completed());
        assertEquals(540, complete.score().score());
        assertEquals(1, complete.score().accuracy(), 1e-6);
        assertEquals(1, complete.score().count300());
        assertEquals(5, complete.score().combo());
    }

    @Test
    void sliderTrackingLossBreaksComboWithoutChangingCircleAccuracy() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(sliderObject(100, 100, 1000, 280, 2))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        session.click(100, 100);
        session.pointerMoved(20, 20);
        clock.set(1500);
        GameplayState state = session.update();

        assertEquals(1, state.score().count300());
        assertEquals(0, state.score().misses());
        assertEquals(0, state.score().combo());
        assertEquals(1, state.score().accuracy(), 1e-6);
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
        assertEquals(0, state.score().misses());
        assertEquals(0, state.score().combo());
        assertEquals(1, state.score().accuracy(), 1e-6);
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

        assertEquals(300 + 30 * ticksDueAtLeniencyStart, atLeniencyStart.score().score(),
                "The tail must wait while an earlier tick is still pending");

        clock.set((long) Math.ceil(timing.endTimeMs() - 17));
        progress = timing.progressAt(clock.nowMs());
        BeatmapPoint ball = path.positionAt(progress);
        session.pointerMoved(ball.x(), ball.y());
        GameplayState afterLastTick = session.update();

        assertEquals(300 + 30 * events.stream().filter(event -> event.type() == SliderEvent.Type.TICK).count() + 150,
                afterLastTick.score().score());
        assertTrue(afterLastTick.completed());
    }

    @Test
    void processesConsecutiveSlidersAndRemovesThemAfterTheirEndWindow() {
        ManualClock clock = new ManualClock();
        OsuGameplaySession session = new OsuGameplaySession(difficulty(List.of(
                sliderObject(100, 100, 1000, 100, 1),
                sliderObject(300, 100, 1600, 100, 1))), clock,
                new dev.osujava.gameplay.JudgementWindows(49.5, 99.5, 149.5));

        clock.set(1000);
        session.click(100, 100);
        clock.set(1358);
        session.pointerMoved(200, 100);
        session.update();

        clock.set(1598);
        GameplayState betweenSliders = session.update();
        assertTrue(betweenSliders.sliders().stream().noneMatch(slider -> slider.startTimeMs() == 1000));
        assertTrue(betweenSliders.sliders().stream().anyMatch(slider -> slider.startTimeMs() == 1600));
        assertFalse(betweenSliders.completed());

        clock.set(1600);
        session.click(300, 100);
        clock.set(1958);
        session.pointerMoved(400, 100);
        GameplayState secondFinished = session.update();
        assertTrue(secondFinished.completed());
        assertEquals(1, secondFinished.score().accuracy(), 1e-6);

        clock.set(2198);
        GameplayState cleanedUp = session.update();
        assertTrue(cleanedUp.sliders().isEmpty());
        assertTrue(cleanedUp.completed());
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects) {
        return difficulty(objects, 5);
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects, double overallDifficulty) {
        return new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0, "", "",
                new DifficultySettings(5, 5, overallDifficulty, 5, 1.4, 1), List.of(), objects, null, null);
    }

    private HitObject object(double x, double y, long time, int type) {
        return new HitObject(x, y, time, HitObject.typeFromBits(type), type, 0);
    }

    private HitObject sliderObject(double x, double y, long time, double length, int slides) {
        return new HitObject(x, y, time, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(x, y), new BeatmapPoint(x + length, y)))), slides - 1, length));
    }

    private HitObject spinnerObject(long startTime, double endTime) {
        return new HitObject(256, 192, startTime, HitObject.Type.SPINNER, 8, 0, null, new SpinnerData(endTime));
    }

    private void rotateClockwise(OsuGameplaySession session, ManualClock clock, int quarterTurns, long startTime) {
        session.click(336, 192);
        for (int step = 1; step <= quarterTurns; step++) {
            clock.set(startTime + step * 100);
            double angle = Math.toRadians(step * 90.0);
            session.pointerMoved(256 + Math.cos(angle) * 80, 192 + Math.sin(angle) * 80);
        }
    }

    private void moveAround(OsuGameplaySession session, ManualClock clock, int quarterTurns, long startTime) {
        session.pointerMoved(336, 192);
        for (int step = 1; step <= quarterTurns; step++) {
            clock.set(startTime + step * 100);
            double angle = Math.toRadians(step * 90.0);
            session.pointerMoved(256 + Math.cos(angle) * 80, 192 + Math.sin(angle) * 80);
        }
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
