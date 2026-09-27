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

    private final EnumMap<Image, SongSelectBodyBounds> actionBodies = new EnumMap<>(Image.class);
    private final EnumMap<Image, SkinTexture> textures = new EnumMap<>(Image.class);
    private final Set<Texture> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private Texture fallbackStar;
    private SongSelectTopCoverage topCoverage;
    private SongSelectBodyBounds rowBody = SongSelectBodyBounds.FULL;
    private SkinConfiguration configuration = SkinConfiguration.defaults();

    public SongSelectSkinAssets(Path directory, Path fallbackDirectory) {
        this(SkinAssetResolver.withBundledDefault(directory, fallbackDirectory));
    }

    public SongSelectSkinAssets(SkinAssetResolver resolver) {
        this(resolver, file -> new Texture(file.handle()));
    }

    /** Injectable loader for ownership/failure tests without OpenGL. All loads finish here. */
    SongSelectSkinAssets(SkinAssetResolver resolver, Function<SkinAssetResolver.AssetFile, Texture> loader) {
        try { configuration = resolver.readConfiguration(); }
        catch (IOException e) { log("Could not read skin.ini", e); }
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
                    if ((image == Image.TOP || image == Image.BOTTOM) && Gdx.app != null)
                        Gdx.app.log("SongSelect skin", image.basename + " loaded: " + file.path()
                                + " provider=" + (file.classpathResource() != null ? "bundled" : file.fallback() ? "fallback" : "current")
                                + " density=" + file.density() + " logical=" + file.logicalSize(texture.getWidth())
                                + "x" + file.logicalSize(texture.getHeight()));
                    if ((image == Image.MENU_BUTTON_BACKGROUND || image == Image.BACK
                            || image == Image.RANDOM || image == Image.RANDOM_OVER || image == Image.TOP) && Gdx.gl != null) {
                        Pixmap pixels = null;
                        try {
                            pixels = new Pixmap(file.handle());
                            Pixmap source = pixels;
                            if (image == Image.TOP) {
                                topCoverage = SongSelectTopCoverage.detect(pixels.getWidth(), pixels.getHeight(),
                                        file.density(), (x,y) -> source.getPixel(x,y) & 255);
                            } else {
                                var body = SongSelectBodyBounds.detect(pixels.getWidth(), pixels.getHeight(),
                                        (x, y) -> source.getPixel(x, y) & 255);
                                if (image == Image.MENU_BUTTON_BACKGROUND) rowBody = body;
                                else actionBodies.put(image, body);
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
            if (image == Image.BACK) resolver.resolveAnimationFirstFrame(image.basename, load);
            else resolver.resolve(image.basename, load);
        }
    }

    private static void log(String message, Exception error) {
        if (Gdx.app != null) Gdx.app.log("SongSelect skin", message, error);
    }

    public SkinTexture get(Image image) { return textures.get(image); }
    public float topDepth(float start, float end) {
        var top = get(Image.TOP);
        return top == null ? 0 : topCoverage == null ? top.logicalHeight() : topCoverage.depth(start,end);
    }
    public SongSelectBodyBounds actionBody(Image image) { return actionBodies.getOrDefault(image, SongSelectBodyBounds.FULL); }
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
        actionBodies.clear();
        fallbackStar = null;
        topCoverage = null;
    }
}
