package dev.osujava.skin;

import java.nio.file.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SkinConfigurationOwnershipTest {
    @TempDir Path root;
    @Test void menuAndGameplayShareDefaultsDespiteForeignImageProvider() throws Exception {
        Path own = Files.createDirectory(root.resolve("own"));
        Path fallback = Files.createDirectory(root.resolve("fallback"));
        Files.writeString(fallback.resolve("skin.ini"), "[General]\nVersion: 1\nCursorExpand: 0\n[Fonts]\nScorePrefix: foreign");
        var resolver = SkinAssetResolver.withBundledDefault(own, fallback);
        assertEquals(resolver.readSelectedConfiguration(), resolver.readConfiguration());
        assertEquals(2.7, resolver.readConfiguration().legacyVersion());
        assertTrue(resolver.readConfiguration().cursor().expand());
        assertEquals("score", resolver.readConfiguration().fonts().scorePrefix());
        Files.write(own.resolve("skin.ini"), new byte[]{(byte) 0xff});
        assertEquals(resolver.readSelectedConfiguration(), resolver.readConfiguration());
    }
    @Test void userVersionOverridePreservesOtherAuthoredSettings() throws Exception {
        Path user = Files.createDirectory(root.resolve("User"));
        Files.writeString(user.resolve("SKIN.INI"), "[General]\nVersion: 1\nCursorExpand: 0\n[Fonts]\nScorePrefix: custom");
        var configuration = new SkinAssetResolver(user).readConfiguration();
        assertEquals(SkinConfiguration.LATEST_VERSION, configuration.legacyVersion());
        assertFalse(configuration.cursor().expand());
        assertEquals("custom", configuration.fonts().scorePrefix());
    }
    @Test void imageFallbackDoesNotSupplySelectedSkinsVersionOrColours() throws Exception {
        Path own = Files.createDirectory(root.resolve("own"));
        Path fallback = Files.createDirectory(root.resolve("fallback"));
        Files.writeString(fallback.resolve("skin.ini"), "[General]\nVersion: 1\n[Colours]\nSongSelectActiveText: 255,0,0");
        Files.createFile(fallback.resolve("menu-button-background.png"));
        var resolver = SkinAssetResolver.withBundledDefault(own, fallback);
        assertTrue(resolver.resolve("menu-button-background").orElseThrow().fallback());
        assertEquals(2.7, resolver.readSelectedConfiguration().legacyVersion());
        assertEquals(SkinConfiguration.SongSelect.defaults(), resolver.readSelectedConfiguration().songSelect());
        Files.writeString(own.resolve("skin.ini"), "[General]\n");
        assertEquals(1, resolver.readSelectedConfiguration().legacyVersion());
        assertEquals(1, new SkinAssetResolver(null, fallback).readSelectedConfiguration().legacyVersion());
        assertTrue(SkinAssetResolver.withBundledDefault(null, null).readSelectedConfiguration().hasIni());
    }
    @Test void animationRateDefaultsAndInvalidValuesNeverProduceZeroDuration() throws Exception {
        assertEquals(-1, SkinConfiguration.defaults().animationFramerate());
        for (String rate : new String[]{"0", "-2", "NaN", "2.5", "999999999999"})
            assertEquals(-1, SkinConfiguration.parse(new StringReader("[General]\nAnimationFramerate: " + rate)).animationFramerate());
        assertEquals(24, SkinConfiguration.parse(new StringReader("[General]\nAnimationFramerate: 24\nAnimationFramerate: 0")).animationFramerate());
        assertEquals(24, SkinConfiguration.parse(new StringReader("[General]\nAnimationFramerate: 24\nAnimationFramerate: -1")).animationFramerate());
    }
}
