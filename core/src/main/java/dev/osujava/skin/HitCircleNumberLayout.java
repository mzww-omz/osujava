package dev.osujava.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** Native logical glyph sizes and positions, independent of textures and the graphics context. */
public record HitCircleNumberLayout(List<Glyph> glyphs, float width, float height) {
    public record Size(float width, float height) { }
    /** Positions are relative to the centre of the complete number (upward-positive Y). */
    public record Glyph(int digit, float x, float y, float width, float height) { }

    public static HitCircleNumberLayout create(int number, IntFunction<Size> sizes, float overlap) {
        if (number < 0) throw new IllegalArgumentException("Expected a nonnegative combo number");
        List<Glyph> glyphs = new ArrayList<>();
        float advance = 0;
        float left = 0;
        float right = 0;
        float height = 0;
        for (char character : Integer.toString(number).toCharArray()) {
            int digit = character - '0';
            Size size = sizes.apply(digit);
            glyphs.add(new Glyph(digit, advance, 0, size.width(), size.height()));
            left = Math.min(left, advance);
            right = Math.max(right, advance + size.width());
            height = Math.max(height, size.height());
            // LegacySpriteText.Spacing = -GetFontOverlap(): positive overlaps, negative separates.
            advance += size.width() - overlap;
        }
        float centre = (left + right) / 2;
        float blockHeight = height;
        return new HitCircleNumberLayout(glyphs.stream().map(glyph -> new Glyph(glyph.digit(),
                glyph.x() - centre, blockHeight / 2 - glyph.height(), glyph.width(), glyph.height())).toList(),
                right - left, height);
    }

    /** OsuLegacySkinTransformer uses 0.8; DrawableHitCircle applies radius / OBJECT_RADIUS (64). */
    public static float scale(double radius) { return (float) (0.8 * radius / 64); }
}
