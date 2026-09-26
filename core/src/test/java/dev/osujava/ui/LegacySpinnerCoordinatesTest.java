package dev.osujava.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacySpinnerCoordinatesTest {
    @Test void circleRotationPreservesClockwiseDirectionAcrossYFlip() {
        assertEquals(-120, LegacySpinnerCoordinates.screenRotation(120));
        assertEquals(90, LegacySpinnerCoordinates.screenRotation(-90));
    }
    @Test void windowSpaceUses640By480AndMinusEightPosition() {
        var c = LegacySpinnerCoordinates.fit(640, 480);
        assertEquals(1, c.unit()); assertEquals(0, c.x(0)); assertEquals(640, c.x(640));
        assertEquals(488, c.y(0)); assertEquals(240, c.y(248)); assertEquals(8, c.y(480));
    }
    @Test void aspectAndHiDpiDoNotReusePlayfieldScale() {
        var wide = LegacySpinnerCoordinates.fit(1920, 1080);
        assertEquals(2.25, wide.unit()); assertEquals(240, wide.x(0)); assertEquals(960, wide.x(320));
        assertEquals(540, wide.y(248));
        var retina = LegacySpinnerCoordinates.fit(1280, 960);
        assertEquals(2, retina.unit()); assertEquals(480, retina.y(248));
        assertNotEquals(PlayfieldViewport.fit(1280, 960).scale(), retina.unit());
        var tall = LegacySpinnerCoordinates.fit(480, 800); assertEquals(.75, tall.unit()); assertEquals(400, tall.y(248));
    }
}
