package dev.osujava.ui.theme;

import java.awt.Font;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmoothUiFontFitCacheTest {
    private Map<?, ?> cache(SmoothUiFont font, String name) throws Exception {
        var field = SmoothUiFont.class.getDeclaredField(name); field.setAccessible(true);
        return (Map<?, ?>) field.get(font);
    }
    @Test void changingRowWidthReusesMeasurementsWhileFontSizeStyleAndMetadataHaveSeparateEntries() throws Exception {
        try (var font = new SmoothUiFont()) {
            String text = "夜空の彼方への冒険 — Long Unicode Title 👩‍🚀";
            var measures = cache(font, "measurements");
            for (int size : new int[]{24, 34, 48}) for (boolean bold : new boolean[]{false, true}) {
                Object measure = null;
                for (int width : new int[]{500, 120, 290, 90, 440}) {
                    assertEquals(UiTextFit.fit(text, new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size), width),
                            font.fitLabel(text, size, bold, width));
                    var cached = measures.get(size + ":" + bold + ":" + text);
                    if (measure != null) assertSame(measure, cached);
                    measure = cached;
                }
            }
            assertEquals(6, measures.size());
            font.fitLabel("Changed metadata", 34, false, 200);
            assertEquals(7, measures.size());
        }
    }
    @Test void emptyFitsAreBoundedAndCloseReleasesMeasurementsAndFontReferences() throws Exception {
        var font = new SmoothUiFont();
        var measurements = cache(font, "measurements");
        var fitted = cache(font, "fittedLabels");
        for (int i = 0; i < 600; i++) {
            assertEquals("", font.fitLabel("Title " + i, 34, false, 1));
            assertTrue(measurements.size() <= 512);
            assertTrue(fitted.size() <= 512);
        }
        font.close();
        assertTrue(measurements.isEmpty()); assertTrue(fitted.isEmpty());
        assertTrue(cache(font, "fonts").isEmpty());
    }
}
