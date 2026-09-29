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

    void visualiser(UiView view, MainMenuLayout m, MainMenuModel model, float[] bins) {
        float unit = m.radius() / 150 * model.transitionScale();
        float radius = m.radius() * model.transitionScale() * model.cookie().spectrumScale();
        Color tint = new Color(128 / 255f, 128 / 255f, 160 / 255f, 1);
        view.beginShapes();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int i = 0; i < MenuVisualiser.BARS; i++) {
            tint.a = MenuVisualiser.opacity(bins[i]) * model.cookie().spectrumAlpha(model.reveal());
            if (tint.a <= 0) continue;
            double angle = MenuVisualiser.rotation(i);
            float dx = (float) Math.cos(angle), dy = -(float) Math.sin(angle);
            float halfWidth = .25f * unit;
            float x = m.cx() + dx * radius, y = m.cy() + dy * radius;
            float length = 300 * bins[i] * unit;
            float tx = -dy * halfWidth, ty = dx * halfWidth;
            view.quad(x - tx, y - ty, x + dx * length - tx, y + dy * length - ty,
                    x + dx * length + tx, y + dy * length + ty, x + tx, y + ty, tint);
        }
        view.endShapes();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
    void draw(UiView view, MainMenuLayout m, MainMenuModel model) {
        var motion = model.cookie();
        float base = m.radius() * model.transitionScale();
        view.beginText();
        for (var ripple : motion.ripples()) {
            float r = base * motion.rippleScale(ripple);
            view.imageCover(texture, m.cx() - r, m.cy() - r, r * 2, r * 2, motion.rippleAlpha(ripple), true);
        }
        float r = m.radius() * model.scale();
        view.imageCover(texture, m.cx() - r, m.cy() - r, r * 2, r * 2);
        r = base * motion.echoScale();
        view.imageCover(texture, m.cx() - r, m.cy() - r, r * 2, r * 2, motion.echoAlpha(), motion.echoAdditive());
        view.endText();
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
