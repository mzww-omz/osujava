package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinAnimationTest {
    @Test void defaultCompletesAllFramesInOneSecondAndExplicitRateUsesFps() {
        assertEquals(0, SkinAnimation.frameIndex(4, -1, 0));
        assertEquals(1, SkinAnimation.frameIndex(4, -1, .25));
        assertEquals(3, SkinAnimation.frameIndex(4, -1, .999));
        assertEquals(0, SkinAnimation.frameIndex(4, -1, 1));
        assertEquals(1, SkinAnimation.frameIndex(4, 2, .5));
        assertEquals(2, SkinAnimation.frameIndex(4, 2, 1));
        assertEquals(0, SkinAnimation.frameIndex(4, 2, 2));
    }
    @Test void samplingIsStatelessAndInvalidTimesStayAtFirstFrame() {
        for (double time : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(0, SkinAnimation.frameIndex(4, 24, time));
        assertEquals(0, SkinAnimation.frameIndex(1, 24, 100));
        int expected = SkinAnimation.frameIndex(12, 24, .35);
        SkinAnimation.frameIndex(12, 24, 500);
        assertEquals(expected, SkinAnimation.frameIndex(12, 24, .35));
    }
}
