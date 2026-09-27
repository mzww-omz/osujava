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
        if (font.getStringBounds(text, MEASURE).getWidth() <= width) return text;
        String suffix = "…";
        if (font.getStringBounds(suffix, MEASURE).getWidth() > width) return "";
        var ends = new ArrayList<Integer>();
        ends.add(0);
        var matcher = GRAPHEME.matcher(text);
        while (matcher.find()) ends.add(matcher.end());
        int low = 0, high = ends.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (font.getStringBounds(text.substring(0, ends.get(mid)) + suffix, MEASURE).getWidth() <= width) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, ends.get(low)) + suffix;
    }
}
