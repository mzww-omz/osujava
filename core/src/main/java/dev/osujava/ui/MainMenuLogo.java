package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Align;
import dev.osujava.ui.theme.UiView;

/** Main Menu only; Song Select retains its existing OsuCookie. Pure drawing of supplied layers. */
final class MainMenuLogo {
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
    void shape(UiView view, MainMenuLayout m, MainMenuModel model) {
        float r = m.radius() * model.scale();
        for (int i = 10; i > 0; i--) view.circle(m.cx(), m.cy(), r + i * 2.2f, new Color(1, .4f, .65f, .01f));
        Color ring = new Color(1, .98f, .99f, 1);
        view.radialDisk(m.cx(), m.cy(), r, m.cx(), m.cy(), ring, ring);
        Color edge = new Color(.78f, .19f, .42f, 1), centre = new Color(.96f, .32f, .57f, 1);
        if (model.pressed()) { edge.mul(.9f, .9f, .9f, 1); centre.mul(.9f, .9f, .9f, 1); }
        view.radialDisk(m.cx(), m.cy(), r * .945f, m.cx() - r * .055f, m.cy() + r * .14f, centre, edge);
        if (model.flash() > .001f) view.circle(m.cx(), m.cy(), r * .945f, new Color(1, 1, 1, model.flash()));
    }
    void text(UiView view, MainMenuLayout m, MainMenuModel model) {
        float r = m.radius() * model.scale();
        view.textSmooth("osu!", m.cx() - r, m.cy() + r * .08f, r * 2, r / 23f, Color.WHITE, Align.center);
        view.textSmooth("java", m.cx() - r, m.cy() - r * .31f, r * 2, r / 72f, Color.WHITE, Align.center);
    }
}
