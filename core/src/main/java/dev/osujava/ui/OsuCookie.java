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

    void drawText(UiView view) { drawText(view, 1f); }

    void drawText(UiView view, float scale) {
        view.textSmooth("osu!", x - radius, y - radius * .13f, radius * 2, radius / 23f * scale,
                Color.WHITE, Align.center);
    }
}
