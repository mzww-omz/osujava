package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.SliderData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderPathTest {
    @Test
    void interpolatesLinearPathByDistanceAndFitsExpectedLength() {
        SliderPath path = path(SliderData.CurveType.LINEAR, 0, 0,
                List.of(new BeatmapPoint(0, 0), new BeatmapPoint(100, 0)), 150);

        assertEquals(150, path.distance(), 1e-6);
        assertEquals(75, path.positionAt(0.5).x(), 1e-6);
        assertEquals(0, path.positionAt(0.5).y(), 1e-6);
        assertEquals(150, path.positionAt(1).x(), 1e-6);
    }

    @Test
    void samplesBezierCurveByArcLength() {
        SliderPath path = path(SliderData.CurveType.BEZIER, 0, 0,
                List.of(new BeatmapPoint(0, 0), new BeatmapPoint(50, 100), new BeatmapPoint(100, 0)), 148);

        BeatmapPoint middle = path.positionAt(0.5);
        assertTrue(path.sampledPoints().size() > 3);
        assertEquals(148, path.distance(), 1e-5);
        assertTrue(middle.y() > 45, "The midpoint of the curved path should be above the direct line");
        assertEquals(50, middle.x(), 1.5);
    }

    @Test
    void trimsCurvedPathToBeatmapPixelLength() {
        SliderPath path = path(SliderData.CurveType.BEZIER, 0, 0,
                List.of(new BeatmapPoint(0, 0), new BeatmapPoint(50, 100), new BeatmapPoint(100, 0)), 120);

        assertEquals(120, path.distance(), 1e-5);
        assertTrue(path.positionAt(1).x() < 100);
    }

    @Test
    void followsPerfectCurveThroughItsMiddleControlPoint() {
        SliderPath path = path(SliderData.CurveType.PERFECT, 0, 0,
                List.of(new BeatmapPoint(0, 0), new BeatmapPoint(50, 50), new BeatmapPoint(100, 0)), 100);

        assertTrue(path.positionAt(0.5).y() > 20);
        assertEquals(100, path.distance(), 1e-5);
    }

    private SliderPath path(SliderData.CurveType type, double startX, double startY,
                            List<BeatmapPoint> points, double pixelLength) {
        return new SliderPath(startX, startY,
                new SliderData(List.of(new SliderData.Segment(type, 0, points)), 0, pixelLength));
    }
}
