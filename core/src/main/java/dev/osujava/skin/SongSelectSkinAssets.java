package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

/** SongSelect visuals only. Resolution and configuration belong to the shared skin resolver. */
public final class SongSelectSkinAssets implements Disposable {
    public enum Image {
        MENU_BUTTON_BACKGROUND("menu-button-background"), STAR("star"),
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

    private final EnumMap<Image, SkinTexture> textures = new EnumMap<>(Image.class);
    private final Set<Texture> owned = Collections.newSetFromMap(new IdentityHashMap<>());
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
            resolver.resolve(image.basename, file -> {
                Texture texture = null;
                try {
                    texture = loader.apply(file);
                    if (texture == null) return false;
                    if (texture.getWidth() <= 0 || texture.getHeight() <= 0)
                        throw new IllegalArgumentException("Empty skin texture");
                    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    textures.put(image, new SkinTexture(texture, file));
                    owned.add(texture);
                    return true;
                } catch (GdxRuntimeException | IllegalArgumentException e) {
                    if (texture != null && !owned.contains(texture)) texture.dispose();
                    log("Could not load " + file.path() + "; trying the next skin candidate", e);
                    return false;
                }
            });
        }
    }

    private static void log(String message, Exception error) {
        if (Gdx.app != null) Gdx.app.log("SongSelect skin", message, error);
    }

    public SkinTexture get(Image image) { return textures.get(image); }
    public SkinConfiguration configuration() { return configuration; }

    @Override public void dispose() {
        for (Texture texture : owned) texture.dispose();
        owned.clear();
        textures.clear();
    }
}
