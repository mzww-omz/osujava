package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectScrollTest {
    private SongSelectScroll scroll() {
        var s = new SongSelectScroll(); s.range(10000); return s;
    }
    @Test void oneWheelNotchMatchesReferenceVelocityIntegralAndPrediction() {
        var s = scroll(); s.wheel(1);
        assertEquals(.4, s.velocity(), 1e-12);
        assertEquals(.994, s.decay());
        assertEquals(66.46646606437685, s.remaining(), 1e-10);
        s.advance(100);
        assertEquals(.21912828061292286, s.velocity(), 1e-12);
        assertEquals(30.054759996616635, s.position(), 1e-10);
        assertEquals(36.411706067760214, s.remaining(), 1e-10);
        assertEquals(66.46646606437685, s.destination(), 1e-10);
    }
    @Test void repeatedNotchesAccelerateAndReverseInputFirstBrakes() {
        var s = scroll(); s.wheel(2);
        assertEquals(.88, s.velocity(), 1e-12);
        s.wheel(-1); assertEquals(.304, s.velocity(), 1e-12);
        s.wheel(-1); assertEquals(-.1568, s.velocity(), 1e-12);
        s.wheel(-1); assertEquals(-.58816, s.velocity(), 1e-12);
    }
    @Test void highSpeedUsesCappedAccelerationNotCappedVelocity() {
        var s = scroll(); s.wheel(100);
        double before = s.velocity();
        assertTrue(before > 10);
        s.wheel(1); assertEquals(before + 2.4, s.velocity(), 1e-10);
        s.wheel(-1); assertEquals(before, s.velocity(), 1e-10);
    }
    @Test void seekUsesPointNineNineAndTrackUsesPointNineNineTwo() {
        var s = scroll(); s.seek(100, SongSelectScroll.SELECT_DECAY);
        assertEquals(1.005033585350145, s.velocity(), 1e-12);
        s.advance(100); assertEquals(63.396765872677086, s.position(), 1e-10);
        s.seek(200, SongSelectScroll.TRACK_DECAY);
        assertEquals(.992, s.decay()); assertEquals(200, s.destination(), 1e-10);
        s.seek(-100, SongSelectScroll.SELECT_DECAY);
        assertEquals(0, s.destination(), 1e-10); assertTrue(s.velocity() < 0);
    }
    @Test void stopThresholdIsAppliedAfterIntegratingTheLastFrame() {
        var s = scroll(); s.wheel(1); s.advance(700);
        assertEquals(65.48229256515754, s.position(), 1e-10);
        assertEquals(0, s.velocity()); assertEquals(SongSelectScroll.IDLE_DECAY, s.decay());
        s.advance(1000); assertEquals(65.48229256515754, s.position(), 1e-10);
        assertEquals(s.position(), s.destination());
    }
    @Test void rangeClampsPositionButDoesNotInventBoundaryBraking() {
        var s = scroll(); s.range(10); s.wheel(1); s.advance(100);
        assertEquals(10, s.position()); assertTrue(s.velocity() > 0);
        assertTrue(s.remaining() > 10); assertEquals(10, s.destination());
        s.range(5); assertEquals(5, s.position()); assertTrue(s.velocity() > 0);
        s.range(0); assertEquals(0, s.position()); assertEquals(0, s.velocity());
        s.range(100); s.advance(100); assertEquals(0, s.position());
    }
    @Test void elapsedTimeComposesBeforeTheFrameBasedStopThreshold() {
        var reference = scroll(); reference.wheel(1); reference.advance(400);
        for (int fps : new int[]{30, 60, 120}) {
            var s = scroll(); s.wheel(1);
            for (int i = 0; i < fps; i++) s.advance(400.0 / fps);
            assertEquals(reference.position(), s.position(), 1e-10);
            assertEquals(reference.velocity(), s.velocity(), 1e-10);
        }
    }
    @Test void zeroInvalidAndTinyElapsedTimesDoNotInjectFrameSizedTravel() {
        var s = scroll(); s.wheel(1);
        for (double t : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) s.advance(t);
        assertEquals(0, s.position()); assertEquals(.4, s.velocity());
        s.advance(1e-10); assertEquals(4e-11, s.position(), 1e-20);
        s.jump(50); assertEquals(50, s.position()); assertEquals(0, s.velocity());
    }
    @Test void fractionalAndBatchedInputAreFiniteAndBatchedNotchesMatchCallbacks() {
        var a = scroll(); var b = scroll(); a.wheel(4.25f);
        for (int i = 0; i < 4; i++) b.wheel(1);
        b.wheel(.25f); assertEquals(a.velocity(), b.velocity());
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) a.wheel(bad);
        assertEquals(b.velocity(), a.velocity());
        a.wheel(Float.MAX_VALUE); assertTrue(Double.isFinite(a.velocity()));
        a.wheel(-Float.MAX_VALUE); assertTrue(Double.isFinite(a.velocity()));
    }
}
