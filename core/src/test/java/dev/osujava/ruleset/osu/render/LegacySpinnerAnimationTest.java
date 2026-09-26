package dev.osujava.ruleset.osu.render;

import dev.osujava.gameplay.SpinnerVisual;
import dev.osujava.gameplay.SpinnerVisual.SpinEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacySpinnerAnimationTest {
    private SpinnerVisual spinner(long completion, List<SpinEvent> events) {
        return new SpinnerVisual(256, 192, 150, .5, 120, 1440, 4, 8, 1000, 5000,
                true, null, 600, 123.999, completion, 140, 7, events);
    }
    @Test void styleUsesOnlyRootsAndBackgroundWins() {
        assertEquals(Style.OLD, select(true, false)); assertEquals(Style.OLD, select(true, true));
        assertEquals(Style.NEW, select(false, true)); assertEquals(Style.FALLBACK, select(false, false));
    }
    @Test void wholeLifetimeIsLateFadeInThenDrawable240MsFade() {
        var s = spinner(Long.MIN_VALUE, List.of());
        assertEquals(0, wholeAlpha(s, 400)); assertEquals(0, wholeAlpha(s, 600));
        assertEquals(.5, wholeAlpha(s, 800)); assertEquals(1, wholeAlpha(s, 1000));
        assertEquals(1, wholeAlpha(s, 5000)); assertEquals(.5, wholeAlpha(s, 5120)); assertEquals(0, wholeAlpha(s, 5240));
    }
    @Test void oldApproachShrinksLinearlyDuringSpinnerRatherThanPreempt() {
        var s = spinner(Long.MIN_VALUE, List.of());
        assertEquals(.625 * 1.86, approachScale(s, 400), 1e-7);
        assertEquals(.625 * 1.86, approachScale(s, 1000), 1e-7);
        assertEquals(.625 * .98, approachScale(s, 3000), 1e-7);
        assertEquals(.0625, approachScale(s, 5000), 1e-7);
    }
    @Test void metreTenBarsNoBlinkIsCompletelyDeterministic() {
        for (long t = 0; t < 1000; t++) {
            assertEquals(0, metreBars(0, true, t, 7)); assertEquals(5, metreBars(.5, true, t, 7));
            assertEquals(10, metreBars(1, true, t, 7)); assertEquals(2, metreBars(.25, true, t, 7));
        }
    }
    @Test void blinkKeepsTruncatedPercentBernoulliDistributionAndSeekRepeatability() {
        int high = 0;
        for (long t = 0; t < 10000; t++) {
            int bars = metreBars(.257, false, t, 7);
            assertTrue(bars == 2 || bars == 3); assertEquals(bars, metreBars(.257, false, t, 7));
            if (bars == 3) high++;
            assertEquals(0, metreBars(0, false, t, 7)); assertEquals(5, metreBars(.5, false, t, 7));
            assertTrue(metreBars(1, false, t, 7) >= 9 && metreBars(1, false, t, 7) <= 10);
        }
        assertEquals(.5, high / 10000.0, .025);
    }
    @Test void rotationsScaleAndGlowFollowLazerRatios() {
        assertEquals(60, topRotation(120, true)); assertEquals(120, topRotation(120, false));
        assertEquals(20, bottomRotation(120, true)); assertEquals(40, bottomRotation(120, false));
        assertEquals(120, middle2Rotation(120)); assertEquals(0, fixedRotation());
        assertEquals(.5, progressScale(0)); assertEquals(.625, progressScale(1));
        assertEquals(.59375, progressScale(.5)); assertEquals(.5, glowAlpha(.5));
        assertEquals(1, glowAlpha(2));
    }
    @Test void fixedMiddleInterpolatesInLinearRgb() {
        var s = spinner(Long.MIN_VALUE, List.of());
        assertEquals(1, fixedColour(s, 999).g()); assertEquals(1, fixedColour(s, 1000).g());
        assertEquals(.73535698, fixedColour(s, 3000).g(), 1e-6);
        assertEquals(1, fixedColour(s, 5000).r()); assertEquals(0, fixedColour(s, 5000).g());
    }
    @Test void commonSpinAndRpmTiming() {
        var s = spinner(Long.MIN_VALUE, List.of());
        assertEquals(0, spinAlpha(s, 800)); assertEquals(.5, spinAlpha(s, 900)); assertEquals(1, spinAlpha(s, 1000));
        assertEquals(.5, spinAlpha(s, 4800)); assertEquals(0, spinAlpha(s, 5000));
        assertEquals(50, spmOffset(s, 600)); assertEquals(12.5, spmOffset(s, 800)); assertEquals(0, spmOffset(s, 1000));
        assertEquals("123", spmText(s.spinsPerMinute())); assertEquals("0", spmText(.99));
        var ticks = spinner(Long.MIN_VALUE, List.of(new SpinEvent(1200, 0, false, false)));
        assertEquals(.5, spinAlpha(ticks, 1350)); assertEquals(0, spinAlpha(ticks, 1500));
        assertEquals(0, spinAlpha(ticks, 4800), "Tick cancels future Alpha fade; SPIN cannot reappear");
    }
    @Test void clearUsesCompletionTimeIncludingLateCompletionBackdatedSequence() {
        var s = spinner(3000, List.of());
        assertEquals(0, clearAlpha(s, 2999)); assertEquals(0, clearAlpha(s, 3000));
        assertEquals(.75, clearAlpha(s, 3200)); assertEquals(1, clearAlpha(s, 3400));
        assertEquals(1.25, clearScale(s, 3000)); assertEquals(.5, clearScale(s, 3240), 1e-7);
        assertEquals(.5625, clearScale(s, 3320), 1e-7); assertEquals(.625, clearScale(s, 3400));
        assertEquals(.5, clearAlpha(s, 4975)); assertEquals(0, clearAlpha(s, 5000));
        var late = spinner(4900, List.of());
        assertEquals(0, clearAlpha(late, 4899)); assertEquals(.9375, clearAlpha(late, 4900));
    }
    @Test void bonusAndWhiteFlashReadOnlyExplicitEvents() {
        var normal = new SpinEvent(3000, 50, false, true);
        var max = new SpinEvent(4000, 100, true, true);
        var s = spinner(2500, List.of(normal, max));
        assertNull(lastBonus(s, 2999, false)); assertSame(normal, lastBonus(s, 3000, false));
        assertEquals(1, bonusAlpha(normal, 3000)); assertEquals(.25, bonusAlpha(normal, 3400));
        assertEquals(1.25, bonusScale(normal, 3000)); assertEquals(.8, bonusScale(normal, 3800));
        assertEquals(1.4, bonusScale(max, 4000)); assertEquals(1.8, bonusScale(max, 5000), 1e-12);
        assertEquals(0, bonusAlpha(max, 4500));
        assertEquals(1, glowColour(s, 3000).r()); assertEquals(3 / 255f, glowColour(s, 3200).r(), 1e-6);
        assertEquals(151 / 255f, glowColour(s, 3200).g(), 1e-6);
    }
}
