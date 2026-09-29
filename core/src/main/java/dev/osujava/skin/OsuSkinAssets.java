package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Result;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Style;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

/** Render-thread-owned textures, loaded once per gameplay screen, independently of GameplaySkin colours. */
public final class OsuSkinAssets implements Disposable {
    public enum Image {
        HIT_CIRCLE("hitcircle"), HIT_CIRCLE_OVERLAY("hitcircleoverlay"), APPROACH_CIRCLE("approachcircle"),
        SLIDER_START_CIRCLE("sliderstartcircle"), SLIDER_START_CIRCLE_OVERLAY("sliderstartcircleoverlay"),
        SLIDER_END_CIRCLE("sliderendcircle"), SLIDER_END_CIRCLE_OVERLAY("sliderendcircleoverlay"),
        REVERSE_ARROW("reversearrow"), SLIDER_FOLLOW_CIRCLE("sliderfollowcircle"), SLIDER_TICK("sliderscorepoint"),
        SPINNER_BACKGROUND("spinner-background"), SPINNER_CIRCLE("spinner-circle"), SPINNER_METRE("spinner-metre"),
        SPINNER_APPROACH("spinner-approachcircle"), SPINNER_GLOW("spinner-glow"), SPINNER_BOTTOM("spinner-bottom"),
        SPINNER_TOP("spinner-top"), SPINNER_MIDDLE2("spinner-middle2"), SPINNER_MIDDLE("spinner-middle"),
        SPINNER_SPIN("spinner-spin"), SPINNER_CLEAR("spinner-clear"), SPINNER_RPM("spinner-rpm"),
        CURSOR("cursor"), CURSOR_MIDDLE("cursormiddle"), CURSOR_TRAIL("cursortrail");

        private final String basename;

        Image(String basename) { this.basename = basename; }
    }

    private final EnumMap<Image, SkinTexture> textures = new EnumMap<>(Image.class);
    public enum LoadStatus { MISSING, LOADED, FAILED }
    public record AssetDiagnostic(LoadStatus status, SkinAssetResolver.AssetFile file, boolean fallback) { }
    private final EnumMap<Image, AssetDiagnostic> diagnostics = new EnumMap<>(Image.class);
    private final Set<Texture> ownedTextures = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<SkinTexture> sliderBallFrames = List.of();
    private List<SkinTexture> hitCircleDigits = List.of();
    public enum HudFont { SCORE, COMBO }
    private final EnumMap<HudFont, Map<Character, SkinTexture>> hudFonts = new EnumMap<>(HudFont.class);
    private final Map<SkinAssetResolver.AssetFile, SkinTexture> textureCache = new HashMap<>();
    public record JudgementAsset(List<SkinTexture> frames, SkinTexture particle, Style style) {
        public JudgementAsset { frames = List.copyOf(frames); }
    }
    private final EnumMap<Result, JudgementAsset> judgements = new EnumMap<>(Result.class);
    private SkinConfiguration configuration = SkinConfiguration.defaults();
    private final SkinConfiguration.Fonts bundledFonts;

    public OsuSkinAssets(Path directory) {
        this(directory, (Path) null);
    }

    public OsuSkinAssets(Path directory, Path fallbackDirectory) {
        this(file -> new Texture(file.handle()), SkinAssetResolver.withBundledDefault(directory, fallbackDirectory));
    }

    /** Allows asset ownership/failure tests without an OpenGL context. */
    OsuSkinAssets(Path directory, Function<SkinAssetResolver.AssetFile, Texture> textureLoader) {
        this(directory, null, textureLoader);
    }

    OsuSkinAssets(Path directory, Path fallbackDirectory, Function<SkinAssetResolver.AssetFile, Texture> textureLoader) {
        this(textureLoader, new SkinAssetResolver(directory, fallbackDirectory));
    }

