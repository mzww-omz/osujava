package dev.osujava.ui;

import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.skin.SkinConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectCursorTest {
    @Test void menuCoordinatesRoundTripAcrossAspectRatiosAndUseNativeSdScale() {
        for (float height : new float[]{720, 768, 1080}) {
            var viewport = SongSelectCursor.viewport(height);
            for (float x : new float[]{0, 512, 1280, 1920}) for (float y : new float[]{0, height / 2, height}) {
                assertEquals(x, viewport.toScreenX(viewport.toOsuX(x)), .001);
                assertEquals(y, viewport.toScreenY(viewport.toOsuY(y)), .001);
            }
            assertEquals(64 * height / 768, GameplayCursorRenderer.screenSize(64, viewport), .001);
        }
    }
    @Test void menuInputExpandsAndReleasesWithoutASession() {
        var cursor = new LegacyCursorVisual(new SkinConfiguration.Cursor(true,false,true,false), true, true, 16);
        cursor.input(0, 10, 20, true, true);
        cursor.input(100, 40, 50, true, false);
        assertEquals(1.3, cursor.cursor(100).scale(), .001);
        cursor.input(100, 40, 50, false, false);
        cursor.advance(200);
        assertEquals(1, cursor.cursor(200).scale(), .001);
        assertEquals(40, cursor.cursor(200).x());
        assertEquals(0, cursor.cursor(200).rotation());
    }
}
