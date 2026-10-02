package dev.osujava.ui.theme;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UiTextFitTest {
    @ParameterizedTest @ValueSource(strings = {
            "Long AVATAR title with kerning and ligatures ffi fi fl",
            "夜空の彼方への冒険 — A Very Long Unicode Song Title 👩‍🚀 é",
            "별빛 아래 우리들의 이야기 — 夜空中最亮的星", "ééé👩‍🚀👩‍🚀✦∞"})
    void cachedMeasurementMatchesOriginalAlgorithmAsWidthShrinksAndExpands(String text) {
        for (int style : new int[]{Font.PLAIN, Font.BOLD, Font.ITALIC}) {
            Font font = new Font("SansSerif", style, 34);
            var measurement = new UiTextFit.Measurement(text, font);
            for (int step = 0; step < 200; step++) {
                float width = (step * 157 % 501) + .25f;
                assertEquals(originalFit(text, font, width), measurement.fit(width));
            }
            assertEquals("", measurement.fit(0));
            assertEquals(text, measurement.fit(10000));
        }
    }
    /** Pre-cache implementation retained as the compatibility oracle. */
    private String originalFit(String text, Font font, float width) {
        if (width <= 0) return "";
        if (font.getStringBounds(text, context).getWidth() <= width) return text;
        String suffix = "…";
        if (font.getStringBounds(suffix, context).getWidth() > width) return "";
        var ends = new java.util.ArrayList<Integer>(); ends.add(0);
        var matcher = Pattern.compile("\\X").matcher(text);
        while (matcher.find()) ends.add(matcher.end());
        int low = 0, high = ends.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (font.getStringBounds(text.substring(0, ends.get(mid)) + suffix, context).getWidth() <= width) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, ends.get(low)) + suffix;
    }
    private final Font font = new Font("SansSerif", Font.BOLD, 34);
    private final FontRenderContext context = new FontRenderContext(null, true, true);
    @ParameterizedTest @ValueSource(strings = {
            "A Very Long English Song Title That Will Not Fit in a Song Select Row",
            "夜明けの星空と夢の続きを描く物語", "夜空中最亮的星与漫长旅程的回忆", "별빛 아래 우리들의 아름다운 이야기",
            "Starlight ✦ ∞ → Café é 👩‍🚀", "ééééééééé", "👩‍🚀👩‍🚀👩‍🚀👩‍🚀", "An Extremely Long Difficulty or Mapper Name"})
    void ellipsisFitsAndPreservesGraphemes(String text) {
        for (int width = 1; width <= 300; width += 7) {
            String fitted = UiTextFit.fit(text, font, width);
            assertTrue(font.getStringBounds(fitted, context).getWidth() <= width, fitted);
            if (fitted.isEmpty() || fitted.equals(text)) continue;
            assertTrue(fitted.endsWith("…"));
            String prefix = fitted.substring(0, fitted.length() - 1);
            assertTrue(text.startsWith(prefix));
            var matcher = Pattern.compile("\\X").matcher(text);
            boolean boundary = prefix.isEmpty();
            while (matcher.find()) boundary |= matcher.end() == prefix.length();
            assertTrue(boundary, "Split a Unicode grapheme: " + fitted);
        }
    }
    @Test void shortLabelsAndImpossibleWidthsAreStable() {
        assertEquals("Title", UiTextFit.fit("Title", font, 300));
        assertEquals("", UiTextFit.fit("Title", font, 0));
        assertEquals("", UiTextFit.fit("Title", font, 1));
    }
}
