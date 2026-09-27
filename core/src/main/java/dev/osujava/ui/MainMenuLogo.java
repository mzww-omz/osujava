package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import dev.osujava.ui.theme.UiView;

/** Shared menu logo artwork. Pure drawing of supplied layers. */
final class MainMenuLogo implements AutoCloseable {
    private Texture texture;

    MainMenuLogo() {
        texture = new Texture(Gdx.files.classpath("ui/main-menu-logo.png"), true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
    }

    void visualiser(UiView view, MainMenuLayout m, float scale, float[] bins) {
        float radius = m.radius() * scale;
        float lengthScale = m.radius() / 200 * scale;
        Color tint = new Color(1, .94f, .98f, .5f * .2f);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        view.beginShapes();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int round = 0; round < MenuVisualiser.ROUNDS; round++) for (int i = 0; i < MenuVisualiser.BARS; i++) {
            if (!MenuVisualiser.visible(bins[i])) continue;
            double angle = MenuVisualiser.rotation(i, round);
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            float halfWidth = radius * (float) Math.sin(Math.PI / MenuVisualiser.BARS);
            float x = m.cx() + dx * radius, y = m.cy() + dy * radius;
            float length = MenuVisualiser.MAX_LENGTH * bins[i] * lengthScale;
            float tx = -dy * halfWidth, ty = dx * halfWidth;
            view.quad(x - tx, y - ty, x + dx * length - tx, y + dy * length - ty,
                    x + dx * length + tx, y + dy * length + ty, x + tx, y + ty, tint);
        }
        view.endShapes();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
    void draw(UiView view, MainMenuLayout m, MainMenuModel model) {
        draw(view, m.cx(), m.cy(), m.radius() * model.scale(), model.flash());
    }
    void draw(UiView view, float x, float y, float r, float flash) {
        view.beginShapes();
        for (int i = 10; i > 0; i--) view.circle(x, y, r + i * 2.2f, new Color(1, .4f, .65f, .01f));
        view.endShapes();
        view.beginText();
        view.imageCover(texture, x - r, y - r, r * 2, r * 2);
        view.endText();
        if (flash > .001f) {
            view.beginShapes();
            view.circle(x, y, r * .945f, new Color(1, 1, 1, flash));
            view.endShapes();
        }
    }
    @Override public void close() {
        if (texture != null) texture.dispose();
        texture = null;
    }
}
