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
    private float elapsed;
    private final Map<Path, Float> loadedAt = new LinkedHashMap<>();
    private final LinkedHashMap<Path, Texture> textures = new LinkedHashMap<>(20, .75f, true);

    Texture get(Path path) {
        if (path == null) return null;
        if (textures.containsKey(path)) return textures.get(path);
        Texture texture = null;
        try {
            if (!Files.isRegularFile(path)) { textures.put(path, null); trim(); return null; }
            texture = new Texture(Gdx.files.absolute(path.toString()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (GdxRuntimeException ignored) { }
        textures.put(path, texture);
        if (texture != null) loadedAt.put(path, elapsed);
        trim();
        return texture;
    }

    private void trim() {
        if (textures.size() > LIMIT) {
            Map.Entry<Path, Texture> eldest = textures.entrySet().iterator().next();
            if (eldest.getValue() != null) eldest.getValue().dispose();
            loadedAt.remove(eldest.getKey());
            textures.remove(eldest.getKey());
        }
    }

    void advance(float delta) { if (Float.isFinite(delta)) elapsed += Math.max(0, Math.min(delta, .1f)); }
    float opacity(Path path) {
        Float start = loadedAt.get(path);
        return start == null ? 0 : fade(elapsed - start);
    }
    static float fade(float age) { return Math.max(0, Math.min(1, age / .11f)); }

    @Override public void close() {
        for (Texture texture : textures.values()) if (texture != null) texture.dispose();
        textures.clear();
        loadedAt.clear();
    }
}
