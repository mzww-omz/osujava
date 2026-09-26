package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapFile;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderBeatmapFixtureTest {
    @Test
    void parsesLegacySliderCurvesLengthsRepeatsAndInheritedTiming() throws IOException {
        BeatmapDifficulty difficulty = parseFixture();
        List<HitObject> sliders = difficulty.hitObjects().stream()
                .filter(object -> object.type() == HitObject.Type.SLIDER)
                .toList();

        assertEquals(9, sliders.size());
        assertEquals(List.of(
                        "LINEAR", "BEZIER", "PERFECT", "CATMULL", "LINEAR",
                        "LINEAR", "LINEAR", "LINEAR", "LINEAR"),
                sliders.stream().map(object -> object.sliderData().curveType().name()).toList());

        HitObject repeated = sliders.get(4);
        assertEquals(1, repeated.sliderData().repeatCount());
        SliderPath repeatedPath = path(repeated);
        SliderTiming repeatedTiming = timing(difficulty, repeated, repeatedPath);
        assertEquals(1, repeatedTiming.progressAt(repeatedTiming.startTimeMs() + repeatedTiming.spanDurationMs()), 1e-6);
        assertEquals(0.5, repeatedTiming.progressAt(repeatedTiming.startTimeMs() + repeatedTiming.spanDurationMs() * 1.5), 1e-6);

        HitObject shortSlider = sliders.get(5);
        assertEquals(10, path(shortSlider).distance(), 1e-6);
        assertTrue(timing(difficulty, shortSlider, path(shortSlider)).durationMs() < 36);

        HitObject longSlider = sliders.get(6);
        assertEquals(450, path(longSlider).distance(), 1e-6);
        assertTrue(timing(difficulty, longSlider, path(longSlider)).durationMs() > 1500);

        HitObject zeroLength = sliders.get(7);
        assertEquals(3, zeroLength.sliderData().repeatCount());
        assertEquals(0, path(zeroLength).distance(), 1e-6);
        assertEquals(1, timing(difficulty, zeroLength, path(zeroLength)).spanCount());

        HitObject inherited = sliders.getLast();
        assertEquals(19000, inherited.timeMs());
        assertEquals(178.571429, timing(difficulty, inherited, path(inherited)).durationMs(), 1e-5);
    }

    private BeatmapDifficulty parseFixture() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/beatmaps/slider-regression.osu")) {
            if (stream == null) throw new IOException("Slider fixture is missing");
            String contents = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            BeatmapFile parsed = new BeatmapFileParser().parse(contents, "slider-regression.osu");
            return parsed.difficulty();
        } catch (dev.osujava.beatmap.parse.BeatmapParseException e) {
            throw new IOException("Could not parse slider fixture", e);
        }
    }

    private SliderPath path(HitObject object) {
        return new SliderPath(object.x(), object.y(), object.sliderData());
    }

    private SliderTiming timing(BeatmapDifficulty difficulty, HitObject object, SliderPath path) {
        return SliderTiming.calculate(difficulty, object, path);
    }
}
