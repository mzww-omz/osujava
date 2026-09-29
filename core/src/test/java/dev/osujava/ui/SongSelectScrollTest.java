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
    @Test void dragSamplesAccelerationDecelerationAndReversalInReferenceUnitsPerMillisecond() {
        var s = scroll(); s.jump(100); s.beginDrag(); s.drag(20, 20);
        assertEquals(120, s.position());
        assertEquals(.8784233454094307, s.velocity(), 1e-12);
        assertEquals(.9937231853739372, s.decay(), 1e-12);
        s.drag(4, 20); assertEquals(.44320521876258856, s.velocity(), 1e-12);
        s.drag(-20, 20); assertEquals(-.8245399376151937, s.velocity(), 1e-12);
        assertEquals(104, s.position());
    }
    @Test void holdingSuppressesFreeFlightAndRetainsPreviousVelocityUntilRelease() {
        var s = scroll(); s.jump(100); s.wheel(1); s.beginDrag();
        s.drag(0, 50); s.advance(50);
        assertEquals(100, s.position()); assertEquals(.4, s.velocity());
        s.releaseDrag(); s.advance(100);
        assertEquals(130.05475999661664, s.position(), 1e-10);
    }
    @Test void pausedTimeParticipatesInTheNextNonzeroSample() {
        var s = scroll(); s.beginDrag(); s.drag(20, 20);
        s.drag(0, 80); s.drag(2, 20);
        assertEquals(22, s.position()); assertEquals(.025082320499913397, s.velocity(), 1e-12);
        s.releaseDrag(); assertEquals(.025082320499913397, s.velocity(), 1e-12);
    }
    @Test void releaseHasSixtySixMillisecondGraceAndThenAttenuatesVelocity() {
        for (double pause : new double[]{0, 65, 66, 100}) {
            var s = scroll(); s.beginDrag(); s.drag(20, 20); s.drag(0, pause);
            s.releaseDrag();
            assertEquals(pause <= 66 ? .8784233454094307 : .15357002292559258, s.velocity(), 1e-12);
            double before = s.velocity(); s.releaseDrag(); assertEquals(before, s.velocity());
            assertFalse(s.dragging());
        }
    }
    @Test void verySlowDragUsesMinimumDecayAndZeroVelocityNeverDividesByZero() {
        var s = scroll(); s.beginDrag(); s.drag(.0001, 20);
        assertEquals(.5, s.decay());
        s.releaseDrag(); s.advance(20); assertEquals(0, s.velocity());
        s.beginDrag(); s.drag(0, 100); s.releaseDrag();
        assertEquals(0, s.velocity()); assertTrue(Double.isFinite(s.remaining()));
    }
    @Test void dragClampsAtBoundsWithoutDeletingTheSampledSpeed() {
        var s = scroll(); s.range(10); s.beginDrag(); s.drag(20, 20);
        assertEquals(10, s.position()); assertEquals(.8784233454094307, s.velocity(), 1e-12);
        s.drag(-20, 20); assertEquals(0, s.position()); assertTrue(s.velocity() < 0);
        s.cancelDrag(); assertFalse(s.dragging()); assertEquals(0, s.velocity());
        s.drag(5, 20); assertEquals(0, s.position());
        s.wheel(1); s.advance(20); assertTrue(s.position() > 0);
    }
    @Test void zeroTimeGeometryDoesNotManufactureSpeedAndInvalidDistancesAreIgnored() {
        var s = scroll(); s.beginDrag(); s.drag(20, 0);
        assertEquals(20, s.position()); assertEquals(0, s.velocity());
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            s.drag(bad, 20);
        assertEquals(20, s.position()); assertEquals(0, s.velocity());
        s.releaseDrag(); s.advance(100); assertEquals(20, s.position());
    }
    @Test void constantSpeedDragAndReleaseComposeAcrossSamplingRatesBeforeStop() {
        var reference = scroll(); reference.beginDrag(); reference.drag(100, 100);
        reference.releaseDrag(); reference.advance(50);
        for (int samples : new int[]{3, 6, 12}) {
            var s = scroll(); s.beginDrag();
            for (int i = 0; i < samples; i++) { s.drag(100.0 / samples, 100.0 / samples); s.advance(100.0 / samples); }
            s.releaseDrag(); s.advance(50);
            assertEquals(reference.position(), s.position(), 1e-10);
            assertEquals(reference.velocity(), s.velocity(), 1e-12);
        }
    }
}
