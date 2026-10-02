package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/** SongSelect visuals only. Resolution and configuration belong to the shared skin resolver. */
public final class SongSelectSkinAssets implements Disposable {
    public static final float LEGACY_SELECTION_HEIGHT = 86.4f;
    public enum Image {
        MENU_BUTTON_BACKGROUND("menu-button-background"), STAR("star"),
        GRADE_SS("ranking-X-small"), GRADE_S("ranking-S-small"), GRADE_A("ranking-A-small"),
        GRADE_B("ranking-B-small"), GRADE_C("ranking-C-small"), GRADE_D("ranking-D-small"),
        TOP("songselect-top"), BOTTOM("songselect-bottom"), BACK("menu-back"),
        MODE("selection-mode"), MODE_OVER("selection-mode-over"),
        MODS("selection-mods"), MODS_OVER("selection-mods-over"),
        RANDOM("selection-random"), RANDOM_OVER("selection-random-over"),
        OPTIONS("selection-options"), OPTIONS_OVER("selection-options-over"),
        CURSOR("cursor"), CURSOR_TRAIL("cursortrail"), CURSOR_MIDDLE("cursormiddle"),
        TAB("selection-tab"), PARTICLE("star2"),
        MODE_OSU("mode-osu"), MODE_TAIKO("mode-taiko"), MODE_CATCH("mode-fruits"), MODE_MANIA("mode-mania"),
        MODE_OSU_SMALL("mode-osu-small"), MODE_TAIKO_SMALL("mode-taiko-small"), MODE_CATCH_SMALL("mode-fruits-small"), MODE_MANIA_SMALL("mode-mania-small"),
        MODE_OSU_MED("mode-osu-med"), MODE_TAIKO_MED("mode-taiko-med"), MODE_CATCH_MED("mode-fruits-med"), MODE_MANIA_MED("mode-mania-med"),
        MOD_NF("selection-mod-nofail"), MOD_EZ("selection-mod-easy"), MOD_HT("selection-mod-halftime"),
        MOD_HD("selection-mod-hidden"), MOD_HR("selection-mod-hardrock"), MOD_SD("selection-mod-suddendeath"),
        MOD_DT("selection-mod-doubletime"), MOD_FL("selection-mod-flashlight"), MOD_RX("selection-mod-relax"),
        MOD_AP("selection-mod-relax2"), MOD_SO("selection-mod-spunout"), MOD_AUTO("selection-mod-autoplay");

        public final String basename;
        Image(String basename) { this.basename = basename; }
    }

    public static Image modeImage(int mode, int size) {
        int index = mode >= 0 && mode < 4 ? mode : 0;
        return Image.values()[(size == 0 ? Image.MODE_OSU : size == 1 ? Image.MODE_OSU_SMALL : Image.MODE_OSU_MED).ordinal() + index];
    }

    public record SkinTexture(Texture texture, SkinAssetResolver.AssetFile file) {
        public int density() { return file.density(); }
        // Stable 06000739/073a divide integer texture dimensions by integer density.
        // Keep this Song Select contract separate from other screens' sizing policies.
        public float logicalWidth() { return texture.getWidth() / density(); }
        public float logicalHeight() { return texture.getHeight() / density(); }
        // 060040af builds an integer logical crop; 060040b1 multiplies it by density.
        public float cropU2() { return logicalWidth() * density() / texture.getWidth(); }
        public float cropV2() { return logicalHeight() * density() / texture.getHeight(); }
    }

    /** Official selection action family. Widths describe control canvases, never composite PNGs. */
    public enum Selection {
        MODE(Image.MODE, Image.MODE_OVER, 92.16f), MODS(Image.MODS, Image.MODS_OVER, 76.8f),
        RANDOM(Image.RANDOM, Image.RANDOM_OVER, 76.8f), OPTIONS(Image.OPTIONS, Image.OPTIONS_OVER, 76.8f);
        public final Image normal, hover;
        public final float logicalWidth;
        Selection(Image normal, Image hover, float logicalWidth) {
            this.normal = normal; this.hover = hover; this.logicalWidth = logicalWidth;
        }
        public static Selection of(Image image) {
            for (var action : values()) if (action.normal == image || action.hover == image) return action;
            return null;
        }
    }

    private final EnumMap<Image, SelectionAssetBounds> selectionBounds = new EnumMap<>(Image.class);
    private final EnumMap<Image, SkinTexture> textures = new EnumMap<>(Image.class);
    private final Set<Texture> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private Texture fallbackStar;
    private final Set<Image> generatedModes = java.util.EnumSet.noneOf(Image.class);
    private List<SkinTexture> backFrames = List.of();
    private SongSelectTopCoverage topCoverage;
    private SongSelectBodyBounds rowBody = SongSelectBodyBounds.FULL;
    private SkinConfiguration configuration = SkinConfiguration.defaults();
    private final SkinAssetResolver resolver;
    public SkinAssetResolver resolver() { return resolver; }

