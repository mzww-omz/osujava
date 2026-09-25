package dev.osujava.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayfieldViewportTest {
    @Test
    void fitsTheStandardPlayfieldAndMapsPointerCoordinates() {
        PlayfieldViewport viewport = PlayfieldViewport.fit(1100, 720);

        assertEquals(70, viewport.left());
        assertEquals(0, viewport.bottom());
        assertEquals(960, viewport.width());
        assertEquals(720, viewport.height());
        assertEquals(256, viewport.toOsuX(viewport.toScreenX(256)));
        assertEquals(192, viewport.toOsuY(viewport.toScreenY(192)));
        assertTrue(viewport.containsScreenPoint(70, 0));
        assertFalse(viewport.containsScreenPoint(69, 100));
    }

    @Test
    void keepsObjectGeometryProportionalAcrossWindowAspectRatios() {
        PlayfieldViewport wide = PlayfieldViewport.fit(1600, 900);
        PlayfieldViewport tall = PlayfieldViewport.fit(900, 1600);

        assertEquals(wide.width(), wide.toScreenLength(512));
        assertEquals(wide.height(), wide.toScreenLength(384));
        assertEquals(54.4, wide.toOsuLength(wide.toScreenLength(54.4)), 1e-5);
        assertEquals(54.4, tall.toOsuLength(tall.toScreenLength(54.4)), 1e-5);

        float wideX = wide.toScreenX(128);
        float tallX = tall.toScreenX(128);
        assertEquals(128, wide.toOsuX(wideX), 1e-5);
        assertEquals(128, tall.toOsuX(tallX), 1e-5);
        assertEquals(256, wide.toOsuX(wide.toScreenX(256)), 1e-5);
        assertEquals(256, tall.toOsuX(tall.toScreenX(256)), 1e-5);
    }
}
