package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SliderNestedAnimationTest {
    @Test void arrowIdleLoopsEvery300msWithVersionSpecificEasingAndWobble() {
        assertEquals(1.3, SliderNestedAnimation.arrowScale(1000, 1000, 2000, 1000, false, false));
        assertEquals(1.075, SliderNestedAnimation.arrowScale(1150, 1000, 2000, 1000, false, false), 1e-9);
        assertEquals(1.15, SliderNestedAnimation.arrowScale(1150, 1000, 2000, 1000, false, true), 1e-9);
        assertEquals(1.3, SliderNestedAnimation.arrowScale(1300, 1000, 2000, 1000, false, false));
        assertEquals(5.625, SliderNestedAnimation.arrowWobble(1000, 1000, true));
        assertEquals(0, SliderNestedAnimation.arrowWobble(1150, 1000, true));
        assertEquals(0, SliderNestedAnimation.arrowWobble(1000, 1000, false));
    }
    @Test void arrowHitResetsToOneGrowsWithOutEasingAndShortSpanCapsDuration() {
        assertEquals(1, SliderNestedAnimation.arrowScale(2000, 1000, 2000, 1000, true, false));
        assertEquals(1.3, SliderNestedAnimation.arrowScale(2150, 1000, 2000, 1000, true, false), 1e-9);
        assertEquals(1.4, SliderNestedAnimation.arrowScale(2300, 1000, 2000, 1000, true, false));
        assertEquals(1.4, SliderNestedAnimation.arrowScale(2050, 1000, 2000, 50, true, false));
        assertEquals(0.25, SliderNestedAnimation.repeatAlpha(2150, 2000, 1000, true));
        assertEquals(0.5, SliderNestedAnimation.repeatAlpha(2150, 2000, 1000, false));
    }
    @Test void tickFadesIn150msAndScalesWith600msElasticTransform() {
        var timing = new SliderNestedVisualTiming(1000, 1000, 150);
        assertEquals(0, SliderNestedAnimation.tickAlpha(1000, timing, false, 2000));
        assertEquals(0.5, SliderNestedAnimation.tickAlpha(1075, timing, false, 2000));
        assertEquals(1, SliderNestedAnimation.tickAlpha(1150, timing, false, 2000));
        assertEquals(0.5, SliderNestedAnimation.tickScale(1000, timing, false, false, 2000), 1e-9);
        assertEquals(1, SliderNestedAnimation.tickScale(1600, timing, false, false, 2000), 1e-9);
        assertTrue(SliderNestedAnimation.tickScale(1180, timing, false, false, 2000) > 1);
    }
    @Test void missedTickKeepsItsInitialScaleTransformWhileFading() {
        var timing = new SliderNestedVisualTiming(1000, 1000, 150);
        assertEquals(SliderNestedAnimation.tickScale(1175, timing, false, false, 1100),
                SliderNestedAnimation.tickScale(1175, timing, true, false, 1100));
        double scaleAtHit = SliderNestedAnimation.tickScale(1100, timing, false, false, 1100);
        assertEquals(scaleAtHit * 1.375, SliderNestedAnimation.tickScale(1175, timing, true, true, 1100), 1e-9);
    }

    @Test void tickHitAndMissFadeWithOutQuintOnlyHitGrows() {
        var timing = new SliderNestedVisualTiming(1000, 1000, 150);
        assertEquals(1.375, SliderNestedAnimation.tickScale(2075, timing, true, true, 2000), 1e-9);
        assertEquals(1.5, SliderNestedAnimation.tickScale(2150, timing, true, true, 2000), 1e-9);
        assertEquals(1, SliderNestedAnimation.tickScale(2075, timing, true, false, 2000), 1e-9);
        assertEquals(0.03125, SliderNestedAnimation.tickAlpha(2075, timing, true, 2000));
        assertEquals(0, SliderNestedAnimation.tickAlpha(2150, timing, true, 2000));
    }
}
