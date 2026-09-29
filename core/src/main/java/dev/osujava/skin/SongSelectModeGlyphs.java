package dev.osujava.skin;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/** Original geometric fallback symbols; no official artwork is copied or extracted. */
final class SongSelectModeGlyphs {
    static Texture texture(int mode, int size) {
        Pixmap pixels = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            pixels.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
                pixels.drawPixel(x, y, pixel(mode, x, y, size));
            Texture texture = new Texture(pixels);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally { pixels.dispose(); }
    }

    static int pixel(int mode, int x, int y, int size) {
        double px = (x + .5) / size * 2 - 1, py = (y + .5) / size * 2 - 1;
        double distance = switch (mode) {
            case 1 -> Math.min(Math.abs(Math.hypot(px, py) - .68) - .09,
                    Math.max(Math.abs(px) - .065, Math.abs(py) - .59));
            case 2 -> Math.min(Math.max(Math.abs(Math.hypot(px, py - .05) - .55) - .08, .12 - py),
                    Math.hypot(px - .12, py + .43) - .14);
            case 3 -> Math.min(Math.min(bar(px, py, -.57), bar(px, py, -.19)),
                    Math.min(bar(px, py, .19), bar(px, py, .57)));
            default -> Math.abs(Math.hypot(px, py) - .64) - .11;
        };
        int alpha = (int) Math.round(Math.max(0, Math.min(1, .5 - distance * size / 2)) * 255);
        return 0xffffff00 | alpha;
    }
    private static double bar(double x, double y, double centre) {
        return Math.max(Math.abs(x - centre) - .105, Math.abs(y) - .68);
    }
}
