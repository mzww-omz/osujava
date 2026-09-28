package dev.osujava.skin;

import java.util.function.IntBinaryOperator;

/** Load-time alpha inventory in logical, bottom-left image coordinates.
 * The entire image is artwork; only its canonical control canvas supplies interaction content.
 * Large composite artwork and intentional transparent 1x1 replacements remain valid assets. */
public record SelectionAssetBounds(Rect opaque, Rect content) {
    public record Rect(float x, float y, float width, float height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);
        public boolean empty() { return width <= 0 || height <= 0; }
    }

    public static SelectionAssetBounds detect(int width, int height, int density,
            float controlWidth, float controlHeight, boolean legacy, IntBinaryOperator alpha) {
        int[] opaque = {width, height, -1, -1};
        int[] content = {width, height, -1, -1};
        int[] visible = {width, height, -1, -1};
        float originY = legacy ? controlHeight - height / (float) density : 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int value = alpha.applyAsInt(x, y);
            if (value == 0) continue;
            include(opaque, x, y);
            float localY = originY + (height - y - .5f) / density;
            if ((x + .5f) / density >= controlWidth || localY < 0 || localY >= controlHeight) continue;
            if (value >= 16) include(visible, x, y);
            if (value >= 160) include(content, x, y);
        }
        return new SelectionAssetBounds(rect(opaque, height, density),
                rect(content[2] < 0 ? visible : content, height, density));
    }

    private static void include(int[] bounds, int x, int y) {
        bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x); bounds[3] = Math.max(bounds[3], y);
    }
    private static Rect rect(int[] b, int height, int density) {
        return b[2] < 0 ? Rect.EMPTY : new Rect(b[0] / (float) density,
                (height - b[3] - 1f) / density, (b[2] - b[0] + 1f) / density, (b[3] - b[1] + 1f) / density);
    }
}
