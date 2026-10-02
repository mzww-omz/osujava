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

    @Test void logicalDimensionsTruncateOddHdPixelsAndKeepTransparentAssetsPresent() throws Exception {
        Files.createFile(directory.resolve("selection-mode@2x.png"));
        Files.createFile(directory.resolve("menu-back@2x.png"));
        Files.createFile(directory.resolve("songselect-top@2x.png"));
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> {
            String name = file.path().getFileName().toString();
            return name.startsWith("selection-mode") ? new TestTexture(185,181)
                    : name.startsWith("menu-back") ? new TestTexture(545,183) : new TestTexture(1,1);
        });
        assertEquals(92,assets.get(Image.MODE).logicalWidth());
        assertEquals(90,assets.get(Image.MODE).logicalHeight());
        assertEquals(184f/185,assets.get(Image.MODE).cropU2());
        assertEquals(180f/181,assets.get(Image.MODE).cropV2());
        assertEquals(272,assets.get(Image.BACK).logicalWidth());
        assertEquals(91,assets.get(Image.BACK).logicalHeight());
        assertNotNull(assets.get(Image.TOP));
        assertEquals(0,assets.get(Image.TOP).logicalWidth());
        assertEquals(0,assets.get(Image.TOP).logicalHeight());
        assertEquals(0,assets.get(Image.TOP).cropU2());
        assertEquals(0,assets.get(Image.TOP).cropV2());
        // The shared file helper is deliberately not changed by the screen contract.
        assertEquals(92.5f,assets.get(Image.MODE).file().logicalSize(185));
        assets.dispose();
    }

    @ParameterizedTest @ValueSource(doubles = {1, 1.1, 1.5, 2, 2.2, 2.7})
    void selectionAnchorVersionGateIsIndependentOfThumbnails(double version) throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: " + version);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> new TestTexture(1, 1));
        assertEquals(version <= 1, assets.legacySelectionAnchors());
        assertEquals(version >= 2.2, assets.thumbnailsEnabled());
        assets.dispose();
    }

    @Test void versionOneAnchorsFollowModsNormalProviderNotOtherImagesOrFallbackIni() throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nVersion: 1");
        Files.createFile(directory.resolve("selection-mode.png"));
        Files.createFile(directory.resolve("selection-mods-over.png"));
        var bundled = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory, null),
                file -> new TestTexture(92, 90));
        assertEquals("bundled", bundled.provider(Image.MODS));
        assertEquals("current", bundled.provider(Image.MODS_OVER));
        assertFalse(bundled.legacySelectionAnchors());
        assertFalse(bundled.thumbnailsEnabled());
        assertEquals(1, bundled.configuration().legacyVersion());
        bundled.dispose();

        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.writeString(fallback.resolve("skin.ini"), "[General]\nVersion: 2.7");
        Files.createFile(fallback.resolve("selection-mods.png"));
        var local = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory, fallback),
                file -> new TestTexture(92, 90));
        assertEquals("fallback", local.provider(Image.MODS));
        assertTrue(local.legacySelectionAnchors());
        local.dispose();

        Files.createFile(directory.resolve("selection-mods.png"));
        var custom = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory, null),
                file -> new TestTexture(1, 1));
        assertEquals("current", custom.provider(Image.MODS));
        assertTrue(custom.legacySelectionAnchors());
        custom.dispose();
    }

    @Test void middleUsesCursorProviderWhileTrailCanComeFromAnotherSkin() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(directory.resolve("cursor.png"));
        Files.createFile(fallback.resolve("cursortrail.png"));
        Files.createFile(fallback.resolve("cursormiddle.png"));
        var resolver = SkinAssetResolver.withBundledDefault(directory, fallback);
        var currentCursor = new SongSelectSkinAssets(resolver, file -> new TestTexture(40,40));
        assertEquals("current", currentCursor.provider(Image.CURSOR));
        assertEquals("fallback", currentCursor.provider(Image.CURSOR_TRAIL));
        assertNull(currentCursor.get(Image.CURSOR_MIDDLE));
        currentCursor.dispose();

        Files.createFile(directory.resolve("cursormiddle@2x.png"));
        var withMiddle = new SongSelectSkinAssets(resolver, file -> new TestTexture(40,40));
        assertEquals("current", withMiddle.provider(Image.CURSOR_MIDDLE));
        assertEquals(2, withMiddle.get(Image.CURSOR_MIDDLE).density());
        withMiddle.dispose();

        Files.delete(directory.resolve("cursor.png"));
        Files.createFile(fallback.resolve("cursor.png"));
        Files.createFile(directory.resolve("cursortrail.png"));
        var fallbackCursor = new SongSelectSkinAssets(resolver, file -> new TestTexture(40,40));
        assertEquals("fallback", fallbackCursor.provider(Image.CURSOR));
        assertEquals("current", fallbackCursor.provider(Image.CURSOR_TRAIL));
        assertEquals("fallback", fallbackCursor.provider(Image.CURSOR_MIDDLE));
        fallbackCursor.dispose();
    }

    @Test void rejectedCursorMiddleNeverSwitchesToAnotherProvider() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        for (String name : new String[]{"cursor", "cursormiddle"}) {
            Files.createFile(directory.resolve(name + ".png"));
            Files.createFile(fallback.resolve(name + ".png"));
        }
        var loaded = new ArrayList<SkinAssetResolver.AssetFile>();
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory, fallback), file -> {
            loaded.add(file);
            if (file.path().getFileName().toString().startsWith("cursormiddle"))
                throw new GdxRuntimeException("Rejected middle");
            return new TestTexture(40,40);
        });
        assertNotNull(assets.get(Image.CURSOR));
        assertNull(assets.get(Image.CURSOR_MIDDLE));
        assertFalse(loaded.stream().anyMatch(SkinAssetResolver.AssetFile::fallback));
        assets.dispose();
    }

    @Test void middleIsNotLoadedWithoutACursor() throws Exception {
        Files.createFile(directory.resolve("cursormiddle.png"));
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> {
            fail("A middle without its cursor must not load"); return null;
        });
        assertNull(assets.get(Image.CURSOR_MIDDLE));
        assets.dispose();
    }

    @Test void missingModeFamiliesAndBundledPlaceholdersGetVisibleDefaultsButCustomTransparencyWins() throws Exception {
        Files.createFile(directory.resolve("mode-osu-small.png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory, null),
                file -> new TestTexture(1, 1));
        var authored = assets.get(Image.MODE_OSU_SMALL);
        var created = new ArrayList<TestTexture>();
        java.util.function.BiFunction<Integer, Integer, Texture> factory = (mode, size) -> {
            var texture = new TestTexture(size, size); created.add(texture); return texture;
        };
        assets.prepareModeFallbacks(factory); assets.prepareModeFallbacks(factory);
        assertSame(authored, assets.get(Image.MODE_OSU_SMALL));
        assertEquals("current", assets.provider(Image.MODE_OSU_SMALL));
        assertEquals(11, created.size());
        for (int mode = 0; mode < 4; mode++) for (int size = 0; size < 3; size++) {
            var image = SongSelectSkinAssets.modeImage(mode, size);
            assertNotNull(assets.get(image));
            if (image != Image.MODE_OSU_SMALL) assertEquals("generated", assets.provider(image));
        }
        assets.dispose(); assets.dispose();
        for (var texture : created) assertEquals(1, texture.disposals);
    }

    @Test void generatedModeSymbolsAreDistinctVisibleAndKeepTransparentMargins() {
        var signatures = new HashSet<Integer>();
        for (int mode = 0; mode < 4; mode++) {
            int hash = 1, visible = 0;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int alpha = SongSelectModeGlyphs.pixel(mode, x, y, 32) & 255;
                hash = 31 * hash + alpha;
                if (alpha > 0) visible++;
                if (x == 0 || y == 0 || x == 31 || y == 31) assertEquals(0, alpha);
            }
            assertTrue(visible > 80); assertTrue(visible < 800); signatures.add(hash);
        }
        assertEquals(4, signatures.size());
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
    void partialSurfaceResolvesMissingChromeIndependently(String basename) throws Exception {
        Files.createFile(directory.resolve(basename + ".png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory, null),
                file -> new TestTexture(file.fallback() ? 200 : 1, file.fallback() ? 90 : 1));
        for (var chrome : new Image[]{Image.TOP,Image.BOTTOM}) {
            if (chrome.basename.equals(basename)) {
                assertEquals("current",assets.provider(chrome));
                assertEquals(1,assets.get(chrome).logicalHeight());
            } else assertEquals("bundled", assets.provider(chrome));
        }
        if (!basename.equals("songselect-top")) assertTrue(assets.topDepth(0,100) > 1,
                "Visible fallback chrome supplies its own layout metrics");
        assertNotNull(assets.get(Image.MENU_BUTTON_BACKGROUND),"Functional row fallback is independent");
        assets.dispose();
    }

    @Test void brokenPresentChromeStopsWhileMissingChromeUsesFallback() throws Exception {
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
        assertNull(authored.get(Image.TOP));
        assertEquals("fallback", authored.provider(Image.BOTTOM)); authored.dispose();
        var unavailable = new SongSelectSkinAssets(resolver,file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Broken current surface");
            return new TestTexture(200,90);
        });
        assertNull(unavailable.get(Image.TOP));
        assertEquals("fallback",unavailable.provider(Image.BOTTOM)); unavailable.dispose();
    }

    @Test void smallOrTransparentNonSurfaceGlyphDoesNotSuppressChromeFallback() throws Exception {
        Files.createFile(directory.resolve("star.png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),file -> new TestTexture(1,1));
        assertEquals("bundled",assets.provider(Image.TOP)); assertEquals("bundled",assets.provider(Image.BOTTOM));
        assets.dispose();
    }

    @Test void visibleTopSuppliesLayoutMetricsAndIsDisposedExactlyOnce() throws Exception {
        Files.createFile(directory.resolve("selection-mode.png"));
        var layoutTexture = new TestTexture(1366,149);
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),file ->
                file.path().getFileName().toString().startsWith("songselect-top") ? layoutTexture : new TestTexture(92,90));
        assertSame(layoutTexture, assets.get(Image.TOP).texture());
        assertEquals("bundled",assets.provider(Image.TOP));
        assertEquals(assets.provider(Image.TOP), assets.topLayoutProvider());
        assertEquals(74f,assets.topDepth(0,100)); // Bundled @2x loader fixture, raw 149 high.
        assertEquals(0,layoutTexture.disposals);
        assets.dispose(); assets.dispose();
        assertEquals(1,layoutTexture.disposals); assertEquals(0,assets.topDepth(0,100));
    }

    @ParameterizedTest @ValueSource(ints = {1,92})
    void hoverResolvesIndependentlyOfCurrentNormalIncludingBlankImages(int width) throws Exception {
        Files.createFile(directory.resolve("selection-mode.png"));
        var resolver = SkinAssetResolver.withBundledDefault(directory,null);
        var missing = new SongSelectSkinAssets(resolver,file -> new TestTexture(width,90));
        assertEquals("current",missing.provider(Image.MODE));
        assertEquals("bundled",missing.provider(Image.MODE_OVER)); missing.dispose();
        Files.createFile(directory.resolve("selection-mode-over@2x.png"));
        Files.createFile(directory.resolve("selection-mode-over.png"));
        var corrupt = new SongSelectSkinAssets(resolver,file -> {
            if (!file.fallback() && file.path().getFileName().toString().startsWith("selection-mode-over"))
                throw new GdxRuntimeException("Corrupt hover");
            return new TestTexture(width,90);
        });
        assertNull(corrupt.get(Image.MODE_OVER)); corrupt.dispose();
        var sd = new SongSelectSkinAssets(resolver,file -> {
            if (file.path().getFileName().toString().equals("selection-mode-over@2x.png"))
                throw new GdxRuntimeException("Corrupt HD hover");
            return new TestTexture(width,90);
        });
        assertNull(sd.get(Image.MODE_OVER)); sd.dispose();
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
        assertNull(broken.get(image)); broken.dispose();
        Files.delete(directory.resolve(basename + ".png"));
        Files.delete(directory.resolve(basename + "@2x.png"));
        var bundled = new SongSelectSkinAssets(resolver, f -> {
            if (f.classpathResource() == null) throw new GdxRuntimeException("Malformed local PNG");
            return new TestTexture(154,180);
        });
        assertNull(bundled.get(image)); bundled.dispose();
        Files.delete(fallback.resolve(basename + "@2x.png"));
        var absentLocals = new SongSelectSkinAssets(resolver,f -> new TestTexture(154,180));
        assertEquals("bundled",absentLocals.provider(image)); absentLocals.dispose();
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

    @Test void providerPriorityPrecedesDensityAndPresentFailuresStop() throws Exception {
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
        assertNull(brokenHigh.get(Image.MENU_BUTTON_BACKGROUND)); brokenHigh.dispose();
        var brokenCustom = new SongSelectSkinAssets(resolver, file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Unreadable custom PNG");
            return new TestTexture(32, 48);
        });
        assertNull(brokenCustom.get(Image.MENU_BUTTON_BACKGROUND));
        brokenCustom.dispose();
        var bundled = new SongSelectSkinAssets(resolver, file -> {
            if (file.classpathResource() == null) throw new GdxRuntimeException("Unreadable local PNG");
            return new TestTexture(32, 48);
        });
        assertNull(bundled.get(Image.MENU_BUTTON_BACKGROUND));
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

    @Test void failedInitializationDisposesFailedTextureWithoutLoadingSd() throws Exception {
        Files.createFile(directory.resolve("songselect-top@2x.png"));
        Files.createFile(directory.resolve("songselect-top.png"));
        var failed = new TestTexture(32, 48) {
            @Override public void setFilter(TextureFilter min, TextureFilter mag) { throw new GdxRuntimeException("Cannot set filter"); }
        };
        var good = new TestTexture(32, 48);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory), file -> file.density() == 2 ? failed : good);
        assertNull(assets.get(Image.TOP));
        assertEquals(1, failed.disposals); assertEquals(0, good.disposals);
        assets.dispose(); assets.dispose();
        assertEquals(1, failed.disposals); assertEquals(0, good.disposals);
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

    @Test void malformedPresentStarDoesNotLoadSdOrOtherProviders() throws Exception {
        Path fallback = Files.createDirectory(directory.resolve("fallback"));
        Files.createFile(directory.resolve("star@2x.png"));
        Files.createFile(directory.resolve("star.png"));
        Files.createFile(fallback.resolve("star@2x.png"));
        var good = new TestTexture(40, 40);
        var assets = new SongSelectSkinAssets(new SkinAssetResolver(directory, fallback), file -> {
            if (file.density() == 2 && !file.fallback()) throw new GdxRuntimeException("Corrupt star");
            return good;
        });
        assertNull(assets.get(Image.STAR));
        assertNull(assets.starTexture()); assets.dispose(); assertEquals(0,good.disposals);
        var fallbackAssets = new SongSelectSkinAssets(new SkinAssetResolver(directory, fallback), file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Corrupt star");
            return new TestTexture(40, 40);
        });
        assertNull(fallbackAssets.get(Image.STAR)); fallbackAssets.dispose();
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
        assertNull(broken.get(Image.GRADE_SS)); broken.dispose();
        Files.delete(directory.resolve("ranking-X-small.png"));
        Files.delete(fallback.resolve("ranking-X-small@2x.png"));
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
        assertNull(brokenHigh.get(image)); brokenHigh.dispose();
        var fallbackAssets = new SongSelectSkinAssets(resolver, file -> {
            if (!file.fallback()) throw new GdxRuntimeException("Broken current");
            return new TestTexture(32,48);
        });
        assertNull(fallbackAssets.get(image));
        fallbackAssets.dispose();
        Files.delete(directory.resolve(basename + "@2x.png"));
        Files.delete(directory.resolve(basename + ".png"));
        var missingCurrent = new SongSelectSkinAssets(resolver,file -> new TestTexture(32,48));
        assertEquals(fallback.resolve(basename + "@2x.png"),missingCurrent.get(image).file().path());
        missingCurrent.dispose();
        Files.delete(fallback.resolve(basename + "@2x.png"));
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

    @Test void missingChromeFallsBackWithoutReplacingCurrentActions() throws Exception {
        Files.writeString(directory.resolve("skin.ini"), "[General]\nName: osu! Default Skin Template\nVersion: 2.7\n");
        for (var image : List.of(Image.BACK, Image.RANDOM, Image.RANDOM_OVER))
            Files.createFile(directory.resolve(image.basename + "@2x.png"));
        var assets = new SongSelectSkinAssets(SkinAssetResolver.withBundledDefault(directory,null),
                file -> new TestTexture(148,180));
        for (var image : List.of(Image.TOP, Image.BOTTOM)) {
            assertNotNull(assets.get(image));
            assertEquals("bundled",assets.provider(image));
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
