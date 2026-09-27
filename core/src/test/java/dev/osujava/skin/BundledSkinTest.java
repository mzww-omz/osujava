package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.gameplay.GameplayVisualConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class BundledSkinTest {
    @TempDir Path custom;

    @Test void defaultConfigurationAndSupportedModeImagesLoadFromClasspath() throws Exception {
        var resolver = SkinAssetResolver.withBundledDefault(null, null);
        var config = resolver.readConfiguration();
        assertTrue(config.hasIni());
        assertEquals(1, config.legacyVersion());
        assertEquals(40, config.fonts().hitCircleOverlap());
        assertEquals(3, config.fonts().scoreOverlap());
        assertFalse(config.cursor().rotate());
        assertFalse(config.cursor().expand());
        assertTrue(config.colours().allowSliderBallTint());
        assertEquals(4, config.colours().comboColours().size());
        var colours = new ConfiguredGameplaySkin(GameplayVisualConfig.defaults(), config.colours());
        assertEquals(115 / 255f, colours.comboColor(0).r);
        assertEquals(colours.comboColor(0), colours.comboColor(4));
        assertEquals(colours.comboColor(3), colours.comboColor(-1));
        for (String name : List.of("hitcircle", "approachcircle", "cursor", "sliderfollowcircle", "reversearrow",
                "spinner-background", "taikohitcircle", "taikobigcircle", "taiko-drum-inner", "taiko-hit300",
                "fruit-apple", "fruit-drop", "fruit-bananas-overlay")) {
            var file = resolver.resolve(name).orElseThrow();
            assertEquals(2, file.density());
            assertNotNull(file.classpathResource());
            try (var input = file.openStream()) { assertNotNull(ImageIO.read(input)); }
        }
        assertTrue(resolver.resolve("does-not-exist").isEmpty());
        assertTrue(resolver.resolveSound("does-not-exist").isEmpty());
        assertEquals(10, resolver.resolveHitCircleDigits(config).orElseThrow().size());
        assertEquals(1, resolver.resolveSliderBall().size());
    }

    @Test void customNormalResolutionAndTransparentAssetsBeatBundledRetina() throws Exception {
        Path circle = Files.write(custom.resolve("hitcircle.png"), new byte[]{1});
        Path tail = Files.write(custom.resolve("sliderendcircle.png"), new byte[]{1});
        var resolver = SkinAssetResolver.withBundledDefault(custom, null);
        assertEquals(circle, resolver.resolve("hitcircle").orElseThrow().path());
        assertEquals(1, resolver.resolve("hitcircle").orElseThrow().density());
        assertEquals(tail, resolver.resolve("sliderendcircle").orElseThrow().path());
        assertTrue(resolver.resolve("approachcircle").orElseThrow().fallback());
        assertNotNull(resolver.resolve("approachcircle").orElseThrow().classpathResource());
        assertEquals(40, resolver.readConfiguration().fonts().hitCircleOverlap());
    }

    @Test void optionalLocalFallbackRemainsBetweenCustomAndBundle() throws Exception {
        Path fallback = Files.createDirectory(custom.resolve("fallback"));
        Path circle = Files.write(fallback.resolve("hitcircle.png"), new byte[]{1});
        var resolver = SkinAssetResolver.withBundledDefault(custom, fallback);
        assertEquals(circle, resolver.resolve("hitcircle").orElseThrow().path());
        assertTrue(resolver.resolve("hitcircle").orElseThrow().fallback());
        assertNotNull(resolver.resolve("cursor").orElseThrow().classpathResource());
    }

    @Test void customIniAndNestedFontGlyphsRetainPriorityWhileMissingGlyphsUseBundledFonts() throws Exception {
        Files.writeString(custom.resolve("skin.ini"), "[General]\nCursorRotate: 1\n[Fonts]\nHitCirclePrefix: Assets/numbers\nHitCircleOverlap: 12\nScorePrefix: Assets/hud\n");
        Files.createDirectory(custom.resolve("Assets"));
        Path digit = Files.write(custom.resolve("Assets/numbers-1.png"), new byte[]{1});
        Path score = Files.write(custom.resolve("Assets/hud-5.png"), new byte[]{1});
        var resolver = SkinAssetResolver.withBundledDefault(custom, null);
        var config = resolver.readConfiguration();
        assertTrue(config.cursor().rotate());
        assertEquals(12, config.fonts().hitCircleOverlap());
        var digits = resolver.resolveHitCircleDigits(config).orElseThrow();
        assertEquals(digit, digits.get(1).path());
        assertNotNull(digits.get(0).classpathResource());
        assertEquals(score, resolver.resolveHudGlyph(config.fonts().scorePrefix(), '5').orElseThrow().path());
        assertNotNull(resolver.resolveHudGlyph(config.fonts().scorePrefix(), '6').orElseThrow().classpathResource());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("../cursor"));
    }

    @Test void brokenCustomImagesUseBundleAndTexturesAreDisposedExactlyOnce() throws Exception {
        Files.write(custom.resolve("hitcircle@2x.png"), new byte[]{1});
        Files.write(custom.resolve("hitcircle.png"), new byte[]{1});
        var loaded = new ArrayList<TestTexture>();
        var assets = new OsuSkinAssets(file -> {
            if (file.classpathResource() == null) throw new GdxRuntimeException("corrupt custom PNG");
            var texture = new TestTexture(); loaded.add(texture); return texture;
        }, SkinAssetResolver.withBundledDefault(custom, null));
        assertNotNull(assets.get(OsuSkinAssets.Image.HIT_CIRCLE));
        assertTrue(assets.diagnostic(OsuSkinAssets.Image.HIT_CIRCLE).fallback());
        assertTrue(assets.hasHitCircleDigits());
        assertTrue(assets.hasHudText(OsuSkinAssets.HudFont.SCORE, "0123456789.,%"));
        assertTrue(assets.hasHudText(OsuSkinAssets.HudFont.COMBO, "12x"));
        assertNotNull(assets.judgement(dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Result.GREAT));
        assertEquals(dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.Style.OLD, assets.spinnerStyle());
        assets.dispose(); assets.dispose();
        assertTrue(loaded.stream().allMatch(t -> t.disposals == 1));
    }

    @Test void customStaticAnimationDoesNotMixWithBundledFrames() throws Exception {
        Path ball = Files.write(custom.resolve("sliderb.png"), new byte[]{1});
        var resolver = SkinAssetResolver.withBundledDefault(custom, null);
        assertEquals(List.of(new SkinAssetResolver.AssetFile(ball, 1)), resolver.resolveSliderBall());
        assertNotNull(resolver.resolveAnimation("hit300").getFirst().classpathResource());
    }

    @Test void unknownOrMalformedOptionalIniValuesAreIgnoredWithoutInheritingBundledSettings() throws Exception {
        Files.writeString(custom.resolve("skin.ini"), "[General]\nAllowSliderBallTint: invalid\nUnknown: 123\n"
                + "[Colours]\nCombo3: 0,255,0\nCombo1: 255,0,0\nCombo2: 999,0,0\nCombo9: 0,0,255\n"
                + "[CatchTheBeat]\nHyperDash: invalid\n[Mania]\nUnknown: ignored\n");
        var config = SkinAssetResolver.withBundledDefault(custom, null).readConfiguration();
        assertFalse(config.colours().allowSliderBallTint());
        assertEquals(List.of(new SkinConfiguration.Rgb(1, 0, 0), new SkinConfiguration.Rgb(0, 1, 0)), config.colours().comboColours());
        assertEquals(-2, config.fonts().hitCircleOverlap());
    }

    @Test void unreadableCustomIniAndAbsentBundledProviderFailSafely() throws Exception {
        Files.write(custom.resolve("skin.ini"), new byte[]{(byte) 0xff});
        assertEquals(40, SkinAssetResolver.withBundledDefault(custom, null).readConfiguration().fonts().hitCircleOverlap());
        var absent = new SkinAssetResolver(null, null, "skins/nonexistent");
        assertFalse(absent.readConfiguration().hasIni());
        assertTrue(absent.resolve("hitcircle").isEmpty());
        assertTrue(absent.resolveSound("normal-hitnormal").isEmpty());
    }

    @Test void packagedCoreJarResolvesIniImagesAndAudioWithoutSourceResourceDirectory() throws Exception {
        Path jar = Path.of(System.getProperty("osujava.coreJar"));
        URL gdx = Gdx.class.getProtectionDomain().getCodeSource().getLocation();
        // Platform parent cannot see the test/source classpath: both code and assets come from the built JAR.
        try (var loader = new URLClassLoader(new URL[]{jar.toUri().toURL(), gdx}, ClassLoader.getPlatformClassLoader());
             var archive = new JarFile(jar.toFile())) {
            Class<?> resolverClass = loader.loadClass(SkinAssetResolver.class.getName());
            Object resolver = resolverClass.getMethod("withBundledDefault", Path.class, Path.class).invoke(null, null, null);
            Object config = resolverClass.getMethod("readConfiguration").invoke(resolver);
            assertEquals(true, config.getClass().getMethod("hasIni").invoke(config));
            for (String name : List.of("hitcircle", "taikohitcircle", "fruit-drop")) {
                var optional = (java.util.Optional<?>) resolverClass.getMethod("resolve", String.class).invoke(resolver, name);
                Object file = optional.orElseThrow();
                try (var input = (java.io.InputStream) file.getClass().getMethod("openStream").invoke(file)) {
                    assertNotNull(ImageIO.read(input));
                }
            }
            var sound = (java.util.Optional<?>) resolverClass.getMethod("resolveSound", String.class).invoke(resolver, "normal-hitnormal2");
            Object file = sound.orElseThrow();
            try (var input = (java.io.InputStream) file.getClass().getMethod("openStream").invoke(file)) {
                assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'}, input.readNBytes(4));
            }
            var entries = archive.stream().filter(e -> e.getName().startsWith("skins/default/") && e.getName().endsWith(".png")).toList();
            assertTrue(entries.size() > 400);
            for (var entry : entries) try (var input = archive.getInputStream(entry)) {
                assertNotNull(ImageIO.read(input), entry.getName());
            }
            assertNotNull(archive.getEntry("skins/default/NOTICE.md"));
            assertNull(archive.getEntry("skins/default/mode-mania.png"));
        }
    }

    private static class TestTexture extends Texture {
        int disposals;
        TestTexture() { super(); }
        @Override public int getWidth() { return 128; }
        @Override public int getHeight() { return 128; }
        @Override public void setFilter(TextureFilter min, TextureFilter mag) { }
        @Override public void dispose() { disposals++; }
    }
}
