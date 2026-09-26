package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayVisualTimingTest {
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
        assertEquals(1.375, GameplayVisualTiming.hitCircleScale(1200, 1000), 1e-9);
        assertEquals(1.5, GameplayVisualTiming.hitCircleScale(1400, 1000), 1e-9);
        assertEquals(1, GameplayVisualTiming.hitCircleAlpha(1040, 1000));
        assertEquals(0, GameplayVisualTiming.hitCircleAlpha(1840, 1000));
        assertEquals(0, GameplayVisualTiming.sliderSnakeProgress(-200, 1000, 1200));
        assertEquals(0.5, GameplayVisualTiming.sliderSnakeProgress(0, 1000, 1200));
        assertEquals(1, GameplayVisualTiming.sliderSnakeProgress(200, 1000, 1200));
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

    @Test
    void followCircleTransitionsUseBeatmapTimeForPressReleaseAndEnd() {
        FollowCircleAnimation animation = new FollowCircleAnimation();
        animation.update(false, 1000, 2000);
        assertEquals(0, animation.alphaAt(1000));
        assertEquals(1, animation.scaleAt(1000));

        animation.update(true, 1100, 2000);
        assertEquals(1, animation.alphaAt(1400));
        assertEquals(FollowCircleAnimation.FOLLOW_SCALE, animation.scaleAt(1400));

        animation.update(false, 1500, 2000);
        assertEquals(0, animation.alphaAt(1650));
        assertEquals(FollowCircleAnimation.FOLLOW_SCALE * 1.2, animation.scaleAt(1650));

        animation.update(false, 2000, 2000);
        assertEquals(0, animation.alphaAt(2300));
        assertEquals(1, animation.scaleAt(2300));
    }
}
