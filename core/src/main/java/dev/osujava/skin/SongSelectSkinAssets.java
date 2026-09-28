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
    public enum Image {
        MENU_BUTTON_BACKGROUND("menu-button-background"), STAR("star"),
        GRADE_SS("ranking-X-small"), GRADE_S("ranking-S-small"), GRADE_A("ranking-A-small"),
        GRADE_B("ranking-B-small"), GRADE_C("ranking-C-small"), GRADE_D("ranking-D-small"),
        TOP("songselect-top"), BOTTOM("songselect-bottom"), BACK("menu-back"),
        MODE("selection-mode"), MODE_OVER("selection-mode-over"),
        MODS("selection-mods"), MODS_OVER("selection-mods-over"),
        RANDOM("selection-random"), RANDOM_OVER("selection-random-over"),
        OPTIONS("selection-options"), OPTIONS_OVER("selection-options-over");

        public final String basename;
        Image(String basename) { this.basename = basename; }
    }

    public record SkinTexture(Texture texture, SkinAssetResolver.AssetFile file) {
        public int density() { return file.density(); }
        public float logicalWidth() { return file.logicalSize(texture.getWidth()); }
        public float logicalHeight() { return file.logicalSize(texture.getHeight()); }
    }

    /** Official selection action family. Widths describe control canvases, never composite PNGs. */
    public enum Selection {
        MODE(Image.MODE, Image.MODE_OVER, 92), MODS(Image.MODS, Image.MODS_OVER, 77),
        RANDOM(Image.RANDOM, Image.RANDOM_OVER, 77), OPTIONS(Image.OPTIONS, Image.OPTIONS_OVER, 77);
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
    private List<SkinTexture> backFrames = List.of();
    private SongSelectTopCoverage topCoverage;
    private SkinTexture topLayoutFallback;
    private SongSelectBodyBounds rowBody = SongSelectBodyBounds.FULL;
    private SkinConfiguration configuration = SkinConfiguration.defaults();

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
        try { configuration = resolver.readSelectedConfiguration(); }
        catch (IOException e) { log("Could not read skin.ini", e); }
        var chromeLoaders = new EnumMap<Image, Predicate<SkinAssetResolver.AssetFile>>(Image.class);
        for (Image image : Image.values()) {
            var pair = Selection.of(image);
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
                    if ((image == Image.MENU_BUTTON_BACKGROUND || image == Image.BACK
                            || Selection.of(image) != null || image == Image.TOP) && Gdx.gl != null) {
                        Pixmap pixels = null;
                        try {
                            pixels = new Pixmap(file.handle());
                            Pixmap source = pixels;
                            if (image == Image.TOP) {
                                topCoverage = SongSelectTopCoverage.detect(pixels.getWidth(), pixels.getHeight(),
                                        file.density(), (x,y) -> source.getPixel(x,y) & 255);
                            } else if (image == Image.BACK) {
                                selectionBounds.put(image, SelectionAssetBounds.detect(pixels.getWidth(), pixels.getHeight(),
                                        file.density(), 224, 90, false, (x,y) -> source.getPixel(x,y) & 255));
                            } else if (Selection.of(image) != null) {
                                var action = Selection.of(image);
                                boolean legacy = configuration.legacyVersion() < 2;
                                selectionBounds.put(image, SelectionAssetBounds.detect(pixels.getWidth(), pixels.getHeight(),
                                        file.density(), action.logicalWidth, legacy ? 87 : 90, legacy,
                                        (x, y) -> source.getPixel(x, y) & 255));
                            } else {
                                var body = SongSelectBodyBounds.detect(pixels.getWidth(), pixels.getHeight(),
                                        (x, y) -> source.getPixel(x, y) & 255);
                                rowBody = body;
                            }
                        } catch (GdxRuntimeException ignored) { /* Full-image bounds remain the safe default. */ }
                        finally { if (pixels != null) pixels.dispose(); }
                    }
                    return true;
                } catch (GdxRuntimeException | IllegalArgumentException e) {
                    if (texture != null && !owned.contains(texture)) texture.dispose();
                    log("Could not load " + file.path() + "; trying the next skin candidate", e);
                    return false;
                }
            };
            if (image == Image.TOP || image == Image.BOTTOM) {
                // Probe BOTH current chrome assets before deciding whether the surface needs a default.
                // A valid transparent replacement is authored presence, just like other current art.
                chromeLoaders.put(image, load);
                resolver.resolveCustom(image.basename, load);
            } else if (pair != null && image == pair.hover
                    && get(pair.normal) != null && !get(pair.normal).file().fallback()) {
                // An authored normal (including a transparent replacement) owns its hover family.
                // Missing current hover retains normal artwork, rather than inventing foreign art/input.
                resolver.resolveCustom(image.basename, load);
            } else if (image == Image.BACK) {
                List<SkinTexture> frames = new ArrayList<>();
                resolver.resolveAnimation(image.basename, 512, file -> {
                    // Keep frame-zero geometry; changing frames must not move the hit target.
                    var firstBounds = selectionBounds.get(Image.BACK);
                    if (!load.test(file)) return false;
                    frames.add(textures.get(Image.BACK));
                    if (frames.size() > 1 && firstBounds != null) selectionBounds.put(Image.BACK, firstBounds);
                    return true;
                });
                backFrames = List.copyOf(frames);
                if (!backFrames.isEmpty()) textures.put(Image.BACK, backFrames.getFirst());
            }
            else resolver.resolve(image.basename, load);
        }
        boolean authoredSurface = textures.entrySet().stream().anyMatch(entry ->
                surfaceImage(entry.getKey()) && !entry.getValue().file().fallback());
        // Decorative omissions in an authored browser surface use owned neutral UI underneath it.
        // An empty/unloadable surface still uses the configured fallback and bundled default.
        if (!authoredSurface) for (var image : new Image[]{Image.TOP, Image.BOTTOM})
            if (!textures.containsKey(image)) resolver.resolve(image.basename, chromeLoaders.get(image));
        if (authoredSurface && !textures.containsKey(Image.TOP)) {
            // Preserve the established content reservation independently of decorative presence.
            // Dropping fallback layout metrics would move rows/rankings into native composite art.
            // This texture remains owned/disposed, but is never returned as rendering artwork.
            resolver.resolve(Image.TOP.basename, chromeLoaders.get(Image.TOP));
            topLayoutFallback = textures.remove(Image.TOP);
        }
    }

    private static boolean surfaceImage(Image image) {
        return image == Image.TOP || image == Image.BOTTOM || image == Image.MENU_BUTTON_BACKGROUND
                || image == Image.BACK || Selection.of(image) != null;
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
        var asset = get(image);
        return asset == null ? "procedural" : asset.file().classpathResource() != null ? "bundled"
                : asset.file().fallback() ? "fallback" : "current";
    }
    /** Diagnostic provenance of content reservation; this does not imply visible chrome. */
    public String topLayoutProvider() {
        var asset = get(Image.TOP) == null ? topLayoutFallback : get(Image.TOP);
        return asset == null ? "owned-minimum" : asset.file().classpathResource() != null ? "bundled"
                : asset.file().fallback() ? "fallback" : "current";
    }
    public float topDepth(float start, float end) {
        var top = get(Image.TOP);
        if (top == null) top = topLayoutFallback;
        return top == null ? 0 : topCoverage == null ? top.logicalHeight() : topCoverage.depth(start,end);
    }
    public SongSelectBodyBounds rowBody() { return rowBody; }
    public boolean thumbnailsEnabled() { return configuration.legacyVersion() >= 2.2; }
    public SkinConfiguration configuration() { return configuration; }

    /** Resolver candidates always win; this small UI glyph is created once if all are absent/corrupt. */
    public Texture starTexture() {
        SkinTexture asset = get(Image.STAR);
        if (asset != null) return asset.texture();
        return fallbackStar;
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
        selectionBounds.clear();
        fallbackStar = null;
        backFrames = List.of();
        topCoverage = null;
        topLayoutFallback = null;
    }
}
