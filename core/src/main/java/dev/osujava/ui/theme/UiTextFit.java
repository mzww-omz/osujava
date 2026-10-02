package dev.osujava.ui.theme;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Width fitting at Unicode grapheme boundaries; no shrinking and no OpenGL dependency. */
final class UiTextFit {
    private static final FontRenderContext MEASURE = new FontRenderContext(null, true, true);
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    static String fit(String text, Font font, float width) {
        if (width <= 0) return "";
        return new Measurement(text, font).fit(width);
    }

    /** Widths use the original full-prefix measurement, including kerning and Unicode fallback.
     * A changing row width only revisits the binary search, not AWT shaping already measured.
     */
    static final class Measurement {
        private final String text;
        private final Font font;
        private final double fullWidth;
        private java.util.List<Integer> ends;
        private double[] prefixWidths;
        Measurement(String text, Font font) {
            this.text = text; this.font = font;
            fullWidth = font.getStringBounds(text, MEASURE).getWidth();
        }
        String fit(float width) {
            if (width <= 0) return "";
            if (fullWidth <= width) return text;
            String suffix = "…";
            if (ends == null) {
                ends = new ArrayList<>(); ends.add(0);
                var matcher = GRAPHEME.matcher(text);
                while (matcher.find()) ends.add(matcher.end());
                prefixWidths = new double[ends.size()];
                java.util.Arrays.fill(prefixWidths, Double.NaN);
                prefixWidths[0] = font.getStringBounds(suffix, MEASURE).getWidth();
            }
            if (prefixWidths[0] > width) return "";
            int low = 0, high = ends.size() - 1;
            while (low < high) {
                int mid = (low + high + 1) / 2;
                if (Double.isNaN(prefixWidths[mid]))
                    prefixWidths[mid] = font.getStringBounds(text.substring(0, ends.get(mid)) + suffix, MEASURE).getWidth();
                if (prefixWidths[mid] <= width) low = mid;
                else high = mid - 1;
            }
            return text.substring(0, ends.get(low)) + suffix;
        }
    }
}
