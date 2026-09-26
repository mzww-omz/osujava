package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.nio.file.Path;
import java.util.EnumMap;

/** Render-thread-owned textures, loaded once per gameplay screen, independently of GameplaySkin colours. */
public final class OsuSkinAssets implements Disposable {
    public enum Image {
        HIT_CIRCLE("hitcircle"), HIT_CIRCLE_OVERLAY("hitcircleoverlay"), APPROACH_CIRCLE("approachcircle");

        private final String basename;

        Image(String basename) { this.basename = basename; }
    }

    private final EnumMap<Image, SkinTexture> textures = new EnumMap<>(Image.class);

    public OsuSkinAssets(Path directory) {
        SkinAssetResolver resolver = new SkinAssetResolver(directory);
        for (Image image : Image.values()) {
            resolver.resolve(image.basename).ifPresent(file -> load(image, file));
        }
    }

    private void load(Image image, SkinAssetResolver.AssetFile file) {
        Texture texture = null;
        try {
            texture = new Texture(Gdx.files.absolute(file.path().toAbsolutePath().toString()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            textures.put(image, new SkinTexture(texture, file));
        } catch (GdxRuntimeException | IllegalArgumentException e) {
            if (texture != null) texture.dispose();
            Gdx.app.log("Skin", "Could not load " + file.path() + "; using vector fallback.", e);
        }
    }

    /** Null means this individual image should use the existing vector drawing. */
    public SkinTexture get(Image image) { return textures.get(image); }

    @Override
    public void dispose() {
        for (SkinTexture asset : textures.values()) asset.texture().dispose();
        textures.clear();
    }

    public record SkinTexture(Texture texture, SkinAssetResolver.AssetFile file) {
        public int density() { return file.density(); }
        public float logicalWidth() { return file.logicalSize(texture.getWidth()); }
        public float logicalHeight() { return file.logicalSize(texture.getHeight()); }
    }
}
