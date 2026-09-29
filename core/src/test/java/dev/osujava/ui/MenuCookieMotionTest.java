package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MenuCookieMotionTest {
    private static MenuAudioAnalysis audio(float peak, float[] bins) {
        return new MenuAudioAnalysis() {
            public float maximumAmplitude() { return peak; }
            public float[] frequencyAmplitudes() { return bins; }
            public boolean available() { return true; }
        };
    }
    private static MenuBeatTiming.Beat beat(double time) { return MenuBeatTiming.at(List.of(), time, true); }
    private static final MenuAudioAnalysis ZERO = audio(0, new float[1024]);

    @Test void firstSixtyHzUpdateUsesStableQuadraticContractionAndSeparateEcho() {
        var motion = new MenuCookieMotion();
        motion.advance(1000.0 / 60, beat(0), ZERO);
        assertEquals(1.028125, motion.scale(), .000001); // pulse=.25, quadratic-out=.4375
        assertEquals(1.063125, motion.echoScale(), .000001);
        assertEquals(1.066875, motion.spectrumScale(), .000001);
        assertEquals(.4 * (1 - .4375) * .52, motion.echoAlpha(), .000001);
    }
    @Test void beatContractionRecoversWhileIdleWithoutClicksOrMenuActions() {
        var model = new MainMenuModel();
        float nearBeat = 0, nearEnd = 0;
        for (int i = 0; i <= 119; i++) {
            model.advance(1000.0 / 60, beat(i * 1000.0 / 60), ZERO);
            if (i == 92) nearBeat = model.scale();
            if (i == 119) nearEnd = model.scale();
        }
        assertTrue(nearBeat < nearEnd);
        assertEquals(MainMenuState.CLOSED, model.state()); assertFalse(model.pending());
        assertEquals(0, model.reveal());
        assertEquals(2, model.cookie().ripples().size());
    }
    @Test void expandingRippleLivesOneSecondAndDoesNotBurstOnLoopOrTrackChange() {
        var motion = new MenuCookieMotion();
        motion.advance(0, beat(490), ZERO); motion.advance(10, beat(500), ZERO);
        var ripple = motion.ripples().getFirst();
        assertEquals(ripple.scale(), motion.rippleScale(ripple));
        motion.advance(500, beat(750), ZERO);
        assertEquals(ripple.scale() * 1.3, motion.rippleScale(ripple), .000001);
        assertEquals(ripple.alpha() * .5, motion.rippleAlpha(ripple), .000001);
        motion.advance(500, beat(750), ZERO); assertTrue(motion.ripples().isEmpty());
        motion.advance(10, beat(0), ZERO); assertTrue(motion.ripples().isEmpty());
        motion.resetTrack(); motion.advance(10, beat(5000), ZERO); assertTrue(motion.ripples().isEmpty());
    }
    @Test void hoverUsesLinearOffsetWithoutChangingMenuStateAndClickRecovers() {
        var plain = new MenuCookieMotion(); var hover = new MenuCookieMotion();
        hover.pointer(true);
        for (int i = 0; i < 8; i++) { plain.advance(1000.0 / 60, beat(0), ZERO); hover.advance(1000.0 / 60, beat(0), ZERO); }
        assertEquals(.096, hover.scale() - plain.scale(), .000001);
        hover.click(); assertEquals(.016, hover.scale() - plain.scale(), .000001);
        hover.pointer(false);
        for (int i = 0; i < 9; i++) { plain.advance(1000.0 / 60, beat(0), ZERO); hover.advance(1000.0 / 60, beat(0), ZERO); }
        assertEquals(plain.scale(), hover.scale(), .000001);
    }
    @Test void referenceRadiusAndCursorParallaxUseTheHeightScaled480Canvas() {
        for (int[] size : new int[][]{{1280,720},{1366,768},{1920,1080},{2560,1440}}) {
            var ui = UiLayout.fromPixels(size[0], size[1]); var m = MainMenuLayout.from(ui);
            assertEquals(size[1] * 150.0 / 480, m.radius() * ui.scale(), .001);
            var moved = m.parallax(m.width(), m.height());
            assertEquals(-size[0] / 120.0, (moved.cx() - m.cx()) * ui.scale(), .001);
            assertEquals(-size[1] / 120.0, (moved.cy() - m.cy()) * ui.scale(), .001);
            assertTrue(moved.logoHit(moved.cx(), moved.cy(), 1));
        }
    }
    @Test void missingAudioNeverInventsSpectrumButRetainsSixtyBpmMotion() {
        var silent = new DeterministicMenuAudioFallback(); var v = new MenuVisualiser();
        silent.sample(0, beat(0)); v.advance(100, silent, false);
        assertFalse(silent.available()); assertArrayEquals(new float[1024], v.amplitudes());
        var motion = new MenuCookieMotion();
        motion.advance(0, MenuBeatTiming.at(List.of(), 999, false), silent);
        motion.advance(1, MenuBeatTiming.at(List.of(), 1000, false), silent);
        assertEquals(1, motion.ripples().size());
    }
    @Test void spectrumHasFourTurnsReversedBinsTenMsOffsetsAndExponentialDecay() {
        float[] bins = new float[1024]; bins[100] = .2f;
        var v = new MenuVisualiser(); v.advance(10, audio(0, bins), false);
        assertEquals(.96 * Math.pow(.95, .6), v.amplitudes()[923], .000001);
        assertEquals(0, v.amplitudes()[973]);
        v.advance(1, audio(0, bins), false);
        assertEquals(.96 * Math.pow(.95, .06), v.amplitudes()[973], .000001);
        float value = v.amplitudes()[973]; v.advance(100, ZERO, false);
        assertEquals(value * Math.pow(.95, 6), v.amplitudes()[973], .000001);
        assertEquals(Math.PI * 2 * .4, MenuVisualiser.rotation(0), .000001);
        assertEquals(Math.PI * 2, MenuVisualiser.rotation(256) - MenuVisualiser.rotation(0), .000001);
        assertEquals(0, MenuVisualiser.opacity(.04f)); assertEquals(.2f, MenuVisualiser.opacity(.08f), .000001);
        assertEquals(.4f, MenuVisualiser.opacity(.12f), .000001);
    }
    @Test void silenceDecaysPeaksAndLongStallsDoNotInventSpectrumHistory() {
        var v = new MenuVisualiser(); v.advance(1, audio(1, new float[]{1,Float.NaN,Float.POSITIVE_INFINITY}), true);
        float[] previous = v.amplitudes(); v.advance(1001, ZERO, false); assertArrayEquals(previous, v.amplitudes());
        v.advance(0, audio(1, new float[]{1}), true); assertArrayEquals(previous, v.amplitudes());
        for (int i = 0; i < 5; i++) v.advance(1000, new DeterministicMenuAudioFallback(), false);
        assertArrayEquals(new float[1024], v.amplitudes());
        v.reset(); assertArrayEquals(new float[1024], v.amplitudes());
    }
    @Test void kiaiChangesOpacityNotFftLengths() {
        var a = new MenuVisualiser(); var b = new MenuVisualiser();
        var source = audio(.8f, new float[]{.4f}); a.advance(50, source, false); b.advance(50, source, true);
        assertArrayEquals(a.amplitudes(), b.amplitudes());
        var motion = new MenuCookieMotion();
        assertEquals(.7f, motion.spectrumAlpha(0), .00001);
        assertEquals(.21f, motion.spectrumAlpha(1), .00001);
        var normal = new MenuCookieMotion();
        normal.advance(1000.0 / 60, beat(0), ZERO);
        motion.advance(1000.0 / 60, new MenuBeatTiming.Beat(0,0,500,0,0,true), ZERO);
        assertEquals(normal.scale(), motion.scale());
        assertEquals(1, motion.spectrumAlpha(0)); assertTrue(motion.echoAdditive());
        assertFalse(normal.echoAdditive()); assertTrue(motion.echoAlpha() < normal.echoAlpha());
    }
}
