package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small bounded texture cache for visible song select rows. */
final class BeatmapThumbnails implements AutoCloseable {
    private static final int LIMIT = 18;
    private final LinkedHashMap<Path, Texture> textures = new LinkedHashMap<>(20, .75f, true);

    Texture get(Path path) {
        if (path == null || !Files.isRegularFile(path)) return null;
        if (textures.containsKey(path)) return textures.get(path);
        Texture texture = null;
        try {
            texture = new Texture(Gdx.files.absolute(path.toString()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (GdxRuntimeException ignored) { }
        textures.put(path, texture);
        if (textures.size() > LIMIT) {
            Map.Entry<Path, Texture> eldest = textures.entrySet().iterator().next();
            if (eldest.getValue() != null) eldest.getValue().dispose();
            textures.remove(eldest.getKey());
        }
        return texture;
    }

    @Override public void close() {
        for (Texture texture : textures.values()) if (texture != null) texture.dispose();
        textures.clear();
    }
}
