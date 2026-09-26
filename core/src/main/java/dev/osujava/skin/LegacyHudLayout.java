package dev.osujava.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** LegacySpriteText / TextBuilder native glyph layout, in top-left coordinates.
 * Digits in score/accuracy use the width of '5'; punctuation and combo remain proportional.
 */
public record LegacyHudLayout(String text, List<Glyph> glyphs, float width, float height) {
    public record Size(float width, float height) { }
    public record Glyph(char character, float x, float y, float width, float height) { }
    public LegacyHudLayout { glyphs = List.copyOf(glyphs); }

    public static String scoreText(long score) { return String.format(Locale.ROOT, "%08d", score); }
    public static String accuracyText(double accuracy) {
        // FormatUtils.FormatAccuracy floors before formatting; never rounds into a higher grade.
        return String.format(Locale.ROOT, "%.2f%%", Math.floor(accuracy * 10000) / 100);
    }
    public static LegacyHudLayout score(long value, Function<Character, Size> sizes, float overlap) {
        return create(scoreText(value), sizes, overlap, true);
    }
    public static LegacyHudLayout accuracy(double value, Function<Character, Size> sizes, float overlap) {
        return create(accuracyText(value), sizes, overlap, true);
    }
    public static LegacyHudLayout combo(int value, Function<Character, Size> sizes, float overlap) {
        return create(value + "x", sizes, overlap, false);
    }
    public static LegacyHudLayout create(String text, Function<Character, Size> sizes, float overlap, boolean fixed) {
        List<Glyph> glyphs = new ArrayList<>();
        float x = 0, width = 0, height = 0;
        Size reference = sizes.apply('5');
        for (char character : text.toCharArray()) {
            Size size = sizes.apply(character);
            if (size == null) continue; // LegacyGlyphStore has no invented default-prefix substitution.
            if (!glyphs.isEmpty()) x -= overlap;
            float advance = fixed && Character.isDigit(character) ? reference == null ? 0 : reference.width : size.width;
            glyphs.add(new Glyph(character, x + (advance - size.width) / 2, 0, size.width, size.height));
            x += advance;
            width = Math.max(width, x);
            height = Math.max(height, size.height);
        }
        return new LegacyHudLayout(text, glyphs, width, height);
    }
}
