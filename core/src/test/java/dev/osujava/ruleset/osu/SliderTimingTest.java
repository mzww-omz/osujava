package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import dev.osujava.beatmap.TimingPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderTimingTest {
    @Test
    void calculatesDurationFromRedlineAndSliderMultiplier() {
        SliderTiming timing = timing(280, 1, List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)));

        assertEquals(1000, timing.spanDurationMs(), 1e-6);
        assertEquals(1000, timing.durationMs(), 1e-6);
        assertEquals(2000, timing.endTimeMs(), 1e-6);
    }

    @Test
    void inheritedVelocityChangesDurationAndRedlineResetsIt() {
        SliderTiming fast = timing(280, 1, List.of(
                new TimingPoint(0, 500, 4, 0, 0, 100, true, 0),
                new TimingPoint(1000, -50, 4, 0, 0, 100, false, 0)));
        SliderTiming reset = timing(280, 1, List.of(
                new TimingPoint(0, 500, 4, 0, 0, 100, true, 0),
                new TimingPoint(1000, -50, 4, 0, 0, 100, false, 0),
                new TimingPoint(1000, 500, 4, 0, 0, 100, true, 0)));

        assertEquals(2000, fast.spanDurationMs(), 1e-6);
        assertEquals(1000, reset.spanDurationMs(), 1e-6);
    }

    @Test
    void computesProgressAlongAlternatingSpansFromBeatmapTime() {
        SliderTiming timing = timing(280, 2, List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)));

        assertEquals(0, timing.progressAt(1000), 1e-6);
        assertEquals(0.5, timing.progressAt(1500), 1e-6);
        assertEquals(1, timing.progressAt(2000), 1e-6);
        assertEquals(0.5, timing.progressAt(2500), 1e-6);
        assertEquals(0, timing.progressAt(3000), 1e-6);
    }

    @Test
    void generatesReversedRepeatTicksAndTail() {
        SliderPath path = new SliderPath(0, 0, new SliderData(List.of(new SliderData.Segment(
                SliderData.CurveType.LINEAR, 0, List.of(new BeatmapPoint(0, 0), new BeatmapPoint(100, 0)))), 1, 100));
        SliderTiming timing = timing(100, 2, List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)));

        List<SliderEvent> events = SliderEventGenerator.generate(timing, path);
        SliderEvent repeat = events.stream().filter(event -> event.type() == SliderEvent.Type.REPEAT).findFirst().orElseThrow();
        SliderEvent tail = events.getLast();
        List<SliderEvent> reverseTicks = events.stream()
                .filter(event -> event.type() == SliderEvent.Type.TICK && event.spanIndex() == 1).toList();

        assertEquals(1, repeat.pathProgress());
        assertEquals(0, tail.pathProgress());
        assertTrue(reverseTicks.size() >= 1);
        assertTrue(reverseTicks.getFirst().timeMs() > repeat.timeMs());
        assertTrue(reverseTicks.getLast().timeMs() < tail.timeMs());
        assertEquals(timing.endTimeMs() - 36, SliderEventGenerator.tailJudgementStartTime(timing), 1e-6);
    }

    @Test
    void shortSliderTailLeniencyNeverBeginsBeforeHalfDuration() {
        SliderTiming shortSlider = timing(14, 1,
                List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)));
        assertEquals(50, shortSlider.durationMs(), 1e-6);
        assertEquals(1025, SliderEventGenerator.tailJudgementStartTime(shortSlider), 1e-6);
    }

    @Test
    void zeroDistancePathDoesNotCreateImpossibleRepeatSpans() {
        HitObject object = new HitObject(0, 0, 1000, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(0, 0), new BeatmapPoint(0, 0)))), 4, 0));
        BeatmapDifficulty difficulty = new BeatmapDifficulty("Song", "Artist", "Mapper", "Normal", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 1), List.of(), List.of(object), null, null);
        SliderPath path = new SliderPath(0, 0, object.sliderData());
        SliderTiming timing = SliderTiming.calculate(difficulty, object, path);

        assertEquals(0, path.distance(), 1e-6);
        assertEquals(1, timing.spanCount());
        assertEquals(1000, timing.endTimeMs(), 1e-6);
    }

    private SliderTiming timing(double pathLength, int slides, List<TimingPoint> timingPoints) {
        HitObject object = new HitObject(0, 0, 1000, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(0, 0), new BeatmapPoint(pathLength, 0)))), slides - 1, pathLength));
        BeatmapDifficulty difficulty = new BeatmapDifficulty("Song", "Artist", "Mapper", "Normal", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 2), timingPoints, List.of(object), null, null);
        return SliderTiming.calculate(difficulty, object, new SliderPath(0, 0, object.sliderData()));
    }
}
