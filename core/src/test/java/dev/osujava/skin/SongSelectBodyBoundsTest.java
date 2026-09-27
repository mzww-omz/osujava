package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectBodyBoundsTest {
    @Test void shadowAndTransparentPaddingDoNotConsumeBodyHeight() {
        var body = SongSelectBodyBounds.detect(100, 100,
                (x, y) -> y >= 20 && y < 80 && x >= 5 && x < 95 ? 255 : y < 90 ? 70 : 0);
        assertEquals(.6f, body.height(), .001);
        assertEquals(.9f, body.width(), .001);
        assertEquals(.2f, body.bottom(), .001);
        assertTrue(body.height() < 1);
    }
    @Test void densityDoesNotChangeNormalisedBody() {
        var normal = SongSelectBodyBounds.detect(100,100,(x,y) -> y >= 20 && y < 80 ? 255 : 0);
        var high = SongSelectBodyBounds.detect(200,200,(x,y) -> y >= 40 && y < 160 ? 255 : 0);
        assertEquals(normal, high);
    }
    @Test void transparentSparseAndOpaqueArtHaveSafeFallback() {
        assertEquals(SongSelectBodyBounds.FULL, SongSelectBodyBounds.detect(20,20,(x,y) -> 0));
        assertEquals(SongSelectBodyBounds.FULL, SongSelectBodyBounds.detect(20,20,(x,y) -> x == 1 && y == 1 ? 255 : 0));
        assertEquals(SongSelectBodyBounds.FULL, SongSelectBodyBounds.detect(20,20,(x,y) -> 255));
    }
}
