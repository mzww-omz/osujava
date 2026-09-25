package dev.osujava.ui.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;

/** Small immediate-mode drawing helper; screens own layout and input. */
public final class UiView {
    private final OsuJavaGame game;
    private final Matrix4 projection = new Matrix4();
    private UiLayout layout;
    private final Color fadeColor = new Color();

    public UiView(OsuJavaGame game) { this.game = game; }

    public UiLayout prepare() {
        layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        projection.setToOrtho2D(0, 0, layout.width(), layout.height());
        game.batch().setProjectionMatrix(projection);
        game.shapes().setProjectionMatrix(projection);
        return layout;
    }

    public void clear() {
        Gdx.gl.glClearColor(UiTheme.BACKGROUND.r, UiTheme.BACKGROUND.g, UiTheme.BACKGROUND.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void background(Texture texture, float alpha) {
        if (texture == null) return;
        float scale = Math.max(layout.width() / texture.getWidth(), layout.height() / texture.getHeight());
        float w = texture.getWidth() * scale;
        float h = texture.getHeight() * scale;
        SpriteBatch batch = game.batch();
        batch.setColor(1, 1, 1, alpha);
        batch.begin();
        batch.draw(texture, (layout.width() - w) / 2, (layout.height() - h) / 2, w, h);
        batch.end();
        batch.setColor(Color.WHITE);
    }

    public void beginShapes() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        game.shapes().begin(ShapeRenderer.ShapeType.Filled);
    }
    public void endShapes() { game.shapes().end(); }

    public void box(float x, float y, float w, float h, float radius, Color color) {
        ShapeRenderer s = game.shapes();
        float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        s.setColor(color);
        s.rect(x + r, y, w - 2 * r, h);
        if (r > 0) {
            s.rect(x, y + r, r, h - 2 * r);
            s.rect(x + w - r, y + r, r, h - 2 * r);
            s.circle(x + r, y + r, r, 10);
            s.circle(x + w - r, y + r, r, 10);
            s.circle(x + r, y + h - r, r, 10);
            s.circle(x + w - r, y + h - r, r, 10);
        }
    }

    public void circle(float x, float y, float radius, Color color) {
        game.shapes().setColor(color);
        game.shapes().circle(x, y, radius, 64);
    }

    public void quad(float x1, float y1, float x2, float y2, float x3, float y3,
                     float x4, float y4, Color color) {
        ShapeRenderer s = game.shapes();
        s.setColor(color);
        s.triangle(x1, y1, x2, y2, x3, y3);
        s.triangle(x1, y1, x3, y3, x4, y4);
    }

    public void beginText() { game.batch().begin(); }
    public void image(Texture texture, float x, float y, float w, float h) {
        if (texture == null) return;
        game.batch().draw(texture, x, y, w, h);
    }
    public void endText() {
        game.batch().end();
        game.font().getData().setScale(1f);
        game.font().setColor(Color.WHITE);
    }

    public void text(String value, float x, float baseline, float width, float scale, Color color, int align) {
        BitmapFont font = game.font();
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(game.batch(), value, x, baseline, 0, value.length(), width, align, false, "...");
    }

    public void text(String value, float x, float baseline, float width, float scale, Color color) {
        text(value, x, baseline, width, scale, color, Align.left);
    }

    public void textSmooth(String value, float x, float baseline, float width, float scale, Color color, int align) {
        game.smoothFont().draw(game.batch(), value, x, baseline, width, scale, color, align);
    }

    public void textSmooth(String value, float x, float baseline, float width, float scale, Color color) {
        textSmooth(value, x, baseline, width, scale, color, Align.left);
    }

    public void cover(float opacity) {
        if (opacity <= 0) return;
        beginShapes();
        box(0, 0, layout.width(), layout.height(), 0, fadeColor.set(UiTheme.BACKGROUND.r,
                UiTheme.BACKGROUND.g, UiTheme.BACKGROUND.b, Math.min(1, opacity)));
        endShapes();
    }

    public void fade(UiTransition transition, float delta) {
        transition.advance(delta);
        cover(transition.opacity());
    }
}
