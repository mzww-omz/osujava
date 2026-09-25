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
}