    public SongSelectSkinAssets(Path directory, Path fallbackDirectory) {
        this(SkinAssetResolver.withBundledDefault(directory, fallbackDirectory));
    }

    public SongSelectSkinAssets(SkinAssetResolver resolver) {
        this(resolver, new Function<>() {
            private long pixels;
            @Override public Texture apply(SkinAssetResolver.AssetFile file) {
                long next = SongSelectImageLimits.check(file.handle());
                // Aggregate budget before native allocation, including all animation frames.
                if (pixels + next > 64L * 1024 * 1024)
                    throw new GdxRuntimeException("Song Select textures exceed 64 MiPixel budget");
                Texture result = new Texture(file.handle());
                pixels += next;
                return result;
            }
        });
    }

    /** Injectable loader for ownership/failure tests without OpenGL. All loads finish here. */
    SongSelectSkinAssets(SkinAssetResolver resolver, Function<SkinAssetResolver.AssetFile, Texture> loader) {
        this.resolver = resolver;
        try { configuration = resolver.readSelectedConfiguration(); }
        catch (IOException e) { log("Could not read skin.ini", e); }
        Predicate<SkinAssetResolver.AssetFile> backLoader = null;
        for (Image image : Image.values()) {
            Predicate<SkinAssetResolver.AssetFile> load = file -> {
                Texture texture = null;
                try {
                    texture = loader.apply(file);
                    if (texture == null) return false;
                    if (texture.getWidth() <= 0 || texture.getHeight() <= 0)
                        throw new IllegalArgumentException("Empty skin texture");
                    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    textures.put(image, new SkinTexture(texture, file));
                    owned.add(texture);
                    if ((image == Image.TOP || image == Image.BOTTOM || Selection.of(image) != null) && Gdx.app != null)
                        Gdx.app.log("SongSelect skin", image.basename + " loaded: " + file.path()
                                + " provider=" + (file.classpathResource() != null ? "bundled" : file.fallback() ? "fallback" : "current")
                                + " density=" + file.density() + " logical=" + file.logicalSize(texture.getWidth())
                                + "x" + file.logicalSize(texture.getHeight()));
                    return true;
                } catch (GdxRuntimeException | IllegalArgumentException e) {
                    if (texture != null && !owned.contains(texture)) texture.dispose();
                    log("Could not load " + file.path() + "; trying the next skin candidate", e);
                    return false;
                }
            };
            if (image == Image.BACK) {
                backLoader = load;
                resolver.resolveAnimationFirstFrame(image.basename, load);
            } else if (image == Image.CURSOR_MIDDLE) {
                // Stable 06002ac0: middle follows the resolved cursor; trail resolves independently.
                var cursor = get(Image.CURSOR);
                if (cursor != null) resolver.resolveFromProvider(image.basename, cursor.file().provider(), load);
            } else {
                // Stable 0600136e resolves normal, hover, top and bottom independently.
                resolver.resolve(image.basename, load);
            }
        }
        // Selection geometry depends on the resolved Mods provider, which may load after Mode.
        for (var entry : textures.entrySet()) measureGeometry(entry.getKey(), entry.getValue());
        // Reserve all static interface images (including fallback chrome) before extra Back frames.
        var first = textures.get(Image.BACK);
        if (first != null) {
            var firstBounds = selectionBounds.get(Image.BACK);
            var frames = new ArrayList<SkinTexture>();
            var load = backLoader;
            resolver.resolveAnimation(Image.BACK.basename, 512, file -> {
                if (file.equals(first.file())) { frames.add(first); return true; }
                if (frames.isEmpty()) return false;
                if (!load.test(file)) return false;
                frames.add(textures.get(Image.BACK));
                return true;
            });
            backFrames = List.copyOf(frames);
            textures.put(Image.BACK, first);
            if (firstBounds != null) selectionBounds.put(Image.BACK, firstBounds);
        }

    }

    private void measureGeometry(Image image, SkinTexture asset) {
        if (Gdx.gl == null || (image != Image.MENU_BUTTON_BACKGROUND && image != Image.BACK
                && Selection.of(image) == null && image != Image.TOP)) return;
        Pixmap pixels = null;
        try {
            pixels = new Pixmap(asset.file().handle());
            Pixmap source = pixels;
            if (image == Image.TOP) {
                topCoverage = SongSelectTopCoverage.detect(pixels.getWidth(), pixels.getHeight(),
                        asset.density(), (x,y) -> source.getPixel(x,y) & 255);
            } else if (image == Image.BACK) {
                selectionBounds.put(image, SelectionAssetBounds.detect(pixels.getWidth(), pixels.getHeight(),
                        asset.density(), 224, 90, false, (x,y) -> source.getPixel(x,y) & 255));
            } else if (Selection.of(image) != null) {
                var action = Selection.of(image);
                boolean legacy = legacySelectionAnchors();
                selectionBounds.put(image, SelectionAssetBounds.detect(pixels.getWidth(), pixels.getHeight(),
                        asset.density(), action.logicalWidth, legacy ? LEGACY_SELECTION_HEIGHT : 90, legacy,
                        (x, y) -> source.getPixel(x, y) & 255));
            } else {
                var body = SongSelectBodyBounds.detect(pixels.getWidth(), pixels.getHeight(),
                        (x, y) -> source.getPixel(x, y) & 255);
                rowBody = body;
            }
        } catch (GdxRuntimeException ignored) { /* Full-image bounds remain the safe default. */ }
        finally { if (pixels != null) pixels.dispose(); }
    }

