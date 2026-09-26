package dev.osujava.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HitCircleNumberLayoutTest {
    @Test
    void usesEachNativeWidthAndNegativeOverlapAsSpacing() {
        var layout = HitCircleNumberLayout.create(123, digit ->
                new HitCircleNumberLayout.Size(digit * 10, 40), -2);
        assertEquals(64f, layout.width()); // 10 + 20 + 30 - (-2 * 2)
        assertEquals(40f, layout.height());
        assertEquals(-32f, layout.glyphs().get(0).x());
        assertEquals(-20f, layout.glyphs().get(1).x());
        assertEquals(2f, layout.glyphs().get(2).x());
        assertEquals(32f, layout.glyphs().get(2).x() + layout.glyphs().get(2).width());
    }

    @Test
    void positiveOverlapBringsDigitsCloserAndCentresWholeBlock() {
        var layout = HitCircleNumberLayout.create(12, digit ->
                new HitCircleNumberLayout.Size(digit * 10, digit * 20), 3);
        assertEquals(27f, layout.width());
        assertEquals(-13.5f, layout.glyphs().get(0).x());
        assertEquals(-6.5f, layout.glyphs().get(1).x());
        assertEquals(0f, layout.glyphs().get(0).y());
        assertEquals(-20f, layout.glyphs().get(1).y());
    }

    @Test
    void singleDigitHasNoTrailingOverlapAndPreservesAspectRatio() {
        var layout = HitCircleNumberLayout.create(0, digit -> new HitCircleNumberLayout.Size(12, 30), -2);
        assertEquals(12f, layout.width());
        assertEquals(30f, layout.height());
        assertEquals(new HitCircleNumberLayout.Glyph(0, -6, -15, 12, 30), layout.glyphs().getFirst());
        assertEquals(0.8f, HitCircleNumberLayout.scale(64), 0.00001f);
        assertEquals(0.4f, HitCircleNumberLayout.scale(32), 0.00001f);
    }

    @Test
    void densityNormalizesBeforeLayoutAndOverlap() {
        var highResolution = new SkinAssetResolver.AssetFile(null, 2);
        var layout = HitCircleNumberLayout.create(11, digit -> new HitCircleNumberLayout.Size(
                highResolution.logicalSize(40), highResolution.logicalSize(80)), -2);
        assertEquals(42f, layout.width());
        assertEquals(40f, layout.height());
    }
}
