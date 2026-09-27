package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import dev.osujava.ui.theme.UiView;

/** Screen-local cookie graphic. UI time is intentionally independent of the gameplay clock. */
final class OsuCookie {
    private static final Color HALO = new Color(1f, .59f, .79f, .13f);
    private static final Color RING = new Color(1f, .97f, 1f, 1f);
    private static final Color INNER = new Color(.82f, .35f, .57f, 1f);
    private static final Color HOVER = new Color(.91f, .38f, .63f, 1f);
    private static final Color PRESSED = new Color(.68f, .19f, .43f, 1f);
    private static final Color SHEEN = new Color(1f, 1f, 1f, .045f);
    private float x, y, radius;

    void bounds(float x, float y, float radius) { this.x = x; this.y = y; this.radius = radius; }

    boolean hit(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= radius * radius;
    }

    void drawShape(UiView view, float seconds, boolean hovered, boolean pressed) {
        // The app has no menu music player yet. A 60 BPM fallback keeps the cookie alive.
        float beat = (float) Math.pow(Math.max(0, Math.sin(seconds * Math.PI * 2)), 5);
        float r = radius + beat * 3 + (hovered ? 5 : 0) - (pressed ? 5 : 0);
        view.circle(x, y, r + 20, HALO);
        view.circle(x, y, r + 8, RING);
        view.circle(x, y, r - 3, pressed ? PRESSED : hovered ? HOVER : INNER);
        view.circle(x - r * .22f, y + r * .25f, r * .33f, SHEEN);
    }

    /** Main Menu presentation; the existing Song Select cookie remains unchanged. */
    void drawMainMenu(UiView view, float seconds, float hover, boolean pressed) {
        float alpha = MainMenuMotion.cookie(seconds);
        float beat = MainMenuMotion.beat(seconds);
        float r = radius * MainMenuMotion.scale(seconds, hover, pressed);
        Color tint = new Color();
        for (int i = 10; i >= 1; i--) {
            view.circle(x, y, r + i * (2.3f + beat * .3f),
                    tint.set(1, .43f, .66f, alpha * (.007f + .002f * beat + .002f * hover)));
        }
        view.circle(x, y - r * .016f, r * 1.013f, tint.set(.06f, .02f, .04f, alpha * .3f));
        tint.set(1, .98f, .99f, alpha);
        view.radialDisk(x, y, r, x, y, tint, tint);
        float inner = r * .945f;
        Color edge = new Color(pressed ? .68f : .78f, .19f + hover * .035f, .42f + hover * .045f, alpha);
        Color center = new Color(pressed ? .85f : .96f, .32f + hover * .035f, .57f + hover * .04f, alpha);
        view.radialDisk(x, y, inner, x - r * .055f, y + r * .14f, center, edge);
    }

    void drawMainMenuText(UiView view, float seconds, float hover, boolean pressed) {
        float r = radius * MainMenuMotion.scale(seconds, hover, pressed);
        view.textSmooth("osu!", x - r, y - r * .14f, r * 2, r / 23f,
                new Color(1, 1, 1, MainMenuMotion.cookie(seconds)), Align.center);
    }

    void drawText(UiView view) { drawText(view, 1f); }

    void drawText(UiView view, float scale) {
        view.textSmooth("osu!", x - radius, y - radius * .13f, radius * 2, radius / 23f * scale,
                Color.WHITE, Align.center);
    }
}
