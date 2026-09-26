package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OsuObjectGeometryTest {
    @Test
    void matchesLegacyScaleAndArFadeFromOsuHitObject() {
        assertEquals(54.4 * 1.00041, OsuObjectGeometry.radius(0), 1e-6);
        assertEquals(32 * 1.00041, OsuObjectGeometry.radius(5), 1e-6);
        assertEquals(9.6 * 1.00041, OsuObjectGeometry.radius(10), 1e-6);
        assertEquals(-3.2 * 1.00041, OsuObjectGeometry.stackOffsetPerHeight(5), 1e-6);
        assertEquals(1200, ApproachTimeCalculator.preemptMs(5));
        assertEquals(450, ApproachTimeCalculator.preemptMs(10));
        assertEquals(400, ApproachTimeCalculator.fadeInMs(450));
        assertEquals(200, ApproachTimeCalculator.fadeInMs(225));
    }
}
