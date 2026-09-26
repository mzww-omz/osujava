package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
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
import java.util.Set;
import java.util.function.Function;

/** Render-thread-owned textures, loaded once per gameplay screen, independently of GameplaySkin colours. */
public final class OsuSkinAssets implements Disposable {
    public enum Image {
        HIT_CIRCLE("hitcircle"), HIT_CIRCLE_OVERLAY("hitcircleoverlay"), APPROACH_CIRCLE("approachcircle"),
        SLIDER_START_CIRCLE("sliderstartcircle"), SLIDER_START_CIRCLE_OVERLAY("sliderstartcircleoverlay"),
        SLIDER_END_CIRCLE("sliderendcircle"), SLIDER_END_CIRCLE_OVERLAY("sliderendcircleoverlay"),
        REVERSE_ARROW("reversearrow"), SLIDER_FOLLOW_CIRCLE("sliderfollowcircle"), SLIDER_TICK("sliderscorepoint");

        private final String basename;

        Image(String basename) { this.basename = basename; }
    }

    private final EnumMap<Image, SkinTexture> textures = new EnumMap<>(Image.class);
    private final Set<Texture> ownedTextures = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<SkinTexture> sliderBallFrames = List.of();
    private List<SkinTexture> hitCircleDigits = List.of();
    private SkinConfiguration configuration = SkinConfiguration.defaults();

    public OsuSkinAssets(Path directory) {
        this(directory, file -> new Texture(Gdx.files.absolute(file.path().toAbsolutePath().toString())));
    }

    /** Allows asset ownership/failure tests without an OpenGL context. */
    OsuSkinAssets(Path directory, Function<SkinAssetResolver.AssetFile, Texture> textureLoader) {
        SkinAssetResolver resolver = new SkinAssetResolver(directory);
        for (Image image : Image.values()) {
            resolver.resolve(image.basename).ifPresent(file -> {
                SkinTexture texture = load(file, textureLoader);
                if (texture != null) textures.put(image, texture);
            });
        }
        try {
            configuration = SkinConfiguration.read(directory);
        } catch (IOException e) {
            logFallback("Could not read skin.ini; using combo number fallback.", e);
        }
        resolver.resolveHitCircleDigits(configuration).ifPresent(files -> {
            List<SkinTexture> loaded = new ArrayList<>(10);
            for (var file : files) {
                SkinTexture texture = load(file, textureLoader);
                if (texture == null) {
                    for (SkinTexture digit : loaded) disposeIfUnreferenced(digit.texture());
                    return;
                }
                loaded.add(texture);
            }
            hitCircleDigits = List.copyOf(loaded);
        });
        List<SkinTexture> ballFrames = new ArrayList<>();
        for (var file : resolver.resolveSliderBall()) {
            SkinTexture frame = load(file, textureLoader);
            if (frame == null) {
                // A broken animation falls back as a whole; never skip or substitute frames.
                for (SkinTexture loaded : ballFrames) disposeIfUnreferenced(loaded.texture());
                return;
            }
            ballFrames.add(frame);
        }
        sliderBallFrames = List.copyOf(ballFrames);
    }

    private SkinTexture load(SkinAssetResolver.AssetFile file,
                             Function<SkinAssetResolver.AssetFile, Texture> textureLoader) {
        Texture texture = null;
        try {
            texture = textureLoader.apply(file);
            ownedTextures.add(texture);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return new SkinTexture(texture, file);
        } catch (GdxRuntimeException | IllegalArgumentException e) {
            if (texture != null) disposeIfUnreferenced(texture);
            logFallback("Could not load " + file.path() + "; using existing drawing fallback.", e);
        }
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
    public double legacyVersion() { return configuration.legacyVersion(); }
    public float hitCircleOverlap() { return configuration.fonts().hitCircleOverlap(); }

    public List<SkinTexture> sliderBallFrames() { return sliderBallFrames; }

    private void disposeIfUnreferenced(Texture texture) {
        if (textures.values().stream().anyMatch(asset -> asset.texture() == texture)
                || hitCircleDigits.stream().anyMatch(asset -> asset.texture() == texture)
                || sliderBallFrames.stream().anyMatch(asset -> asset.texture() == texture)) return;
        if (ownedTextures.remove(texture)) texture.dispose();
    }

    @Override
    public void dispose() {
        for (Texture texture : ownedTextures) texture.dispose();
        ownedTextures.clear();
        textures.clear();
        sliderBallFrames = List.of();
        hitCircleDigits = List.of();
    }

    public record SkinTexture(Texture texture, SkinAssetResolver.AssetFile file) {
        public int density() { return file.density(); }
        public float logicalWidth() { return file.logicalSize(texture.getWidth()); }
        public float logicalHeight() { return file.logicalSize(texture.getHeight()); }
    }
}
