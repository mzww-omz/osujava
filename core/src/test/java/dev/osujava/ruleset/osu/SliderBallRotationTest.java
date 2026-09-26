package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SliderBallRotationTest {
    private record Fixture(SliderPath path, SliderTiming timing) { }
    private Fixture fixture(List<BeatmapPoint> points, double length, int spans) {
        var object = new HitObject(points.getFirst().x(), points.getFirst().y(), 1000, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0, points)), spans - 1, length));
        var map = new BeatmapDifficulty("", "", "", "", 0, "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 1),
                List.of(), List.of(object), null, null);
        var path = new SliderPath(object.x(), object.y(), object.sliderData());
        return new Fixture(path, SliderTiming.calculate(map, object, path));
    }
    @Test void tangentFollowsPathAndReverseSpanFlipsDirection() {
        var f = fixture(List.of(new BeatmapPoint(0, 0), new BeatmapPoint(0, 280)), 280, 2);
        assertEquals(90, SliderBallRotation.at(f.path, f.timing, 1500, 0), 1e-6);
        assertEquals(-90, SliderBallRotation.at(f.path, f.timing, 2500, 0), 1e-6);
        assertEquals(-90, SliderBallRotation.at(f.path, f.timing, 3000, 0), 1e-6);
        assertEquals(90, SliderBallRotation.at(f.path, f.timing, 1999, 0), 1e-6);
        assertEquals(-90, SliderBallRotation.at(f.path, f.timing, 2000, 0), 1e-6);
    }
    @Test void tinyAndZeroPathsKeepLastFiniteAngleAtEnd() {
        for (double length : new double[]{0, 0.001, 0.02, 0.1}) {
            var f = fixture(List.of(new BeatmapPoint(0, 0), new BeatmapPoint(length, 0)), length, 1);
            assertTrue(Double.isFinite(SliderBallRotation.at(f.path, f.timing, f.timing.endTimeMs(), 23)));
            if (length < 0.01) assertEquals(23, SliderBallRotation.at(f.path, f.timing, f.timing.endTimeMs(), 23));
        }
    }
    @Test void atanBoundaryIsUnwrappedAndSnakingArrowPointsBackIntoVisibleCurve() {
        var f = fixture(List.of(new BeatmapPoint(280, 0), new BeatmapPoint(0, -1)), 280, 1);
        double angle = SliderBallRotation.at(f.path, f.timing, 1500, 179);
        assertTrue(angle > 180 && angle < 181);
        var straight = fixture(List.of(new BeatmapPoint(0, 0), new BeatmapPoint(280, 0)), 280, 2);
        assertEquals(180, ReverseArrowDirection.at(straight.path, 0.5, true));
        assertEquals(0, ReverseArrowDirection.at(straight.path, 0, false));
        assertTrue(Double.isFinite(ReverseArrowDirection.at(straight.path, 0, true)));
    }
}
