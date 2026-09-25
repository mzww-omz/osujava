package dev.osujava.ui.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads a selected beatmap background once and releases the previous texture. */
public final class BeatmapBackdrop implements AutoCloseable {
    private Texture texture;
    private Path path;
    private float fade;

    public void select(BeatmapSet set, BeatmapDifficulty difficulty) {
        Path next = difficulty != null && difficulty.backgroundPath() != null
                ? difficulty.backgroundPath() : set == null ? null : set.backgroundPath();
        if (next == null ? path == null : next.equals(path)) return;
        close();
        path = next;
        fade = 0;
        if (next == null || !Files.isRegularFile(next)) return;
        try {
            texture = new Texture(Gdx.files.absolute(next.toString()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (GdxRuntimeException ignored) { texture = null; }
    }

    public void draw(UiView view, float delta) {
        fade = Math.min(1, fade + Math.max(0, delta) / 0.25f);
        view.background(texture, 0.75f * fade);
    }

    @Override public void close() {
        if (texture != null) texture.dispose();
        texture = null;
    }
}
