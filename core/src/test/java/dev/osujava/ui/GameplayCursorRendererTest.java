package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameplayCursorRendererTest {
    @Test void nativeLogicalSizeUndoesNonPlayfieldMagicScaleWithoutStretchingToContainerSize() {
        var viewport = PlayfieldViewport.fit(1024, 768);
        assertEquals(80, GameplayCursorRenderer.screenSize(64, viewport));
        assertEquals(60, GameplayCursorRenderer.screenSize(48, viewport));
        assertEquals(15, GameplayCursorRenderer.screenSize(12, viewport));
        assertEquals(40, GameplayCursorRenderer.screenSize(64, PlayfieldViewport.fit(512, 384)));
    }
}
