package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionAssetBoundsTest {
    @Test void compositeDecorationIsInventoriedButCannotBecomeControlContent() {
        var b = SelectionAssetBounds.detect(1143, 930, 1, 92, 90, false,
                (x,y) -> x >= 47 && x < 82 && y >= 865 && y < 915 || x > 300 && y < 300 ? 255 : 0);
        assertTrue(b.opaque().width() > 900);
        assertEquals(new SelectionAssetBounds.Rect(47,15,35,50), b.content());
    }
    @Test void densityAndLegacyOriginsHaveEquivalentLogicalContent() {
        for (int density : new int[]{1,2}) {
            var b = SelectionAssetBounds.detect(92*density,85*density,density,92,87,true,
                    (x,y) -> y >= 8*density ? 255 : 0);
            assertEquals(new SelectionAssetBounds.Rect(0,0,92,77), b.content());
        }
    }
    @Test void paddingShadowAndEmptyReplacementDoNotBecomeBody() {
        var b = SelectionAssetBounds.detect(200,200,1,77,90,false,
                (x,y) -> x >= 10 && x < 60 && y >= 130 && y < 180 ? 255 : 10);
        assertEquals(new SelectionAssetBounds.Rect(0,0,200,200), b.opaque());
        assertEquals(new SelectionAssetBounds.Rect(10,20,50,50), b.content());
        var empty = SelectionAssetBounds.detect(1,1,1,77,90,false,(x,y)->0);
        assertTrue(empty.opaque().empty()); assertTrue(empty.content().empty());
    }
    @Test void translucentSkinKeepsVisibleContentWithoutFullImageFallback() {
        var b = SelectionAssetBounds.detect(100,100,1,77,90,false,
                (x,y) -> x >= 20 && x < 50 && y >= 30 && y < 80 ? 80 : 0);
        assertEquals(new SelectionAssetBounds.Rect(20,20,30,50), b.content());
    }
}
