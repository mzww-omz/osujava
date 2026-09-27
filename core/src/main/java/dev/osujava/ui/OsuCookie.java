package dev.osujava.ui;

import dev.osujava.ui.theme.UiView;

/** Screen-local cookie graphic. UI time is intentionally independent of the gameplay clock. */
final class OsuCookie implements AutoCloseable {
    private MainMenuLogo logo;
    private float x, y, radius, interactionTop;

    void loadGraphics() { if (logo == null) logo = new MainMenuLogo(); }

    void bounds(float x, float y, float radius, float interactionTop) {
        this.x = x; this.y = y; this.radius = radius; this.interactionTop = interactionTop;
    }

    boolean hit(float px, float py) {
        float dx = px - x, dy = py - y;
        // The artwork overlaps the carousel, whose entire viewport keeps its input.
        return py >= 0 && py <= interactionTop && dx * dx + dy * dy <= radius * radius;
    }

    void draw(UiView view, float seconds, boolean hovered, boolean pressed) {
        // Song Select uses a 60 BPM fallback for its UI pulse.
        float beat = (float) Math.pow(Math.max(0, Math.sin(seconds * Math.PI * 2)), 5);
        float r = radius + beat * 3 + (hovered ? 5 : 0) - (hovered && pressed ? 5 : 0);
        logo.draw(view, x, y, r, 0);
    }

    @Override public void close() {
        if (logo != null) logo.close();
        logo = null;
    }
}
