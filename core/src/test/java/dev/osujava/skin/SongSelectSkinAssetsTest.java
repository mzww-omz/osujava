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

    @ParameterizedTest @ValueSource(doubles = {1, 1.1, 1.5, 2, 2.2, 2.7})
    void selectionAnchorVersionGateIsIndependentOfThumbnails(double version) throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: " + version);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> new TestTexture(1, 1));
        assertEquals(version <= 1, assets.legacySelectionAnchors());
        assertEquals(version >= 2.2, assets.thumbnailsEnabled());
        assets.dispose();
    }

    @Test void animationCannotStarveLaterCursorAndInterfaceAssets() throws Exception {
        for (String name : new String[]{"menu-back-0", "menu-back-1", "cursor", "selection-mode"})
            Files.createFile(directory.resolve(name + ".png"));
        var order = new ArrayList<String>();
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> {
            String name = file.path().getFileName().toString();
            order.add(name);
            if (name.equals("menu-back-1.png")) throw new GdxRuntimeException("budget reached");
            return new TestTexture(100, 90);
        });
        assertNotNull(assets.get(Image.CURSOR)); assertNotNull(assets.get(Image.MODE));
        assertEquals(1, assets.backFrameCount());
        assertTrue(order.indexOf("cursor.png") < order.indexOf("menu-back-1.png"));
        assertEquals(1, Collections.frequency(order, "menu-back-0.png"));
        assets.dispose();
    }

    @Test void newInterfaceFamiliesPreserveDensityTransparentPresenceAndDispose() throws Exception {
        for (var image : new Image[]{Image.TAB,Image.MODE_OSU_SMALL,Image.MODE_MANIA_MED,Image.MOD_HD,Image.PARTICLE})
            Files.createFile(directory.resolve(image.basename + "@2x.png"));
        List<TestTexture> owned = new ArrayList<>();
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory),file -> {
            var texture = new TestTexture(2,2); owned.add(texture); return texture;
        });
        for (var image : new Image[]{Image.TAB,Image.MODE_OSU_SMALL,Image.MODE_MANIA_MED,Image.MOD_HD,Image.PARTICLE}) {
            assertEquals("current",assets.provider(image));
            assertEquals(1,assets.get(image).logicalWidth());
        }
        assertEquals(Image.MODE_MANIA_MED,SongSelectSkinAssets.modeImage(3,2));
        assertNull(assets.get(Image.MODE_OSU));
        assets.dispose(); assets.dispose();
        for (var texture : owned) assertEquals(1,texture.disposals);
    }

    @Test void backAnimationKeepsProviderFramesDensityTimingAndOwnership() throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nAnimationFramerate: 2\n");
        for (String name : new String[]{"menu-back-0.png", "menu-back-1@2x.png", "menu-back-3.png", "menu-back.png"})
            Files.createFile(directory.resolve(name));
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(fallback.resolve("menu-back-2.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory, fallback), file -> {
            var texture = new TestTexture(100 * file.density(), 90 * file.density());
            loaded.add(texture); return texture;
        });
        assertEquals(2, assets.backFrameCount()); // Does not join a different provider or skip the hole.
        assertEquals(1, assets.backFrame(0).density());
        assertEquals(2, assets.backFrame(.5).density());
        assertEquals(100, assets.backFrame(.5).logicalWidth());
        assertSame(assets.get(Image.BACK), assets.backFrame(1));
        assertSame(assets.backFrame(.5), assets.backFrame(.5));
        assets.dispose(); assets.dispose();
        for (var texture : loaded) assertEquals(1, texture.disposals);
    }

    @ParameterizedTest @ValueSource(strings = {"selection-mode", "menu-button-background", "menu-back", "songselect-top", "songselect-bottom"})
    void authoredSurfaceDoesNotImportForeignDecorativeChrome(String basename) throws Exception {
        Files.createFile(directory.resolve(basename + ".png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory, null),
                file -> new TestTexture(file.fallback() ? 200 : 1, file.fallback() ? 90 : 1));
        for (var chrome : new Image[]{Image.TOP,Image.BOTTOM}) {
            if (chrome.basename.equals(basename)) {
                assertEquals("current",assets.provider(chrome));
                assertEquals(1,assets.get(chrome).logicalHeight());
            } else assertNull(assets.get(chrome),"An authored omission must not import bundled decoration");
        }
        if (!basename.equals("songselect-top")) assertTrue(assets.topDepth(0,100) > 1,
                "Missing decorative presence must not collapse established content reservation");
        assertNotNull(assets.get(Image.MENU_BUTTON_BACKGROUND),"Functional row fallback is independent");
        assets.dispose();
    }

    @Test void brokenChromeInAuthoredSurfaceUsesOwnedUnderlayButEmptySurfaceUsesFallback() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(fallback.resolve("songselect-top.png"));
        Files.createFile(fallback.resolve("songselect-bottom.png"));
        Files.createFile(directory.resolve("selection-mode.png"));
        Files.createFile(directory.resolve("songselect-top@2x.png"));
        var resolver = SkinAssetResolver.withBundledDefault(directory,fallback);
        var authored = new SongSelectSkinAssets(resolver,file -> {
            if (!file.fallback() && file.path().getFileName().toString().startsWith("songselect"))
                throw new GdxRuntimeException("Broken chrome");
            return new TestTexture(92,90);
        });
        assertNull(authored.get(Image.TOP)); assertNull(authored.get(Image.BOTTOM)); authored.dispose();
        var unavailable = new SongSelectSkinAssets(resolver,file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Broken current surface");
            return new TestTexture(200,90);
        });
        assertEquals("fallback",unavailable.provider(Image.TOP));
        assertEquals("fallback",unavailable.provider(Image.BOTTOM)); unavailable.dispose();
    }

    @Test void smallOrTransparentNonSurfaceGlyphDoesNotSuppressChromeFallback() throws Exception {
        Files.createFile(directory.resolve("star.png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),file -> new TestTexture(1,1));
        assertEquals("bundled",assets.provider(Image.TOP)); assertEquals("bundled",assets.provider(Image.BOTTOM));
        assets.dispose();
    }

    @Test void invisibleLayoutFallbackRetainsMetricsAndIsDisposedExactlyOnce() throws Exception {
        Files.createFile(directory.resolve("selection-mode.png"));
        var layoutTexture = new TestTexture(1366,149);
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),file ->
                file.path().getFileName().toString().startsWith("songselect-top") ? layoutTexture : new TestTexture(92,90));
        assertNull(assets.get(Image.TOP)); assertEquals("procedural",assets.provider(Image.TOP));
        assertEquals(74.5f,assets.topDepth(0,100)); // Bundled @2x loader fixture, raw 149 high.
        assertEquals(0,layoutTexture.disposals);
        assets.dispose(); assets.dispose();
        assertEquals(1,layoutTexture.disposals); assertEquals(0,assets.topDepth(0,100));
    }

    @ParameterizedTest @ValueSource(ints = {1,92})
    void currentNormalOwnsHoverFamilyEvenWhenBlankOrHoverIsCorrupt(int width) throws Exception {
        Files.createFile(directory.resolve("selection-mode.png"));
        var resolver = SkinAssetResolver.withBundledDefault(directory,null);
        var missing = new SongSelectSkinAssets(resolver,file -> new TestTexture(width,90));
        assertEquals("current",missing.provider(Image.MODE)); assertNull(missing.get(Image.MODE_OVER)); missing.dispose();
        Files.createFile(directory.resolve("selection-mode-over@2x.png"));
        Files.createFile(directory.resolve("selection-mode-over.png"));
        var corrupt = new SongSelectSkinAssets(resolver,file -> {
            if (file.path().getFileName().toString().startsWith("selection-mode-over"))
                throw new GdxRuntimeException("Corrupt hover");
            return new TestTexture(width,90);
        });
        assertNull(corrupt.get(Image.MODE_OVER)); corrupt.dispose();
        var sd = new SongSelectSkinAssets(resolver,file -> {
            if (file.path().getFileName().toString().equals("selection-mode-over@2x.png"))
                throw new GdxRuntimeException("Corrupt HD hover");
            return new TestTexture(width,90);
        });
        assertEquals("current",sd.provider(Image.MODE_OVER)); assertEquals(1,sd.get(Image.MODE_OVER).density()); sd.dispose();
    }

    @ParameterizedTest @ValueSource(strings = {"selection-mode", "selection-mode-over", "selection-mods",
            "selection-mods-over", "selection-random", "selection-random-over", "selection-options", "selection-options-over"})
    void selectionFamilyPreservesEveryProviderAndDensityPriority(String basename) throws Exception {
        Image image = Arrays.stream(Image.values()).filter(i -> i.basename.equals(basename)).findFirst().orElseThrow();
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(directory.resolve(basename + ".png"));
        Files.createFile(fallback.resolve(basename + "@2x.png"));
        var resolver = SkinAssetResolver.withBundledDefault(directory, fallback);
        var current = new SongSelectSkinAssets(resolver, f -> new TestTexture(77,90));
        assertEquals("current", current.provider(image)); assertEquals(1,current.get(image).density()); current.dispose();
        Files.createFile(directory.resolve(basename + "@2x.png"));
        var high = new SongSelectSkinAssets(resolver, f -> new TestTexture(154,180));
        assertEquals("current", high.provider(image)); assertEquals(2,high.get(image).density()); high.dispose();
        var broken = new SongSelectSkinAssets(resolver, f -> {
            if (!f.fallback()) throw new GdxRuntimeException("Malformed current PNG");
            return new TestTexture(154,180);
        });
        assertEquals("fallback",broken.provider(image)); broken.dispose();
        var bundled = new SongSelectSkinAssets(resolver, f -> {
            if (f.classpathResource() == null) throw new GdxRuntimeException("Malformed local PNG");
            return new TestTexture(154,180);
        });
        assertEquals("bundled",bundled.provider(image)); bundled.dispose();
        var missing = new SongSelectSkinAssets(new SkinAssetResolver(directory), f -> { throw new GdxRuntimeException("Malformed"); });
        assertEquals("procedural",missing.provider(image)); assertNull(missing.get(image)); missing.dispose();
    }

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
        assertEquals(2.7, fallbackConfig.configuration().legacyVersion());
        assertEquals(SkinConfiguration.SongSelect.defaults(), fallbackConfig.configuration().songSelect());
        fallbackConfig.dispose();
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: latest\n");
        var custom = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(2.7, custom.configuration().legacyVersion());
        assertEquals(SkinConfiguration.SongSelect.defaults(), custom.configuration().songSelect()); // No per-key merging.
        custom.dispose();
        Files.delete(directory.resolve("skin.ini")); Files.delete(fallback.resolve("skin.ini"));
        var bundled = new SongSelectSkinAssets(resolver, file -> new TestTexture(32, 48));
        assertEquals(2.7, bundled.configuration().legacyVersion());
        assertEquals(new SkinConfiguration.Rgb(0, 0, 0), bundled.configuration().songSelect().activeText());
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

    @ParameterizedTest @ValueSource(strings = {"songselect-top", "songselect-bottom"})
    void chromeLoadsCurrentBeforeFallbackAndBundledIncludingDecodeFailure(String basename) throws Exception {
        Image image = basename.equals("songselect-top") ? Image.TOP : Image.BOTTOM;
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(directory.resolve(basename + ".png"));
        Files.createFile(fallback.resolve(basename + "@2x.png"));
        var resolver = SkinAssetResolver.withBundledDefault(directory, fallback);
        var current = new SongSelectSkinAssets(resolver, file -> new TestTexture(1,1));
        assertEquals(directory.resolve(basename + ".png"), current.get(image).file().path());
        assertFalse(current.get(image).file().fallback());
        assertEquals(1,current.get(image).density());
        current.dispose();
        Files.createFile(directory.resolve(basename + "@2x.png"));
        var high = new SongSelectSkinAssets(resolver, file -> new TestTexture(2732,180));
        assertEquals(directory.resolve(basename + "@2x.png"), high.get(image).file().path());
        assertEquals(1366,high.get(image).logicalWidth()); high.dispose();
        var brokenHigh = new SongSelectSkinAssets(resolver, file -> {
            if (!file.fallback() && file.density() == 2) throw new GdxRuntimeException("Broken 2x");
            return new TestTexture(1,1);
        });
        assertEquals(1,brokenHigh.get(image).density()); brokenHigh.dispose();
        var fallbackAssets = new SongSelectSkinAssets(resolver, file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Broken current");
            return new TestTexture(32,48);
        });
        assertEquals(fallback.resolve(basename + "@2x.png"),fallbackAssets.get(image).file().path());
        fallbackAssets.dispose();
        var bundled = new SongSelectSkinAssets(resolver, file -> {
            if (file.classpathResource() == null) throw new GdxRuntimeException("Broken local");
            return new TestTexture(32,48);
        });
        assertEquals("skins/default/" + basename + "@2x.png",bundled.get(image).file().classpathResource());
        bundled.dispose();
        var absent = new SongSelectSkinAssets(resolver, file -> { throw new GdxRuntimeException("All broken"); });
        assertNull(absent.get(image)); absent.dispose();
    }

    @Test void customAnimatedBackFirstFrameWinsOverBundledStaticBack() throws Exception {
        Files.createFile(directory.resolve("menu-back-0.png"));
        Files.createFile(directory.resolve("menu-back-0@2x.png"));
        Files.createFile(directory.resolve("menu-back-1@2x.png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),file -> new TestTexture(544,182));
        assertEquals(directory.resolve("menu-back-0@2x.png"),assets.get(Image.BACK).file().path());
        assertEquals(2,assets.get(Image.BACK).density());
        assertFalse(assets.get(Image.BACK).file().fallback());
        assets.dispose();
        var brokenHigh = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),file -> {
            if (file.path().equals(directory.resolve("menu-back-0@2x.png"))) throw new GdxRuntimeException("Broken frame zero 2x");
            return new TestTexture(272,91);
        });
        assertEquals(directory.resolve("menu-back-0.png"),brokenHigh.get(Image.BACK).file().path());
        brokenHigh.dispose();
    }

    @Test void missingChromeUsesOwnedUnderlayWithoutReplacingCurrentActions() throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nName: osu! Default Skin Template\nVersion: 2.7\n");
        for (var image : List.of(Image.BACK, Image.RANDOM, Image.RANDOM_OVER))
            Files.createFile(directory.resolve(image.basename + "@2x.png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),
                file -> new TestTexture(148,180));
        for (var image : List.of(Image.TOP, Image.BOTTOM)) {
            assertNull(assets.get(image));
            assertEquals("procedural",assets.provider(image));
        }
        for (var image : List.of(Image.BACK, Image.RANDOM, Image.RANDOM_OVER)) {
            assertEquals(directory.resolve(image.basename + "@2x.png"),assets.get(image).file().path());
            assertFalse(assets.get(image).file().fallback());
            assertEquals(2,assets.get(image).density());
        }
        assets.dispose();
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
