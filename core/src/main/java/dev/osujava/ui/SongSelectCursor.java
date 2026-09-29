package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;

/** Menu presentation reuses the cursor visual without constructing a gameplay session. */
final class SongSelectCursor implements AutoCloseable {
    private final GameplayCursorRenderer renderer;
    private final LegacyCursorVisual visual;
    private final GameplayCursorVisibility visibility;
    private final SongSelectCursorInput input = new SongSelectCursorInput();
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
    void hide() { input.clear(); visibility.hide(); }
    void event(long nanos, int x, int y, int button, boolean down) { input.event(nanos, x, y, button, down); }
    void update(UiLayout layout, int x, int y, boolean left, boolean right, double seconds) {
        viewport = viewport(layout.height()); now = seconds * 1000;
        input.advance(now, System.nanoTime(), x, y, left, right, (time, px, py, held, press) ->
                visual.input(time, viewport.toOsuX(layout.pointerX(px)), viewport.toOsuY(layout.pointerY(py)), held, press));
    }
    void draw(SpriteBatch batch, ShapeRenderer shapes) {
        if (viewport != null) renderer.draw(batch, shapes, visual, now, viewport);
    }
    @Override public void close() { visibility.close(); }
}
