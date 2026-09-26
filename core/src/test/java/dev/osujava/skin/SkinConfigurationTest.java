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
    void legacyVersionUsesLazerDecoderDefaultsAndLatestConstant() throws IOException {
        assertEquals(1, parse("[General]\n").legacyVersion());
        assertEquals(1, SkinConfiguration.defaults().legacyVersion());
        assertEquals(2.7, parse("[General]\nVersion: latest\n").legacyVersion());
        assertEquals(2.5, parse("[General]\nVersion: 2.5\n").legacyVersion());
        assertEquals(1, parse("[General]\nVersion: malformed\n").legacyVersion());
        assertEquals(1, parse("[Fonts]\nVersion: 2.5\n").legacyVersion());
    }

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

    @Test
    void overlayDefaultsAboveNumberAndSupportsTypoWithCanonicalPrecedence() throws IOException {
        assertTrue(SkinConfiguration.defaults().hitCircleOverlayAboveNumber());
        assertTrue(parse("[General]\nName: Test").hitCircleOverlayAboveNumber());
        assertFalse(parse("[General]\nHitCircleOverlayAboveNumber: 0").hitCircleOverlayAboveNumber());
        assertFalse(parse("[General]\nHitCircleOverlayAboveNumer: 0").hitCircleOverlayAboveNumber());
        for (String settings : new String[]{
                "HitCircleOverlayAboveNumer: 0\nHitCircleOverlayAboveNumber: 1",
                "HitCircleOverlayAboveNumber: 1\nHitCircleOverlayAboveNumer: 0"})
            assertTrue(parse("[General]\n" + settings).hitCircleOverlayAboveNumber());
        assertFalse(parse("[General]\nHitCircleOverlayAboveNumber: broken\nHitCircleOverlayAboveNumer: 0")
                .hitCircleOverlayAboveNumber());
        assertTrue(parse("[Fonts]\nHitCircleOverlayAboveNumber: 0").hitCircleOverlayAboveNumber());
    }

    @Test
    void parsesSliderColoursWithoutChangingFontsGeneralOrVersion() throws IOException {
        var c = parse("[Colours]\nSliderBorder: 12, 34, 255 // border\nSliderTrackOverride: 200,0,80\n"
                + "[Fonts]\nHitCirclePrefix: score\nHitCircleOverlap: 4\n"
                + "[General]\nVersion: latest\nHitCircleOverlayAboveNumber: 0");
        assertEquals(new SkinConfiguration.Rgb(12/255f,34/255f,1),c.colours().sliderBorder());
        assertEquals(new SkinConfiguration.Rgb(200/255f,0,80/255f),c.colours().sliderTrackOverride());
        assertEquals("score",c.fonts().hitCirclePrefix());
        assertEquals(4,c.fonts().hitCircleOverlap());
        assertEquals(2.7,c.legacyVersion());
        assertFalse(c.hitCircleOverlayAboveNumber());
    }

    @Test
    void invalidSliderColoursFallBackToLegacyDefaults() throws IOException {
        for (String value : new String[]{"", "0,0", "0,0,0,128", "256,0,0", "-1,0,0", "NaN,0,0",
                "1.5,0,0", "99999999999999999999,0,0", "red"}) {
            var c = parse("[Colours]\nSliderBorder: " + value + "\nSliderTrackOverride: " + value);
            assertEquals(SkinConfiguration.Colours.defaults(),c.colours(),value);
        }
        assertEquals(SkinConfiguration.Colours.defaults(),parse("[Fonts]\nSliderBorder: 0,0,0").colours());
        assertEquals(SkinConfiguration.Colours.defaults(),parse("[Colours]\nSliderBorder: 0,0,0\nSliderBorder: bad").colours());
        assertEquals(new SkinConfiguration.Rgb(0,1,0),parse("[colours]\nsliderborder: 0,255,0").colours().sliderBorder());
    }

    private SkinConfiguration parse(String ini) throws IOException {
        return SkinConfiguration.parse(new StringReader(ini));
    }
}
