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

    void draw(UiView view, float beat, boolean hovered, boolean pressed) {
        float r = radius + beat * radius * .018f + (hovered ? radius * .035f : 0) - (hovered && pressed ? radius * .035f : 0);
        logo.draw(view, x, y, r, 0);
    }

    @Override public void close() {
        if (logo != null) logo.close();
        logo = null;
    }
}