    OsuSkinAssets(Function<SkinAssetResolver.AssetFile, Texture> textureLoader, SkinAssetResolver resolver) {
        bundledFonts = resolver.bundledFonts();
        for (Image image : Image.values()) {
            diagnostics.put(image, new AssetDiagnostic(LoadStatus.MISSING, null, false));
            Image sliderBase = sliderCircleBase(image);
            // Reuse the selected skin's hitcircle family when its dedicated slider base is absent.
            // An orphan slider overlay still cannot select a dedicated family on its own.
            boolean customOnly = sliderBase != null && hasCustomHitCircle()
                    && resolver.resolveCustom(sliderBase.basename, file -> load(file, textureLoader) != null).isEmpty();
            var resolved = customOnly
                    ? resolver.resolveCustom(image.basename, file -> load(file, textureLoader) != null)
                        .or(() -> resolver.resolveCustom(image.basename, file -> true))
                    : resolver.resolve(image.basename, file -> load(file, textureLoader) != null)
                        .or(() -> resolver.resolve(image.basename));
            resolved.ifPresent(file -> {
                SkinTexture texture = load(file, textureLoader);
                if (texture != null) textures.put(image, texture);
                diagnostics.put(image, new AssetDiagnostic(texture == null ? LoadStatus.FAILED : LoadStatus.LOADED,
                        file, resolver.isFallback(file)));
            });
        }
        try {
            configuration = resolver.readConfiguration();
        } catch (IOException e) {
            logFallback("Could not read skin.ini; using combo number fallback.", e);
        }
        resolver.resolveHitCircleDigits(configuration).ifPresent(available -> {
            var files = resolver.resolveHitCircleDigits(configuration, file -> load(file, textureLoader) != null);
            if (files.isEmpty()) {
                for (SkinTexture asset : new ArrayList<>(textureCache.values()))
                    if (asset != null) disposeIfUnreferenced(asset.texture());
                return;
            }
            List<SkinTexture> loaded = new ArrayList<>(10);
            for (var file : files.get()) {
                SkinTexture texture = load(file, textureLoader);
                if (texture == null) {
                    for (SkinTexture digit : loaded) disposeIfUnreferenced(digit.texture());
                    return;
                }
                loaded.add(texture);
            }
            hitCircleDigits = List.copyOf(loaded);
        });
        for (HudFont font : HudFont.values()) {
            String prefix = font == HudFont.SCORE ? configuration.fonts().scorePrefix() : configuration.fonts().comboPrefix();
            Map<Character, SkinTexture> glyphs = new HashMap<>();
            for (char character : "0123456789.,%x".toCharArray()) {
                resolver.resolveHudGlyph(prefix, character, font == HudFont.COMBO, file -> load(file, textureLoader) != null).ifPresent(file -> {
                    SkinTexture texture = load(file, textureLoader);
                    if (texture != null) glyphs.put(character, texture);
                });
            }
            hudFonts.put(font, Map.copyOf(glyphs));
        }
        for (Result result : Result.values()) {
            if (result == Result.SLIDER_TAIL_HIT && configuration.legacyVersion() >= 2) continue;
            List<SkinTexture> frames = new ArrayList<>();
            var files = result == Result.SLIDER_TAIL_HIT ? resolver.resolve(result.image).map(List::of).orElseGet(List::of)
                    : resolver.resolveAnimation(result.image, file -> load(file, textureLoader) != null);
            boolean failed = false;
            for (var file : files) {
                SkinTexture frame = load(file, textureLoader);
                if (frame == null) { failed = true; break; }
                frames.add(frame);
            }
            if (failed) {
                for (var frame : frames) disposeIfUnreferenced(frame.texture());
                continue;
            }
            if (frames.isEmpty()) continue;
            SkinTexture particle = result.particle == null ? null
                    : resolver.resolve(result.particle).map(file -> load(file, textureLoader)).orElse(null);
            judgements.put(result, new JudgementAsset(frames, particle,
                    result == Result.SLIDER_TAIL_HIT ? Style.SLIDER_POINT : particle == null ? Style.OLD : Style.NEW));
        }
        List<SkinTexture> ballFrames = new ArrayList<>();
        for (var file : resolver.resolveSliderBall(file -> load(file, textureLoader) != null)) {
            SkinTexture frame = load(file, textureLoader);
            if (frame == null) {
                // Resolution already checked loadability; retain defensive cleanup for a loader failure.
                for (SkinTexture loaded : ballFrames) disposeIfUnreferenced(loaded.texture());
                return;
            }
            ballFrames.add(frame);
        }
        sliderBallFrames = List.copyOf(ballFrames);
    }

    private boolean hasCustomHitCircle() {
        for (Image image : List.of(Image.HIT_CIRCLE, Image.HIT_CIRCLE_OVERLAY)) {
            SkinTexture asset = textures.get(image);
            if (asset != null && !asset.file().fallback()) return true;
        }
        return false;
    }

    private static Image sliderCircleBase(Image image) {
        return switch (image) {
            case SLIDER_START_CIRCLE, SLIDER_START_CIRCLE_OVERLAY -> Image.SLIDER_START_CIRCLE;
            case SLIDER_END_CIRCLE, SLIDER_END_CIRCLE_OVERLAY -> Image.SLIDER_END_CIRCLE;
            default -> null;
        };
    }

    private SkinTexture load(SkinAssetResolver.AssetFile file,
                             Function<SkinAssetResolver.AssetFile, Texture> textureLoader) {
        if (textureCache.containsKey(file)) return textureCache.get(file);
        Texture texture = null;
        try {
            texture = textureLoader.apply(file);
            ownedTextures.add(texture);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            SkinTexture asset = new SkinTexture(texture, file);
            textureCache.put(file, asset);
            return asset;
        } catch (GdxRuntimeException | IllegalArgumentException e) {
            if (texture != null) disposeIfUnreferenced(texture);
            logFallback("Could not load " + file.path() + "; using existing drawing fallback.", e);
        }
        textureCache.put(file, null); // Cache failed lookups too when score/combo share a prefix.
        return null;
    }

    private static void logFallback(String message, Exception error) {
        if (Gdx.app != null) Gdx.app.log("Skin", message, error);
    }

