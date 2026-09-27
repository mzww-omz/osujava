package dev.osujava.skin;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectSkinAssetsTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(strings = {"1.0", "2.1", "2.2", "latest"})
    void thumbnailPolicyUsesSkinVersionRatherThanSkinName(String version) throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nName: Arbitrary\nVersion: " + version + "\n");
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> { fail(); return null; });
        assertEquals(version.equals("2.2") || version.equals("latest"), assets.thumbnailsEnabled());
        assets.dispose();
    }

    @Test void emptySkinUsesNoTexturesAndKeepsDrawingFallback() {
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> {
            fail("Missing files must not invoke the loader"); return null;
        });
        for (Image image : Image.values()) assertNull(assets.get(image));
        assets.dispose(); assets.dispose();
    }

    @ParameterizedTest @ValueSource(strings = {"menu-button-background", "songselect-top", "songselect-bottom"})
    void partialSkinResolvesOnlyPresentElement(String basename) throws Exception {
        Files.createFile(directory.resolve(basename + ".png"));
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> new TestTexture(1, 1));
        for (Image image : Image.values()) {
            if (image.basename.equals(basename)) assertNotNull(assets.get(image));
            else assertNull(assets.get(image));
        }
        assets.dispose();
    }

    @ParameterizedTest @ValueSource(strings = {"", "@2x"})
    void everyElementSupportsNormalOrHighDensityOnly(String suffix) throws Exception {
        List<TestTexture> loaded = new ArrayList<>();
        for (Image image : Image.values()) Files.createFile(directory.resolve(image.basename + suffix + ".png"));
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> {
            var texture = new TestTexture(32, 48); loaded.add(texture); return texture;
        });
        int density = suffix.isEmpty() ? 1 : 2;
        for (int frame = 0; frame < 100; frame++) for (Image image : Image.values()) {
            var asset = assets.get(image);
            assertEquals(density, asset.density());
            assertEquals(32f / density, asset.logicalWidth());
            assertEquals(48f / density, asset.logicalHeight());
        }
        assertEquals(Image.values().length, loaded.size());
        assets.dispose(); assets.dispose();
        assertTrue(loaded.stream().allMatch(t -> t.disposals == 1));
        for (Image image : Image.values()) assertNull(assets.get(image));
    }

    @Test void providerPriorityPrecedesDensityAndFailuresTryEveryNextCandidate() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(directory.resolve("menu-button-background.png"));
        Files.createFile(fallback.resolve("menu-button-background@2x.png"));
        var resolver = new SkinAssetResolver(directory, fallback, SkinAssetResolver.DEFAULT_RESOURCE_ROOT);
        var custom = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(directory.resolve("menu-button-background.png"), custom.get(Image.MENU_BUTTON_BACKGROUND).file().path());
        custom.dispose();
        Files.createFile(directory.resolve("menu-button-background@2x.png"));
        var high = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(2, high.get(Image.MENU_BUTTON_BACKGROUND).density()); high.dispose();
        var brokenHigh = new SongSelectSkinAssets(resolver, file -> {
            if (!file.fallback() && file.density() == 2) throw new GdxRuntimeException("Broken PNG");
            return new TestTexture(32, 48);
        });
        assertEquals(1, brokenHigh.get(Image.MENU_BUTTON_BACKGROUND).density()); brokenHigh.dispose();
        var brokenCustom = new SongSelectSkinAssets(resolver, file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Unreadable custom PNG");
            return new TestTexture(32, 48);
        });
        assertEquals(fallback.resolve("menu-button-background@2x.png"), brokenCustom.get(Image.MENU_BUTTON_BACKGROUND).file().path());
        brokenCustom.dispose();
        var bundled = new SongSelectSkinAssets(resolver, file -> {
            if (file.classpathResource() == null) throw new GdxRuntimeException("Unreadable local PNG");
            return new TestTexture(32, 48);
        });
        assertEquals("skins/default/menu-button-background@2x.png", bundled.get(Image.MENU_BUTTON_BACKGROUND).file().classpathResource());
        bundled.dispose();
    }

    @Test void brokenWithoutFallbackStaysMissing() throws Exception {
        Files.writeString(directory.resolve("songselect-top.png"), "not a PNG");
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> { throw new GdxRuntimeException("Decode failed"); });
        assertNull(assets.get(Image.TOP)); assets.dispose();
    }

    @ParameterizedTest @ValueSource(ints = {1, 10000})
    void tinyAndUnusualAspectRatioRemainVisualOnly(int width) throws Exception {
        Files.createFile(directory.resolve("songselect-top.png"));
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> new TestTexture(width, 1));
        assertEquals(width, assets.get(Image.TOP).logicalWidth()); assets.dispose();
    }

    @Test void failedInitializationDisposesOnlyFailedTextureAndKeepsFallbackOwned() throws Exception {
        Files.createFile(directory.resolve("songselect-top@2x.png"));
        Files.createFile(directory.resolve("songselect-top.png"));
        var failed = new TestTexture(32, 48) {
            @Override public void setFilter(TextureFilter min, TextureFilter mag) { throw new GdxRuntimeException("Cannot set filter"); }
        };
        var good = new TestTexture(32, 48);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> file.density() == 2 ? failed : good);
        assertSame(good, assets.get(Image.TOP).texture());
        assertEquals(1, failed.disposals); assertEquals(0, good.disposals);
        assets.dispose(); assets.dispose();
        assertEquals(1, failed.disposals); assertEquals(1, good.disposals);
    }

    @Test void sharedIdentityIsOwnedOnceAndConfigurationUsesSharedResolver() throws Exception {
        Files.createFile(directory.resolve("songselect-top.png"));
        Files.createFile(directory.resolve("songselect-bottom.png"));
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: latest\n[Colours]\nSongSelectActiveText: 10,20,30\nSongSelectInactiveText: 40,50,60\n");
        var texture = new TestTexture(32, 48);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> texture);
        assertSame(assets.get(Image.TOP).texture(), assets.get(Image.BOTTOM).texture());
        assertEquals(2.7, assets.configuration().legacyVersion());
        assertEquals(10 / 255f, assets.configuration().songSelect().activeText().r());
        assets.dispose(); assets.dispose(); assertEquals(1, texture.disposals);
    }

    @Test void configurationFallbackKeepsEachIniSelfContained() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.writeString(fallback.resolve("skin.ini"), "[General]\nVersion: 2.2\n[Colours]\nSongSelectActiveText: 20,30,40\n");
        var resolver = new SkinAssetResolver(directory, fallback, SkinAssetResolver.DEFAULT_RESOURCE_ROOT);
        var fallbackConfig = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(2.2, fallbackConfig.configuration().legacyVersion());
        assertEquals(20 / 255f, fallbackConfig.configuration().songSelect().activeText().r());
        fallbackConfig.dispose();
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: latest\n");
        var custom = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(2.7, custom.configuration().legacyVersion());
        assertNull(custom.configuration().songSelect().activeText()); // No per-key merging from fallback.
        custom.dispose();
        Files.delete(directory.resolve("skin.ini")); Files.delete(fallback.resolve("skin.ini"));
        var bundled = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(1, bundled.configuration().legacyVersion());
        assertEquals(new SkinConfiguration.Rgb(1, 1, 1), bundled.configuration().songSelect().activeText());
        bundled.dispose();
    }

    @Test void malformedStarTriesNormalThenFallbackAndKeepsProviderPriority() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(directory.resolve("star@2x.png"));
        Files.createFile(directory.resolve("star.png"));
        Files.createFile(fallback.resolve("star@2x.png"));
        var good = new TestTexture(40, 40);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory, fallback), file -> {
            if (file.density() == 2 && !file.fallback()) throw new GdxRuntimeException("Corrupt star");
            return good;
        });
        assertEquals(directory.resolve("star.png"), assets.get(Image.STAR).file().path());
        assertSame(good, assets.starTexture()); assets.dispose();
        var fallbackAssets = new SongSelectSkinAssets(new SkinAssetResolver(directory, fallback), file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Corrupt star");
            return new TestTexture(40, 40);
        });
        assertEquals(fallback.resolve("star@2x.png"), fallbackAssets.get(Image.STAR).file().path());
        assertEquals(20, fallbackAssets.get(Image.STAR).logicalWidth()); fallbackAssets.dispose();
    }

    @Test void missingAndMalformedStarLeaveProceduralFallbackAvailableToRenderer() throws Exception {
        var missing = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> { fail(); return null; });
        assertNull(missing.starTexture()); missing.dispose();
        Files.writeString(directory.resolve("star.png"), "not a PNG");
        var broken = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> { throw new GdxRuntimeException("Decode failed"); });
        assertNull(broken.get(Image.STAR)); assertNull(broken.starTexture()); broken.dispose();
    }

    @Test void gradeCandidatesUseSkinFallbackBundledAndDensity() throws Exception {
        Path fallback=Files.createDirectory(directory.resolve("grade-fallback"));
        Files.createFile(directory.resolve("ranking-X-small.png"));
        Files.createFile(fallback.resolve("ranking-X-small@2x.png"));
        var resolver=new SkinAssetResolver(directory,fallback,SkinAssetResolver.DEFAULT_RESOURCE_ROOT);
        var custom=new SongSelectSkinAssets(resolver,file -> new TestTexture(64,48));
        assertEquals(directory.resolve("ranking-X-small.png"),custom.get(Image.GRADE_SS).file().path());
        assertEquals(1,custom.get(Image.GRADE_SS).density()); custom.dispose();
        var broken=new SongSelectSkinAssets(resolver,file -> {
            if(!file.fallback())throw new GdxRuntimeException("Corrupt grade"); return new TestTexture(64,48);
        });
        assertEquals(fallback.resolve("ranking-X-small@2x.png"),broken.get(Image.GRADE_SS).file().path());
        assertEquals(32,broken.get(Image.GRADE_SS).logicalWidth()); broken.dispose();
        var bundled=new SongSelectSkinAssets(resolver,file -> {
            if(file.classpathResource()==null)throw new GdxRuntimeException("Corrupt grade"); return new TestTexture(64,48);
        });
        for(var grade:List.of(Image.GRADE_SS,Image.GRADE_S,Image.GRADE_A,Image.GRADE_B,Image.GRADE_C,Image.GRADE_D)) {
            assertEquals("skins/default/"+grade.basename+"@2x.png",bundled.get(grade).file().classpathResource());
        }
        bundled.dispose();
    }

    private static class TestTexture extends Texture {
        int disposals;
        final int width, height;
        TestTexture(int width, int height) { super(); this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void setFilter(TextureFilter min, TextureFilter mag) { }
        @Override public void dispose() { disposals++; }
    }
}
