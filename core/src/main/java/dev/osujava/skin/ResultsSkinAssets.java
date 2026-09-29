package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Ranking assets only; no beatmap provider, extraction or file access while drawing. */
public final class ResultsSkinAssets implements Disposable {
    public enum Image {
        PANEL("ranking-panel"), TITLE("ranking-title"), COMBO("ranking-maxcombo"),
        ACCURACY("ranking-accuracy"), GRAPH("ranking-graph"), PERFECT("ranking-perfect"),
        SS("ranking-X"), S("ranking-S"), A("ranking-A"), B("ranking-B"), C("ranking-C"), D("ranking-D"), F("ranking-F"),
        HIT300("hit300"), HIT100("hit100"), HIT50("hit50"), GEKI("hit300g"), KATU("hit100k"), MISS("hit0"),
        RETRY("ranking-retry"), BACK("menu-back");
        public final String basename;
        Image(String basename) { this.basename = basename; }
        boolean judgement() { return ordinal() >= HIT300.ordinal() && ordinal() <= MISS.ordinal(); }
    }
    public record SkinTexture(Texture texture, SkinAssetResolver.AssetFile file) {
        public float width() { return file.logicalSize(texture.getWidth()); }
        public float height() { return file.logicalSize(texture.getHeight()); }
    }
    private final Map<Image, SkinTexture> images = new EnumMap<>(Image.class);
    private final Map<Character, SkinTexture> glyphs = new HashMap<>();
    private final Set<Texture> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private final SkinConfiguration configuration;

    public ResultsSkinAssets(Path directory, Path fallback) {
        this(SkinAssetResolver.withBundledDefault(directory, fallback), new Function<>() {
            private long pixels;
            @Override public Texture apply(SkinAssetResolver.AssetFile file) {
                long next = SongSelectImageLimits.check(file.handle());
                if (pixels + next > 64L * 1024 * 1024) throw new IllegalArgumentException("Results texture budget exceeded");
                Texture texture = new Texture(file.handle());
                pixels += next;
                return texture;
            }
        });
    }

    ResultsSkinAssets(SkinAssetResolver resolver, Function<SkinAssetResolver.AssetFile, Texture> loader) {
        SkinConfiguration config;
        try { config = resolver.readSelectedConfiguration(); }
        catch (IOException e) { config = SkinConfiguration.withoutIni(); }
        configuration = config;
        for (Image image : Image.values()) {
            Predicate<SkinAssetResolver.AssetFile> load = file -> load(file, loader, texture -> images.put(image, texture));
            if (image.judgement()) {
                // Static > frame zero within each provider; custom frame zero > fallback static.
                for (var provider : SkinAssetResolver.Provider.values()) {
                    if (resolver.resolveFromProvider(image.basename, provider, load).isPresent()
                            || resolver.resolveFromProvider(image.basename + "-0", provider, load).isPresent()) break;
                }
            } else if (image == Image.BACK) resolver.resolveAnimationFirstFrame(image.basename, load);
            else if (image == Image.RETRY) {
                boolean found = legacy() ? resolver.resolve(image.basename, load).isPresent()
                        : resolver.resolveFromProvider(image.basename, SkinAssetResolver.Provider.CUSTOM, load).isPresent();
                if (!found) resolver.resolve("pause-retry", load);
            } else resolver.resolve(image.basename, load);
        }
        for (char c : "0123456789.,%x".toCharArray())
            resolver.resolveHudGlyph(configuration.fonts().scorePrefix(), c,
                    file -> load(file, loader, texture -> glyphs.put(c, texture)));
    }

    private boolean load(SkinAssetResolver.AssetFile file, Function<SkinAssetResolver.AssetFile, Texture> loader,
                         java.util.function.Consumer<SkinTexture> accept) {
        Texture texture = null;
        try {
            texture = loader.apply(file);
            if (texture == null) return false;
            if (texture.getWidth() <= 0 || texture.getHeight() <= 0) throw new IllegalArgumentException("Empty texture");
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            accept.accept(new SkinTexture(texture, file));
            owned.add(texture);
            return true;
        } catch (RuntimeException e) {
            if (texture != null && !owned.contains(texture)) texture.dispose();
            if (Gdx.app != null) Gdx.app.log("Results skin", "Rejected " + file.path() + "; trying fallback", e);
            return false;
        }
    }

    public boolean legacy() { return configuration.legacyVersion() <= 1; }
    public float overlap() { return configuration.fonts().scoreOverlap(); }
    public SkinTexture get(Image image) { return images.get(image); }
    public SkinTexture glyph(char character) { return glyphs.get(character); }
    public boolean hasText(String text) { return text.chars().allMatch(c -> glyphs.containsKey((char) c)); }
    @Override public void dispose() {
        owned.forEach(Texture::dispose); owned.clear(); images.clear(); glyphs.clear();
    }
}
