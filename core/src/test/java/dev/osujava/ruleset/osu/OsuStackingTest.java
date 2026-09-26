package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OsuStackingTest {
    @Test
    void circlesStackBackwardsAndSliderTailPushesFollowingCirclesForward() {
        HitObject a = circle(100, 100, 1000);
        HitObject b = circle(100, 100, 1100);
        HitObject c = circle(100, 100, 1200);
        OsuStacking simple = new OsuStacking(difficulty(List.of(a, b, c), 14));
        assertEquals(2, simple.height(a));
        assertEquals(1, simple.height(b));
        assertEquals(0, simple.height(c));
        assertEquals(100 + 2 * (-3.2 * 1.00041), simple.position(a).x(), 1e-6);

        HitObject slider = slider(100, 100, 1000, 100);
        HitObject tailCircle = circle(200, 100, 1500);
        OsuStacking tail = new OsuStacking(difficulty(List.of(slider, tailCircle), 14));
        assertEquals(0, tail.height(slider));
        assertEquals(-1, tail.height(tailCircle));
        assertEquals(200 + 3.2 * 1.00041, tail.position(tailCircle).x(), 1e-6);
    }

    @Test
    void oldFormatsUseForwardStacking() {
        HitObject first = circle(100, 100, 1000);
        HitObject second = circle(100, 100, 1100);
        OsuStacking stacking = new OsuStacking(difficulty(List.of(first, second), 5));
        assertEquals(1, stacking.height(first));
        assertEquals(0, stacking.height(second));
    }

    private BeatmapDifficulty difficulty(List<HitObject> objects, int formatVersion) {
        return new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0, "", "",
                new DifficultySettings(5, 5, 5, 5, 1.4, 1, 0.7, formatVersion),
                List.of(), objects, null, null);
    }

    private HitObject circle(double x, double y, long time) {
        return new HitObject(x, y, time, HitObject.Type.CIRCLE, 1, 0);
    }

    private HitObject slider(double x, double y, long time, double length) {
        return new HitObject(x, y, time, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(x, y), new BeatmapPoint(x + length, y)))), 0, length));
    }
}
