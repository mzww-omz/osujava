package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Menu presentation reuses the cursor visual without constructing a gameplay session. */
final class SongSelectCursor implements AutoCloseable {
    private final GameplayCursorRenderer renderer;
    private final LegacyCursorVisual visual;
    private final GameplayCursorVisibility visibility;
    private boolean pressed;
    private PlayfieldViewport viewport;
    private double now;

    SongSelectCursor(SongSelectSkinAssets skin) {
        renderer = new GameplayCursorRenderer(image -> {
            var asset = skin.get(switch (image) {
                case CURSOR -> Image.CURSOR;
                case CURSOR_TRAIL -> Image.CURSOR_TRAIL;
                case CURSOR_MIDDLE -> Image.CURSOR_MIDDLE;
                default -> throw new IllegalArgumentException("Not a cursor image");
            });
            return asset == null ? null : new OsuSkinAssets.SkinTexture(asset.texture(), asset.file());
        }, skin.configuration().cursor());
        visual = renderer.createVisual();
        visibility = new GameplayCursorVisibility(Gdx.graphics);
    }

    /** The menu's SD asset canvas is 768 high; legacy cursor visuals divide sizes by 1.6. */
    static PlayfieldViewport viewport(float height) { return new PlayfieldViewport(0, 0, height / 480); }
    void show() { visibility.show(); }
    void hide() { visibility.hide(); }
    void update(float height, float x, float y, boolean held, double seconds) {
        viewport = viewport(height); now = seconds * 1000;
        visual.input(now, viewport.toOsuX(x), viewport.toOsuY(y), held, held && !pressed);
        pressed = held;
    }
    void draw(SpriteBatch batch, ShapeRenderer shapes) {
        if (viewport != null) renderer.draw(batch, shapes, visual, now, viewport);
    }
    @Override public void close() { visibility.close(); }
}