    private static void log(String message, Exception error) {
        if (Gdx.app != null) Gdx.app.log("SongSelect skin", message, error);
    }

    public SkinTexture get(Image image) { return textures.get(image); }
    public int backFrameCount() { return backFrames.size(); }
    public SkinTexture backFrame(double elapsedSeconds) {
        return backFrames.isEmpty() ? null : backFrames.get(SkinAnimation.frameIndex(
                backFrames.size(), configuration.animationFramerate(), elapsedSeconds));
    }
    public SelectionAssetBounds selectionBounds(Image image) { return selectionBounds.get(image); }
    public String provider(Image image) {
        if (generatedModes.contains(image)) return "generated";
        var asset = get(image);
        return asset == null ? "procedural" : asset.file().classpathResource() != null ? "bundled"
                : asset.file().fallback() ? "fallback" : "current";
    }
    /** Layout and rendering use the same resolved top artwork. */
    public String topLayoutProvider() {
        var asset = get(Image.TOP);
        return asset == null ? "owned-minimum" : asset.file().classpathResource() != null ? "bundled"
                : asset.file().fallback() ? "fallback" : "current";
    }
    public float topDepth(float start, float end) {
        var top = get(Image.TOP);
        return top == null ? 0 : topCoverage == null ? top.logicalHeight() : topCoverage.depth(start,end);
    }
    public SongSelectBodyBounds rowBody() { return rowBody; }
    /** Stable 0600136e also uses new anchors when the resolved Mods normal is built-in. */
    public boolean legacySelectionAnchors() {
        var mods = get(Image.MODS);
        return configuration.legacyVersion() <= 1
                && (mods == null || mods.file().provider() != SkinAssetResolver.Provider.BUNDLED);
    }
    public boolean thumbnailsEnabled() { return configuration.legacyVersion() >= 2.2; }
    public SkinConfiguration configuration() { return configuration; }

    /** Resolver candidates always win; this small UI glyph is created once if all are absent/corrupt. */
    public Texture starTexture() {
        SkinTexture asset = get(Image.STAR);
        if (asset != null) return asset.texture();
        return fallbackStar;
    }

    public void prepareModeFallbacks() { prepareModeFallbacks(SongSelectModeGlyphs::texture); }

    void prepareModeFallbacks(java.util.function.BiFunction<Integer, Integer, Texture> factory) {
        for (int size = 0; size < 3; size++) for (int mode = 0; mode < 4; mode++) {
            Image image = modeImage(mode, size);
            var asset = get(image);
            // A local author's transparent replacement is intentional. Only our bundled
            // 1px placeholders and absent families receive a generated default.
            if (asset != null && (asset.file().classpathResource() == null
                    || asset.texture().getWidth() != 1 || asset.texture().getHeight() != 1)) continue;
            Texture texture = factory.apply(mode, size == 0 ? 256 : size == 1 ? 32 : 128);
            if (texture == null) continue;
            textures.put(image, new SkinTexture(texture,
                    new SkinAssetResolver.AssetFile(Path.of("generated", image.basename + ".png"), 1, true)));
            owned.add(texture);
            generatedModes.add(image);
        }
    }

    public void prepareStarFallback() {
        if (get(Image.STAR) != null || fallbackStar != null) return;
        Pixmap pixels = new Pixmap(40, 40, Pixmap.Format.RGBA8888);
        pixels.setColor(1, 1, 1, 1);
        int[] x = new int[10], y = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double radius = i % 2 == 0 ? 18 : 8;
            x[i] = 20 + (int) Math.round(Math.cos(angle) * radius);
            y[i] = 20 + (int) Math.round(Math.sin(angle) * radius);
        }
        for (int i = 0; i < 10; i++) pixels.fillTriangle(20, 20, x[i], y[i], x[(i + 1) % 10], y[(i + 1) % 10]);
        fallbackStar = new Texture(pixels);
        pixels.dispose();
        fallbackStar.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        owned.add(fallbackStar);
    }

    @Override public void dispose() {
        for (Texture texture : owned) texture.dispose();
        owned.clear();
        textures.clear();
        generatedModes.clear();
        selectionBounds.clear();
        fallbackStar = null;
        backFrames = List.of();
        topCoverage = null;
    }
}
