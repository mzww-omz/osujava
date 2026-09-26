package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import dev.osujava.beatmap.TimingPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderTimingTest {
    @Test
    void calculatesDurationFromRedlineAndSliderMultiplier() {
        SliderTiming timing = timing(280, 1, List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)));

        assertEquals(0.28, timing.velocity(), 1e-6);
        assertEquals(1000, timing.spanDurationMs(), 1e-6);
        assertEquals(1000, timing.durationMs(), 1e-6);
        assertEquals(2000, timing.endTimeMs(), 1e-6);
    }

    @Test
    void inheritedVelocityChangesDurationAndRedlineResetsIt() {
        SliderTiming normal = timing(280, 1, List.of(
                new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)));
        SliderTiming fast = timing(280, 1, List.of(
                new TimingPoint(0, 500, 4, 0, 0, 100, true, 0),
                new TimingPoint(500, -50, 4, 0, 0, 100, false, 0)));
        SliderTiming slow = timing(280, 1, List.of(
                new TimingPoint(0, 500, 4, 0, 0, 100, true, 0),
                new TimingPoint(500, -200, 4, 0, 0, 100, false, 0)));
        SliderTiming reset = timing(280, 1, List.of(
                new TimingPoint(0, 500, 4, 0, 0, 100, true, 0),
                new TimingPoint(500, -50, 4, 0, 0, 100, false, 0),
                new TimingPoint(1000, 500, 4, 0, 0, 100, true, 0)));

        assertEquals(0.28, normal.velocity(), 1e-6);
        assertEquals(0.56, fast.velocity(), 1e-6);
        assertEquals(0.14, slow.velocity(), 1e-6);
        assertEquals(1000, normal.spanDurationMs(), 1e-6);
        assertEquals(500, fast.spanDurationMs(), 1e-6);
        assertEquals(2000, slow.spanDurationMs(), 1e-6);
        assertTrue(fast.durationMs() < normal.durationMs());
        assertTrue(normal.durationMs() < slow.durationMs());
        assertEquals(2000, normal.endTimeMs(), 1e-6);
        assertEquals(1500, fast.endTimeMs(), 1e-6);
        assertEquals(3000, slow.endTimeMs(), 1e-6);
        assertEquals(0.28, reset.velocity(), 1e-6);
        assertEquals(1000, reset.spanDurationMs(), 1e-6);
        assertEquals(2000, reset.endTimeMs(), 1e-6);
    }

    @Test
    void inheritedVelocitiesDriveProgressAndTickSpacing() {
        List<TimingPoint> redline = List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0));
        SliderTiming normal = timing(280, 1, redline);
        SliderTiming fast = timing(280, 1, List.of(
                redline.getFirst(), new TimingPoint(500, -50, 4, 0, 0, 100, false, 0)));
        SliderTiming slow = timing(280, 1, List.of(
                redline.getFirst(), new TimingPoint(500, -200, 4, 0, 0, 100, false, 0)));

        assertEquals(0.5, normal.progressAt(1500), 1e-6);
        assertEquals(0.5, fast.progressAt(1250), 1e-6);
        assertEquals(0.5, slow.progressAt(2000), 1e-6);
        assertEquals(1, fast.progressAt(1500), 1e-6);

        assertEquals(70, normal.tickDistance(), 1e-6);
        assertEquals(140, fast.tickDistance(), 1e-6);
        assertEquals(35, slow.tickDistance(), 1e-6);
        assertTickTimes(normal, 3);
        assertTickTimes(fast, 1);
        assertTickTimes(slow, 7);
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
                sliderData(pathLength, slides));
        BeatmapDifficulty difficulty = new BeatmapDifficulty("Song", "Artist", "Mapper", "Normal", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 2), timingPoints, List.of(object), null, null);
        return SliderTiming.calculate(difficulty, object, new SliderPath(0, 0, object.sliderData()));
    }

    private void assertTickTimes(SliderTiming timing, int expectedCount) {
        List<SliderEvent> ticks = SliderEventGenerator.generate(timing, new SliderPath(0, 0,
                        sliderData(280, 1))).stream()
                .filter(event -> event.type() == SliderEvent.Type.TICK)
                .toList();

        assertEquals(expectedCount, ticks.size());
        IntStream.range(0, ticks.size()).forEach(index ->
                assertEquals(1250 + index * 250, ticks.get(index).timeMs(), 1e-6));
    }

    private SliderData sliderData(double pathLength, int slides) {
        return new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                List.of(new BeatmapPoint(0, 0), new BeatmapPoint(pathLength, 0)))), slides - 1, pathLength);
    }
}
