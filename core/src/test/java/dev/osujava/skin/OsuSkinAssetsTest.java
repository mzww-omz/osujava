package dev.osujava.skin;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.osujava.skin.OsuSkinAssets.Image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OsuSkinAssetsTest {
    @TempDir Path directory;

    @Test
    void cursorPiecesPreferTwoXShareTexturesAndDisposeOnce() throws IOException {
        for (String name : new String[]{"cursor", "cursormiddle", "cursortrail"}) {
            Files.createFile(directory.resolve(name + ".png"));
            Files.createFile(directory.resolve(name + "@2x.png"));
        }
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture(); loaded.add(texture); return texture;
        });
        for (Image image : new Image[]{Image.CURSOR, Image.CURSOR_MIDDLE, Image.CURSOR_TRAIL}) {
            var asset = assets.get(image); assertEquals(2, asset.density());
            assertEquals(16, asset.logicalWidth()); assertEquals(24, asset.logicalHeight());
            for (int i = 0; i < 100; i++) assertSame(asset, assets.get(image));
        }
        assertEquals(3, loaded.size()); assets.dispose(); assets.dispose();
        assertTrue(loaded.stream().allMatch(t -> t.disposals == 1));
        assertNull(assets.get(Image.CURSOR));
    }

    @Test
    void missingAndBrokenCursorPiecesUseIndependentFallbacks() throws IOException {
        var missing = new OsuSkinAssets(null, file -> { fail("No load"); return null; });
        assertNull(missing.get(Image.CURSOR)); assertNull(missing.get(Image.CURSOR_TRAIL)); missing.dispose();
        Files.createFile(directory.resolve("cursor.png"));
        Files.createFile(directory.resolve("cursormiddle.png"));
        Files.createFile(directory.resolve("cursortrail.png"));
        var shared = new TestTexture();
        var assets = new OsuSkinAssets(directory, file -> {
            if (file.path().getFileName().toString().equals("cursortrail.png")) throw new GdxRuntimeException("bad PNG");
            return shared;
        });
        assertNotNull(assets.get(Image.CURSOR)); assertSame(assets.get(Image.CURSOR).texture(), assets.get(Image.CURSOR_MIDDLE).texture());
        assertNull(assets.get(Image.CURSOR_TRAIL)); assets.dispose(); assets.dispose(); assertEquals(1, shared.disposals);
    }

    @Test
    void hudFontsShareTexturesWithEachOtherAndHitcircleFontAndDisposeOnce() throws IOException {
        Files.writeString(directory.resolve("skin.ini"), "[Fonts]\nHitCirclePrefix: score\nScoreOverlap: 2\nComboOverlap: -3");
        for (char c : "0123456789.%x".toCharArray()) {
            String suffix = c == '.' ? "dot" : c == '%' ? "percent" : String.valueOf(c);
            Files.createFile(directory.resolve("score-" + suffix + "@2x.png"));
        }
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture(); loaded.add(texture); return texture;
        });
        assertEquals(13, loaded.size());
        assertTrue(assets.hasHudText(OsuSkinAssets.HudFont.SCORE, "100.00%"));
        assertTrue(assets.hasHudText(OsuSkinAssets.HudFont.COMBO, "100x"));
        assertSame(assets.hitCircleDigit(5), assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '5'));
        assertSame(assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '5'), assets.hudGlyph(OsuSkinAssets.HudFont.COMBO, '5'));
        assertEquals(16, assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '5').logicalWidth());
        assertEquals(2, assets.hudOverlap(OsuSkinAssets.HudFont.SCORE));
        assertEquals(-3, assets.hudOverlap(OsuSkinAssets.HudFont.COMBO));
        for (int i = 0; i < 100; i++) assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '5');
        assertEquals(13, loaded.size());
        assets.dispose(); assets.dispose();
        assertTrue(loaded.stream().allMatch(t -> t.disposals == 1));
    }

    @Test
    void hudWithoutIniUsesScoreAssetsAndMissingOrBrokenGlyphAllowsReadableFallback() throws IOException {
        Files.createFile(directory.resolve("score-0.png"));
        Files.createFile(directory.resolve("score-5.png"));
        Files.createFile(directory.resolve("score-x.png"));
        var attempts = new java.util.HashMap<Path, Integer>();
        var assets = new OsuSkinAssets(directory, file -> {
            attempts.merge(file.path(), 1, Integer::sum);
            if (file.path().getFileName().toString().equals("score-x.png")) throw new GdxRuntimeException("broken");
            return new TestTexture();
        });
        assertTrue(assets.hasHudText(OsuSkinAssets.HudFont.SCORE, "00000000"));
        assertFalse(assets.hasHudText(OsuSkinAssets.HudFont.SCORE, "100.00%"));
        assertFalse(assets.hasHudText(OsuSkinAssets.HudFont.COMBO, "0x"));
        assertNull(assets.hudGlyph(OsuSkinAssets.HudFont.COMBO, 'x'));
        assertTrue(attempts.values().stream().allMatch(count -> count == 1));
        assets.dispose();
        var absent = new OsuSkinAssets(null, file -> { fail("Unexpected load"); return null; });
        assertFalse(absent.hasHudText(OsuSkinAssets.HudFont.SCORE, "00000000"));
        absent.dispose();
    }

    @Test
    void reverseArrowFollowAndTickReuseDensityResolverAndDisposeOnce() throws IOException {
        for (String name : List.of("reversearrow", "sliderfollowcircle", "sliderscorepoint")) {
            Files.createFile(directory.resolve(name + ".png"));
            Files.createFile(directory.resolve(name + "@2x.png"));
        }
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: 2.5\n");
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        for (Image image : List.of(Image.REVERSE_ARROW, Image.SLIDER_FOLLOW_CIRCLE, Image.SLIDER_TICK)) {
            assertEquals(2, assets.get(image).density());
            assertSame(assets.get(image), assets.get(image));
        }
        assertEquals(2.5, assets.legacyVersion());
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @Test
    void staticBallLoadsOnceAndDisposesOnce() throws IOException {
        Files.createFile(directory.resolve("sliderb.png"));
        var texture = new TestTexture();
        var assets = new OsuSkinAssets(directory, file -> texture);
        assertEquals(1, assets.sliderBallFrames().size());
        assertEquals(directory.resolve("sliderb.png"), assets.sliderBallFrames().getFirst().file().path());
        assets.dispose();
        assets.dispose();
        assertTrue(assets.sliderBallFrames().isEmpty());
        assertEquals(1, texture.disposals);
    }

    @Test
    void animationFramesAreReusedAndEveryFrameIsDisposedExactlyOnce() throws IOException {
        for (int frame = 0; frame < 4; frame++) Files.createFile(directory.resolve("sliderb" + frame + "@2x.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        assertEquals(4, assets.sliderBallFrames().size());
        for (int i = 0; i < 1000; i++) {
            var frame = assets.sliderBallFrames().get(i % 4);
            assertSame(loaded.get(i % 4), frame.texture());
            assertEquals(2, frame.density());
        }
        assertEquals(4, loaded.size());
        assets.dispose();
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void brokenAnimationFallsBackAsWholeAndDisposesPartialFrames(int brokenFrame) throws IOException {
        Files.createFile(directory.resolve("hitcircle.png"));
        Files.createFile(directory.resolve("sliderb.png"));
        for (int frame = 0; frame < 4; frame++) Files.createFile(directory.resolve("sliderb" + frame + ".png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            if (file.path().getFileName().toString().equals("sliderb" + brokenFrame + ".png")) {
                throw new GdxRuntimeException("Broken frame");
            }
            assertNotEquals("sliderb.png", file.path().getFileName().toString());
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        assertTrue(assets.sliderBallFrames().isEmpty());
        assertNotNull(assets.get(Image.HIT_CIRCLE));
        assertEquals(brokenFrame + 1, loaded.size());
        assertEquals(0, loaded.getFirst().disposals);
        assertTrue(loaded.subList(1, loaded.size()).stream().allMatch(texture -> texture.disposals == 1));
        assets.dispose();
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @Test
    void absentOrBrokenStaticBallKeepsVectorFallback() throws IOException {
        var absent = new OsuSkinAssets(null, file -> { fail("No textures expected"); return null; });
        assertTrue(absent.sliderBallFrames().isEmpty());
        Files.createFile(directory.resolve("sliderb.png"));
        var broken = new OsuSkinAssets(directory, file -> { throw new GdxRuntimeException("Broken PNG"); });
        assertTrue(broken.sliderBallFrames().isEmpty());
        absent.dispose();
        broken.dispose();
    }

    @Test
    void failureAfterTextureCreationDisposesFailedAndPartialFramesOnce() throws IOException {
        Files.createFile(directory.resolve("sliderb0.png"));
        Files.createFile(directory.resolve("sliderb1@2x.png"));
        var first = new TestTexture();
        var broken = new TestTexture() {
            @Override public void setFilter(TextureFilter min, TextureFilter mag) {
                throw new GdxRuntimeException("Could not initialize texture");
            }
        };
        var assets = new OsuSkinAssets(directory, file -> file.density() == 2 ? broken : first);
        assertTrue(assets.sliderBallFrames().isEmpty());
        assertEquals(1, first.disposals);
        assertEquals(1, broken.disposals);
        assets.dispose();
        assets.dispose();
        assertEquals(1, first.disposals);
        assertEquals(1, broken.disposals);
    }

    @Test
    void sharedTextureIdentityIsNeverDisposedTwiceEvenDuringFailure() throws IOException {
        Files.createFile(directory.resolve("hitcircle.png"));
        Files.createFile(directory.resolve("sliderb0.png"));
        Files.createFile(directory.resolve("sliderb1.png"));
        var texture = new TestTexture();
        var initial = texture;
        var assets = new OsuSkinAssets(directory, file -> initial);
        assets.dispose();
        assets.dispose();
        assertEquals(1, texture.disposals);

        var shared = new TestTexture();
        assets = new OsuSkinAssets(directory, file -> {
            if (file.path().getFileName().toString().equals("sliderb1.png")) throw new GdxRuntimeException("Broken frame");
            return shared;
        });
        assertTrue(assets.sliderBallFrames().isEmpty());
        assertEquals(0, shared.disposals); // Still owned by the hitcircle.
        assets.dispose();
        assertEquals(1, shared.disposals);
    }

    @Test
    void headAndTailUseSeparatePrefixesWithoutChangingHitCircles() throws IOException {
        for (String name : new String[]{"hitcircle", "hitcircleoverlay", "sliderstartcircle",
                "sliderstartcircleoverlay", "sliderendcircle", "sliderendcircleoverlay"}) {
            Files.createFile(directory.resolve(name + ".png"));
        }
        var assets = new OsuSkinAssets(directory, file -> new TestTexture());
        assertImage(assets, Image.HIT_CIRCLE, "hitcircle.png");
        assertImage(assets, Image.HIT_CIRCLE_OVERLAY, "hitcircleoverlay.png");
        assertImage(assets, Image.SLIDER_START_CIRCLE, "sliderstartcircle.png");
        assertImage(assets, Image.SLIDER_START_CIRCLE_OVERLAY, "sliderstartcircleoverlay.png");
        assertImage(assets, Image.SLIDER_END_CIRCLE, "sliderendcircle.png");
        assertImage(assets, Image.SLIDER_END_CIRCLE_OVERLAY, "sliderendcircleoverlay.png");
        assertNotSame(assets.get(Image.SLIDER_START_CIRCLE), assets.get(Image.SLIDER_END_CIRCLE));
        assets.dispose();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sliderstartcircle", "sliderendcircle"})
    void missingDedicatedBaseFallsBackAsWholePrefixAndSharesTextures(String prefix) throws IOException {
        Files.createFile(directory.resolve("hitcircle.png"));
        Files.createFile(directory.resolve("hitcircleoverlay.png"));
        // An orphan dedicated overlay must not affect selection.
        Files.createFile(directory.resolve(prefix + "overlay@2x.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        Image base = sliderBase(prefix);
        Image overlay = sliderOverlay(prefix);
        assertSame(assets.get(Image.HIT_CIRCLE), assets.get(base));
        assertSame(assets.get(Image.HIT_CIRCLE_OVERLAY), assets.get(overlay));
        assertFalse(assets.hasDedicatedSliderCircle(base));
        for (int frame = 0; frame < 100; frame++) {
            assets.get(base);
            assets.get(overlay);
        }
        assertEquals(3, loaded.size());
        assets.dispose();
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sliderstartcircle", "sliderendcircle"})
    void dedicatedBaseWithoutOverlayOmitsOverlayAndPreservesOtherCircles(String prefix) throws IOException {
        Files.createFile(directory.resolve("hitcircle.png"));
        Files.createFile(directory.resolve("hitcircleoverlay.png"));
        Files.createFile(directory.resolve(prefix + ".png"));
        var assets = new OsuSkinAssets(directory, file -> new TestTexture());
        assertImage(assets, sliderBase(prefix), prefix + ".png");
        assertNull(assets.get(sliderOverlay(prefix)));
        assertTrue(assets.hasDedicatedSliderCircle(sliderOverlay(prefix))); // suppress vector overlay too
        assertImage(assets, Image.HIT_CIRCLE, "hitcircle.png");
        assertImage(assets, Image.HIT_CIRCLE_OVERLAY, "hitcircleoverlay.png");
        String otherPrefix = prefix.equals("sliderstartcircle") ? "sliderendcircle" : "sliderstartcircle";
        assertSame(assets.get(Image.HIT_CIRCLE), assets.get(sliderBase(otherPrefix)));
        assertSame(assets.get(Image.HIT_CIRCLE_OVERLAY), assets.get(sliderOverlay(otherPrefix)));
        assets.dispose();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sliderstartcircle", "sliderendcircle"})
    void dedicatedBaseAndOverlayPreferHighResolutionWithIndependentDensity(String prefix) throws IOException {
        Files.createFile(directory.resolve(prefix + ".png"));
        Files.createFile(directory.resolve(prefix + "@2x.png"));
        Files.createFile(directory.resolve(prefix + "overlay.png"));
        var assets = new OsuSkinAssets(directory, file -> new TestTexture());
        assertImage(assets, sliderBase(prefix), prefix + "@2x.png");
        assertEquals(2, assets.get(sliderBase(prefix)).density());
        assertEquals(16f, assets.get(sliderBase(prefix)).logicalWidth());
        assertEquals(24f, assets.get(sliderBase(prefix)).logicalHeight());
        assertEquals(1, assets.get(sliderOverlay(prefix)).density());
        assets.dispose();

        Files.createFile(directory.resolve(prefix + "overlay@2x.png"));
        assets = new OsuSkinAssets(directory, file -> new TestTexture());
        assertImage(assets, sliderOverlay(prefix), prefix + "overlay@2x.png");
        assertEquals(2, assets.get(sliderOverlay(prefix)).density());
        assets.dispose();
    }

    private void assertImage(OsuSkinAssets assets, Image image, String filename) {
        assertEquals(directory.resolve(filename), assets.get(image).file().path());
    }

    private static Image sliderBase(String prefix) {
        return prefix.equals("sliderstartcircle") ? Image.SLIDER_START_CIRCLE : Image.SLIDER_END_CIRCLE;
    }

    private static Image sliderOverlay(String prefix) {
        return prefix.equals("sliderstartcircle") ? Image.SLIDER_START_CIRCLE_OVERLAY : Image.SLIDER_END_CIRCLE_OVERLAY;
    }

    @Test
    void loadsOnceReusesTexturesAndDisposesWithScreen() throws IOException {
        createFont();
        Files.createFile(directory.resolve("hitcircle.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        assertTrue(assets.hasHitCircleDigits());
        assertEquals(11, loaded.size());
        assertSame(assets.hitCircleDigit(1), assets.hitCircleDigit(1));
        assertEquals(32f, assets.hitCircleDigit(1).logicalWidth());
        assertEquals(-2f, assets.hitCircleOverlap());
        for (int frame = 0; frame < 100; frame++) assets.hitCircleDigit(frame % 10);
        assertEquals(11, loaded.size());
        assets.dispose();
        assertFalse(assets.hasHitCircleDigits());
        assertNull(assets.get(OsuSkinAssets.Image.HIT_CIRCLE));
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @Test
    void failedDigitLoadDisposesPartialFontAndKeepsCircleAsset() throws IOException {
        createFont();
        Files.createFile(directory.resolve("hitcircle.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            if (file.path().getFileName().toString().equals("default-5.png")) {
                throw new GdxRuntimeException("Broken PNG");
            }
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        assertFalse(assets.hasHitCircleDigits());
        assertEquals(6, loaded.size()); // circle + digits 0..4
        assertEquals(0, loaded.getFirst().disposals);
        assertTrue(loaded.subList(1, loaded.size()).stream().allMatch(texture -> texture.disposals == 1));
        assertNotNull(assets.get(OsuSkinAssets.Image.HIT_CIRCLE));
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @Test
    void absentIniOrMissingDigitNeverLoadsPartialFont() throws IOException {
        createFont();
        Files.delete(directory.resolve("skin.ini"));
        var assets = new OsuSkinAssets(directory, file -> { fail("Must not load without ini"); return null; });
        assertFalse(assets.hasHitCircleDigits());
        Files.createFile(directory.resolve("skin.ini"));
        Files.delete(directory.resolve("default-9.png"));
        assets = new OsuSkinAssets(directory, file -> { fail("Must not load an incomplete font"); return null; });
        assertFalse(assets.hasHitCircleDigits());
    }

    private void createFont() throws IOException {
        Files.writeString(directory.resolve("skin.ini"), "[Fonts]\n");
        for (int digit = 0; digit < 10; digit++) Files.createFile(directory.resolve("default-" + digit + ".png"));
    }

    /** Texture's protected empty constructor requires no GL/native libraries. */
    private static class TestTexture extends Texture {
        int disposals;
        TestTexture() { super(); }
        @Override public int getWidth() { return 32; }
        @Override public int getHeight() { return 48; }
        @Override public void setFilter(TextureFilter min, TextureFilter mag) { }
        @Override public void dispose() { disposals++; }
    }
}
