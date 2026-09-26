package dev.osujava.ruleset.osu.render;

import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.SliderData;
import dev.osujava.ruleset.osu.SliderPath;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SliderBodyGeometryTest {
    private SliderBodyGeometry geometry(double... xy) {
        var points = new java.util.ArrayList<BeatmapPoint>();
        for (int i = 0; i < xy.length; i += 2) points.add(new BeatmapPoint(xy[i], xy[i + 1]));
        return new SliderBodyGeometry(points);
    }

    @Test void straightSnakeZeroHalfAndFullIncludingPartialSegment() {
        var g = geometry(0,0, 30,0, 100,0);
        assertEquals(0, g.visibleSegments(0));
        assertEquals(Double.POSITIVE_INFINITY, g.distanceAt(0,0,0));
        assertEquals(2, g.visibleSegments(.5));
        assertEquals(50, g.segments().get(1).endAt(g.limit(.5)).x(), 1e-6);
        assertEquals(10, g.distanceAt(60,0,.5), 1e-6);
        assertEquals(0, g.distanceAt(60,0,1), 1e-6);
        assertEquals(100, g.limit(1), 1e-6);
        assertEquals(1, g.visibleSegments(.3), "An exact vertex stops before the next segment");
    }

    @Test void roundCapsShareTheSameDistanceFieldAsBody() {
        var g = geometry(0,0, 100,0);
        assertEquals(5, g.distanceAt(-3,4,1), 1e-6);
        assertEquals(5, g.distanceAt(103,4,1), 1e-6);
        assertEquals(4, g.distanceAt(50,4,1), 1e-6);
        assertEquals(5, g.distanceAt(53,4,.5), 1e-6);
        assertEquals(g.distanceAt(30,7,1), g.distanceAt(50,7,1), 1e-6);
    }

    @Test void rightAngleJoinIsRoundWithoutCracksOrMiterSpikes() {
        var g = geometry(0,0, 100,0, 100,100);
        assertEquals(5, g.distanceAt(103,-4,1), 1e-6);
        assertEquals(0, g.distanceAt(100,0,1), 1e-6);
        assertEquals(10, g.distanceAt(110,-10,1) / Math.sqrt(2), 1e-6);
        finite(g);
    }

    @Test void acuteBendAndBacktrackingResolveToNearestSegment() {
        var g = geometry(0,0, 100,0, 1,1, 100,0);
        assertEquals(5, g.distanceAt(103,-4,1), 1e-6);
        assertEquals(0, g.distanceAt(60,0,1), 1e-6);
        assertTrue(g.distanceAt(60,.5,1) <= .5);
        finite(g);
    }

    @Test void bezierSamplesRemainUnchangedAndFinite() {
        var controls = List.of(new BeatmapPoint(0,0), new BeatmapPoint(50,100), new BeatmapPoint(100,0));
        var data = new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.BEZIER,0,controls)),0,148);
        var path = new SliderPath(0,0,data);
        var g = new SliderBodyGeometry(path.sampledPoints());
        assertEquals(path.distance(), g.limit(1), 1e-5);
        for (var point : path.sampledPoints()) assertEquals(0,g.distanceAt(point.x(),point.y(),1),1e-5);
        finite(g);
    }

    @Test void veryShortAndRepeatedPointsDoNotDivideByZero() {
        var g = geometry(1,1, 1,1, 1.0001,1, 1.0001,1);
        assertEquals(1,g.visibleSegments(1));
        assertEquals(.0001,g.limit(1),1e-9);
        assertEquals(0,g.distanceAt(1.00005,1,.5),1e-8);
        finite(g);
        var point = geometry(4,4, 4,4);
        assertEquals(1,point.visibleSegments(1));
        assertEquals(5,point.distanceAt(7,8,1),1e-6);
        finite(point);
        assertEquals(0, new SliderBodyGeometry(List.of()).visibleSegments(1));
    }

    @Test void cachedVerticesContainCapsuleEndpointsAndAreNotMutableFromOutside() {
        var g = geometry(10,20, 90,30);
        var vertices = g.vertices();
        assertEquals(48, vertices.length);
        assertArrayEquals(new float[]{-1,-1,10,20,90,30,0,(float)Math.hypot(80,10)},
                java.util.Arrays.copyOf(vertices,8));
        vertices[2] = 999;
        assertEquals(10,g.vertices()[2]);
    }

    private void finite(SliderBodyGeometry g) {
        for (float value : g.vertices()) assertTrue(Float.isFinite(value));
        for (double p : new double[]{.0001,.5,1}) {
            for (var segment : g.segments()) {
                var end = segment.endAt(g.limit(p));
                assertTrue(Double.isFinite(end.x()) && Double.isFinite(end.y()));
            }
            assertTrue(Double.isFinite(g.distanceAt(50,20,p)));
        }
    }
}
