package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.audio.AudioVolumes.Channel;
import dev.osujava.ui.theme.UiLayout;

/** Procedural disks/rings/glow. No skin assets, paths, fonts or textures are created per frame. */
public final class VolumeHudRenderer {
    private static final Channel[] CHANNELS = Channel.values();
    private static final String[] LABELS = {"master", "music", "effect"};
    private static final String[] PERCENTAGES = new String[101];
    static { for (int i = 0; i <= 100; i++) PERCENTAGES[i] = i + "%"; }
    private final OsuJavaGame game;
    private final Matrix4 projection = new Matrix4(), transform = new Matrix4();
    private final Matrix4 oldBatchProjection = new Matrix4(), oldShapeProjection = new Matrix4();
    private final Matrix4 oldBatchTransform = new Matrix4(), oldShapeTransform = new Matrix4();
    private final Color inner = new Color(), outer = new Color(), text = new Color(), shadow = new Color();
    private VolumeHudLayout layout;
    private int width, height;

    public VolumeHudRenderer(OsuJavaGame game) { this.game = game; }
    public void draw(VolumeHud hud) {
        if (hud.alpha() <= 0) return;
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        if (w <= 0 || h <= 0) return;
        if (layout == null || w != width || h != height) {
            width = w; height = h;
            UiLayout ui = UiLayout.fromPixels(w, h);
            layout = new VolumeHudLayout(ui.width(), ui.height());
            projection.setToOrtho2D(0, 0, layout.width(), layout.height());
        }
        SpriteBatch batch = game.batch(); ShapeRenderer shapes = game.shapes();
        oldBatchProjection.set(batch.getProjectionMatrix()); oldShapeProjection.set(shapes.getProjectionMatrix());
        oldBatchTransform.set(batch.getTransformMatrix()); oldShapeTransform.set(shapes.getTransformMatrix());
        float pivotX = layout.x(Channel.MASTER), pivotY = layout.y(Channel.MASTER);
        transform.idt().translate(pivotX, pivotY, 0).scale(hud.scale(), hud.scale(), 1).translate(-pivotX, -pivotY, 0);
        batch.setProjectionMatrix(projection); shapes.setProjectionMatrix(projection);
        batch.setTransformMatrix(transform); shapes.setTransformMatrix(transform);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Channel channel : CHANNELS) circle(shapes, hud, channel);
        shapes.end();
        batch.begin();
        for (Channel channel : CHANNELS) {
            float x = layout.x(channel), y = layout.y(channel), r = layout.radius(channel);
            boolean master = channel == Channel.MASTER;
            text.set(1, 1, 1, hud.alpha());
            game.smoothFont().draw(batch, PERCENTAGES[hud.percent(channel)], x - r, y - (master ? 9 : 5),
                    r * 2, master ? 1.65f : .82f, text, Align.center);
            text.set(.78f, .93f, .97f, hud.alpha() * (channel == hud.selected() ? 1 : .8f));
            label(batch, LABELS[channel.ordinal()], channel == Channel.EFFECT ? x - r - 116 : x - 62,
                    master ? y - r - 25 : channel == Channel.EFFECT ? y - 5 : y + r + 12,
                    channel == Channel.EFFECT ? 108 : 124, master ? 1.0f : .86f, text);
        }
        text.set(.7f, .83f, .87f, hud.alpha() * .8f);
        label(batch, "F4  ·  Tab / ← →  ·  ↑ ↓ / wheel", layout.width() - 335,
                layout.y(Channel.MASTER) - 127, 310, .65f, text);
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setTransformMatrix(oldBatchTransform); shapes.setTransformMatrix(oldShapeTransform);
        batch.setProjectionMatrix(oldBatchProjection); shapes.setProjectionMatrix(oldShapeProjection);
    }

    private void label(SpriteBatch batch, String value, float x, float baseline, float width, float scale, Color color) {
        // Small outline keeps labels readable over bright Song Select rows or beatmap backgrounds.
        shadow.set(.005f, .015f, .025f, color.a * .85f);
        game.smoothFont().draw(batch, value, x - 1, baseline, width, scale, shadow, Align.center);
        game.smoothFont().draw(batch, value, x + 1, baseline, width, scale, shadow, Align.center);
        game.smoothFont().draw(batch, value, x, baseline - 1, width, scale, shadow, Align.center);
        game.smoothFont().draw(batch, value, x, baseline + 1, width, scale, shadow, Align.center);
        game.smoothFont().draw(batch, value, x, baseline, width, scale, color, Align.center);
    }

    private void circle(ShapeRenderer shapes, VolumeHud hud, Channel channel) {
        float x = layout.x(channel), y = layout.y(channel), r = layout.radius(channel), alpha = hud.alpha();
        boolean selected = channel == hud.selected();
        float strength = selected ? .24f + hud.emphasis() * .09f : .10f;
        inner.set(.2f, .85f, 1, alpha * strength); outer.set(.2f, .85f, 1, 0);
        ring(shapes, x, y, r + 1, r + 15, 1, inner, outer);
        shapes.setColor(.015f, .025f, .04f, alpha * .77f);
        shapes.circle(x, y, r - 1, VolumeHudGeometry.SEGMENTS);
        inner.set(.28f, .42f, .47f, alpha * .62f);
        ring(shapes, x, y, r - (channel == Channel.MASTER ? 8 : 4), r, 1, inner, inner);
        float volume = hud.displayed(channel), thickness = channel == Channel.MASTER ? 8 : 4;
        inner.set(.24f, .88f, 1, alpha * (selected ? .46f : .26f)); outer.set(.24f, .88f, 1, 0);
        ring(shapes, x, y, r, r + 9, volume, inner, outer);
        inner.set(.37f, .91f, 1, alpha); outer.set(.88f, .99f, 1, alpha);
        ring(shapes, x, y, r - thickness, r, volume, inner, outer);
        inner.set(.88f, .99f, 1, alpha); outer.set(.88f, .99f, 1, 0);
        ring(shapes, x, y, r, r + .8f, volume, inner, outer);
        inner.set(.37f, .91f, 1, 0); outer.set(.37f, .91f, 1, alpha);
        ring(shapes, x, y, r - thickness - .8f, r - thickness, volume, inner, outer);
    }

    private void ring(ShapeRenderer shapes, float cx, float cy, float innerRadius, float outerRadius,
                      float volume, Color innerColor, Color outerColor) {
        float end = VolumeHudGeometry.end(volume);
        for (int i = 0; i < end; i++) {
            float next = Math.min(i + 1, end);
            float ax = VolumeHudGeometry.x(i), ay = VolumeHudGeometry.y(i);
            float bx = VolumeHudGeometry.x(next), by = VolumeHudGeometry.y(next);
            float ix = cx + ax * innerRadius, iy = cy + ay * innerRadius;
            float ox = cx + ax * outerRadius, oy = cy + ay * outerRadius;
            float jx = cx + bx * innerRadius, jy = cy + by * innerRadius;
            float px = cx + bx * outerRadius, py = cy + by * outerRadius;
            shapes.triangle(ix, iy, ox, oy, px, py, innerColor, outerColor, outerColor);
            shapes.triangle(ix, iy, px, py, jx, jy, innerColor, outerColor, innerColor);
        }
    }
}
