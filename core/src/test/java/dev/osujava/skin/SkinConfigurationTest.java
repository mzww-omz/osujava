package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkinConfigurationTest {
    @TempDir Path directory;

    @Test
    void absentIniUsesLegacyDefaultsButDisablesSkinNumbers() throws IOException {
        var configuration = SkinConfiguration.read(directory);
        assertEquals("default", configuration.fonts().hitCirclePrefix());
        assertEquals(-2f, configuration.fonts().hitCircleOverlap());
        assertFalse(configuration.hasIni());
        assertEquals(configuration, SkinConfiguration.read(null));
    }

    @Test
    void readsCustomFontsFromIni() throws IOException {
        Files.writeString(directory.resolve("skin.ini"), "[Fonts]\nHitCirclePrefix: score\nHitCircleOverlap: -2.5\n");
        var configuration = SkinConfiguration.read(directory);
        assertEquals("score", configuration.fonts().hitCirclePrefix());
        assertEquals(-2.5f, configuration.fonts().hitCircleOverlap());
        assertTrue(configuration.hasIni());
    }

    @Test
    void missingFontsSettingsInExistingIniUseDefaults() throws IOException {
        var configuration = parse("[General]\nName: Test\n[Fonts]\nScorePrefix: ignored\nComboPrefix: ignored\n");
        assertEquals(SkinConfiguration.Fonts.defaults(), configuration.fonts());
        assertTrue(configuration.hasIni());
    }

    @Test
    void onlyReadsFontsAndHandlesBomWhitespaceAndComments() throws IOException {
        var configuration = parse("\uFEFF[Fonts]\n ; ignored\n# ignored\n // ignored\n"
                + " HitCirclePrefix : My_Numbers // comment\nHitCircleOverlap: 3\n"
                + "[Colours]\nHitCirclePrefix: wrong\nHitCircleOverlap: 99\nCombo1: 255,0,0\n");
        assertEquals("My_Numbers", configuration.fonts().hitCirclePrefix());
        assertEquals(3f, configuration.fonts().hitCircleOverlap());
    }

    @Test
    void malformedValuesRetainDefaults() throws IOException {
        for (String value : new String[]{"invalid", "NaN", "Infinity", "1e99", ""}) {
            var configuration = parse("[Fonts]\nHitCirclePrefix: \nHitCircleOverlap: " + value);
            assertEquals(SkinConfiguration.Fonts.defaults(), configuration.fonts());
        }
    }

    private SkinConfiguration parse(String ini) throws IOException {
        return SkinConfiguration.parse(new StringReader(ini));
    }
}