    /** Slider circles select their entire prefix by the loaded base, as in LegacyMainCirclePiece. */
    public SkinTexture get(Image image) {
        if (hasDedicatedSliderCircle(image)) return textures.get(image);
        return textures.get(switch (image) {
            case SLIDER_START_CIRCLE, SLIDER_END_CIRCLE -> Image.HIT_CIRCLE;
            case SLIDER_START_CIRCLE_OVERLAY, SLIDER_END_CIRCLE_OVERLAY -> Image.HIT_CIRCLE_OVERLAY;
            default -> image;
        });
    }

    /** A dedicated circle with no overlay must omit it, including the vector overlay fallback. */
    public boolean hasDedicatedSliderCircle(Image image) {
        return switch (image) {
            case SLIDER_START_CIRCLE, SLIDER_START_CIRCLE_OVERLAY -> textures.containsKey(Image.SLIDER_START_CIRCLE);
            case SLIDER_END_CIRCLE, SLIDER_END_CIRCLE_OVERLAY -> textures.containsKey(Image.SLIDER_END_CIRCLE);
            default -> false;
        };
    }

    public boolean hasHitCircleDigits() { return hitCircleDigits.size() == 10; }
    public SkinTexture hitCircleDigit(int digit) { return hitCircleDigits.get(digit); }
    public boolean hitCircleOverlayAboveNumber() { return configuration.hitCircleOverlayAboveNumber(); }
    public SkinConfiguration.Colours sliderColours() { return configuration.colours(); }
    public SkinConfiguration.Cursor cursorConfiguration() { return configuration.cursor(); }
    public SkinConfiguration.Spinner spinnerConfiguration() { return configuration.spinner(); }
    /** Immutable load-time result; queries never reload or log per frame. */
    public AssetDiagnostic diagnostic(Image image) { return diagnostics.get(image); }
    public dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.Style spinnerStyle() {
        // The provider supplying the body selects its style before sub-assets fall back.
        boolean ownBackground = get(Image.SPINNER_BACKGROUND) != null && !diagnostic(Image.SPINNER_BACKGROUND).fallback();
        boolean ownTop = get(Image.SPINNER_TOP) != null && !diagnostic(Image.SPINNER_TOP).fallback();
        if (ownBackground || ownTop)
            return dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.select(ownBackground, ownTop);
        return dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.select(
                get(Image.SPINNER_BACKGROUND) != null, get(Image.SPINNER_TOP) != null);
    }
    public double legacyVersion() { return configuration.legacyVersion(); }
    public float hitCircleOverlap() {
        return !configuration.hasIni() && !hitCircleDigits.isEmpty()
                && hitCircleDigits.stream().allMatch(t -> t.file().classpathResource() != null)
                ? bundledFonts.hitCircleOverlap() : configuration.fonts().hitCircleOverlap();
    }

    public SkinTexture hudGlyph(HudFont font, char character) {
        return hudFonts.getOrDefault(font, Map.of()).get(character);
    }
    public boolean hasHudText(HudFont font, String text) {
        if (font == HudFont.SCORE && hudGlyph(font, '5') == null) return false;
        for (char character : text.toCharArray()) if (hudGlyph(font, character) == null) return false;
        return true;
    }
    public float hudOverlap(HudFont font) {
        var glyphs = hudFonts.getOrDefault(font, Map.of());
        var metrics = !configuration.hasIni() && !glyphs.isEmpty()
                && glyphs.values().stream().allMatch(t -> t.file().classpathResource() != null)
                ? bundledFonts : configuration.fonts();
        return font == HudFont.SCORE ? metrics.scoreOverlap() : metrics.comboOverlap();
    }

    public JudgementAsset judgement(Result result) { return judgements.get(result); }
    public Style judgementStyle(Result result) {
        var asset = judgement(result);
        return asset == null ? Style.NONE : asset.style();
    }

    public List<SkinTexture> sliderBallFrames() { return sliderBallFrames; }

    private void disposeIfUnreferenced(Texture texture) {
        if (textures.values().stream().anyMatch(asset -> asset.texture() == texture)
                || hitCircleDigits.stream().anyMatch(asset -> asset.texture() == texture)
                || hudFonts.values().stream().flatMap(m -> m.values().stream()).anyMatch(asset -> asset.texture() == texture)
                || judgements.values().stream().anyMatch(j -> j.frames().stream().anyMatch(a -> a.texture() == texture)
                        || j.particle() != null && j.particle().texture() == texture)
                || sliderBallFrames.stream().anyMatch(asset -> asset.texture() == texture)) return;
        textureCache.values().removeIf(asset -> asset != null && asset.texture() == texture);
        if (ownedTextures.remove(texture)) texture.dispose();
    }

    @Override
    public void dispose() {
        for (Texture texture : ownedTextures) texture.dispose();
        ownedTextures.clear();
        textures.clear();
        textureCache.clear();
        hudFonts.clear();
        judgements.clear();
        sliderBallFrames = List.of();
        hitCircleDigits = List.of();
    }

    public record SkinTexture(Texture texture, SkinAssetResolver.AssetFile file) {
        public int density() { return file.density(); }
        public float logicalWidth() { return file.logicalSize(texture.getWidth()); }
        public float logicalHeight() { return file.logicalSize(texture.getHeight()); }
    }
}
