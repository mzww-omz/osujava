package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHudLayoutTest {
    private final Function<Character, LegacyHudLayout.Size> sizes = c -> new LegacyHudLayout.Size(
            c == '1' ? 8 : c == '.' ? 4 : c == '%' ? 25 : c == 'x' ? 12 : 20, c == '.' ? 8 : 32);
    @Test void scoreUsesEightMinimumDigitsAndWidthOfFiveWithCentredNativeSprites() {
        var score = LegacyHudLayout.score(1, sizes, 2);
        assertEquals("00000001", score.text());
        assertEquals(146, score.width());
        assertEquals(132, score.glyphs().getLast().x());
        assertEquals(8, score.glyphs().getLast().width());
        assertEquals(32, score.height());
        assertEquals("123456789", LegacyHudLayout.scoreText(123456789));
    }
    @Test void accuracyUsesNativePunctuationWithFixedWidthDigitsAndFloorsPercentage() {
        var accuracy = LegacyHudLayout.accuracy(0.9912, sizes, 2);
        assertEquals("99.12%", accuracy.text());
        assertEquals(99, accuracy.width()); // four digits + dot + percent - five overlaps
        assertEquals(0, accuracy.glyphs().get(2).y()); // native textures are top aligned
        assertEquals(4, accuracy.glyphs().get(2).width());
        assertEquals("100.00%", LegacyHudLayout.accuracyText(1));
        assertEquals("99.99%", LegacyHudLayout.accuracyText(0.999999));
    }
    @Test void comboIsProportionalIncludingXAndHasNoPadding() {
        for (int value : new int[]{0, 1, 9, 10, 100, 1234}) {
            var combo = LegacyHudLayout.combo(value, sizes, 0);
            assertEquals(value + "x", combo.text());
            assertEquals(combo.text().chars().mapToDouble(c -> sizes.apply((char)c).width()).sum(), combo.width());
        }
        assertEquals(20, LegacyHudLayout.combo(1, sizes, 0).width());
        assertEquals(18, LegacyHudLayout.combo(1, sizes, 2).width());
        assertEquals(22, LegacyHudLayout.combo(1, sizes, -2).width());
    }
    @Test void rawLegacyMissingGlyphIsOmittedWithoutInventingAnAssetName() {
        var layout = LegacyHudLayout.create("1x", c -> c == 'x' ? null : sizes.apply(c), 0, false);
        assertEquals(1, layout.glyphs().size());
        assertEquals(8, layout.width());
    }
}
