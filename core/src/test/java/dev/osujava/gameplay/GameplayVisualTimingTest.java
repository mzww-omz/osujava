package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayVisualTimingTest {
    @Test
    void legacyNumberVersionChangesOnlyItsOwnScaleAndFade() {
        assertEquals(1.3, GameplayVisualTiming.hitCircleNumberScale(1120, 1000, 1), 1e-9);
        assertEquals(1, GameplayVisualTiming.hitCircleNumberScale(1120, 1000, 2.7));
        assertEquals(0.5, GameplayVisualTiming.hitCircleNumberAlpha(1120, 1000, 1));
        assertEquals(0.5, GameplayVisualTiming.hitCircleNumberAlpha(1030, 1000, 2.7));
        assertEquals(0, GameplayVisualTiming.hitCircleNumberAlpha(1060, 1000, 2.7));
    }

    @Test
    void approachRadiusAndFadeFollowAbsoluteObjectTime() {
        assertEquals(0, GameplayVisualTiming.approachProgress(400, 1000, 600));
        assertEquals(0.5, GameplayVisualTiming.approachProgress(700, 1000, 600));
        assertEquals(1, GameplayVisualTiming.approachProgress(1000, 1000, 600));
        assertEquals(250, GameplayVisualTiming.approachRadius(100, 0.5));
        assertEquals(0, GameplayVisualTiming.fadeInProgress(400, 1000, 600, 180));
        assertEquals(1, GameplayVisualTiming.fadeInProgress(580, 1000, 600, 180));
    }

    @Test
    void lazerApproachFadeAndHitTransformsFollowBeatmapTime() {
        assertEquals(0, GameplayVisualTiming.approachAlpha(-200, 1000, 1200));
        assertEquals(0.45, GameplayVisualTiming.approachAlpha(200, 1000, 1200), 1e-9);
        assertEquals(0.9, GameplayVisualTiming.approachAlpha(1000, 1000, 1200), 1e-9);
        assertEquals(0.45, GameplayVisualTiming.approachAlpha(1025, 1000, 1200), 1e-9);
        assertEquals(0, GameplayVisualTiming.approachAlpha(1050, 1000, 1200));
        assertEquals(4, GameplayVisualTiming.approachRadius(1, 0));
        assertEquals(1, GameplayVisualTiming.approachRadius(1, 1));
        assertEquals(1.3, GameplayVisualTiming.hitCircleScale(1120, 1000), 1e-9);
        assertEquals(1.4, GameplayVisualTiming.hitCircleScale(1240, 1000), 1e-9);
        assertEquals(1, GameplayVisualTiming.hitCircleAlpha(1000, 1000));
        assertEquals(0.5, GameplayVisualTiming.hitCircleAlpha(1120, 1000));
        assertEquals(0, GameplayVisualTiming.hitCircleAlpha(1240, 1000));
        assertEquals(0.5, GameplayVisualTiming.hitCircleMissAlpha(1050, 1000));
        assertEquals(0, GameplayVisualTiming.hitCircleMissAlpha(1100, 1000));
        assertEquals(0, GameplayVisualTiming.sliderSnakeProgress(-200, 1000, 1200));
        assertEquals(0.5, GameplayVisualTiming.sliderSnakeProgress(0, 1000, 1200));
        assertEquals(1, GameplayVisualTiming.sliderSnakeProgress(200, 1000, 1200));
        assertEquals(0, GameplayVisualTiming.spinnerIntroScale(0, 1000, 1200));
        assertEquals(0.2, GameplayVisualTiming.spinnerIntroScale(400, 1000, 1200));
        assertEquals(1, GameplayVisualTiming.spinnerIntroScale(1000, 1000, 1200));
        assertEquals(1.2, GameplayVisualTiming.spinnerCompletionScale(1320, 1000, true));
        assertEquals(0.8, GameplayVisualTiming.spinnerCompletionScale(1320, 1000, false));
    }

    @Test
    void visualLifetimesAndFadeOutAreClamped() {
        assertFalse(GameplayVisualTiming.isVisible(399, 1000, 600, 1000, 150));
        assertTrue(GameplayVisualTiming.isVisible(400, 1000, 600, 1000, 150));
        assertTrue(GameplayVisualTiming.isVisible(1150, 1000, 600, 1000, 150));
        assertFalse(GameplayVisualTiming.isVisible(1151, 1000, 600, 1000, 150));
        assertEquals(1, GameplayVisualTiming.fadeOutAlpha(1000, 1000, 240));
        assertEquals(0.5, GameplayVisualTiming.fadeOutAlpha(1120, 1000, 240));
        assertEquals(0, GameplayVisualTiming.fadeOutAlpha(1240, 1000, 240));
    }

}
