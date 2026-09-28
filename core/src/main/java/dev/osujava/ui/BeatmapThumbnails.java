package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Small bounded texture cache for visible song select rows. */
final class BeatmapThumbnails implements AutoCloseable {
    private static final int LIMIT = 18;
    private final Function<Path, Texture> loader;

    BeatmapThumbnails() {
        this(path -> {
            if (!Files.isRegularFile(path)) return null;
            Texture texture = new Texture(Gdx.files.absolute(path.toString()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        });
    }
    BeatmapThumbnails(Function<Path, Texture> loader) { this.loader = loader; }
    private float elapsed;
    private Set<Path> framePaths = Set.of();
    private final Map<Path, Float> loadedAt = new LinkedHashMap<>();
    private final LinkedHashMap<Path, Texture> textures = new LinkedHashMap<>(20, .75f, true);

    Texture get(Path path) {
        if (path == null) return null;
        if (textures.containsKey(path)) return textures.get(path);
        Texture texture = null;
        try {
            texture = loader.apply(path);
        } catch (GdxRuntimeException ignored) { }
        textures.put(path, texture);
        if (texture != null) loadedAt.put(path, elapsed);
        trim();
        return texture;
    }

    /** Pin this frame's resources before building snapshots; eviction cannot invalidate draw input. */
    void prepare(Set<Path> paths) {
        framePaths = Set.copyOf(paths);
        for (Path path : paths) get(path);
        trim();
    }

    /** Draw-side lookup never loads a file or changes residency. */
    Texture resident(Path path) { return path == null ? null : textures.get(path); }

    private void trim() {
        var iterator = textures.entrySet().iterator();
        while (textures.size() > LIMIT && iterator.hasNext()) {
            Map.Entry<Path, Texture> eldest = iterator.next();
            if (framePaths.contains(eldest.getKey())) continue;
            if (eldest.getValue() != null) eldest.getValue().dispose();
            loadedAt.remove(eldest.getKey());
            iterator.remove();
        }
    }

    void advance(float delta) { if (Float.isFinite(delta)) elapsed += Math.max(0, Math.min(delta, 2f)); }
    float opacity(Path path) {
        Float start = loadedAt.get(path);
        return start == null ? 0 : fade(elapsed - start);
    }
    static float fade(float age) { return Math.max(0, Math.min(1, age / .11f)); }

    @Override public void close() {
        for (Texture texture : textures.values()) if (texture != null) texture.dispose();
        textures.clear();
        loadedAt.clear();
        framePaths = Set.of();
    }
}
