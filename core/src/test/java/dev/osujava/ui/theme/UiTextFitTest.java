package dev.osujava.ui.theme;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UiTextFitTest {
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
