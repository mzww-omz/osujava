package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;

/** Main Menu geometry in UiLayout units. Drawing and hit testing use the same animated polygons. */
record MainMenuLayout(float width, float height, float cx, float cy, float radius,
                      float stripLeft, float stripRight, float rowHeight, float gap) {
    static final java.util.List<String> LABELS = java.util.List.of("Play", "Exit");
    static final int ITEMS = LABELS.size();
    static final float EDGE = 24;
    static final float HOVER_EXTENSION = 9;
    static final float SLANT = 22;

    static MainMenuLayout from(UiLayout ui) {
        float r = Math.min(ui.height() * .258f, ui.width() * .205f);
        float cx = Math.max(r + EDGE * 2, ui.width() * .335f);
        return new MainMenuLayout(ui.width(), ui.height(), cx, ui.height() * .5f, r,
                cx, ui.width() * (ui.height() > ui.width() ? .94f : .87f),
                Math.min(78, Math.max(64, ui.height() * .105f)), 4);
    }

    float rowY(int index) { return cy + (ITEMS * .5f - index - 1) * (rowHeight + gap) + gap * .5f; }
    float labelX() { return cx + radius + 24; }
    float right(float reveal, float hover) { return stripLeft + (stripRight - stripLeft) * reveal + HOVER_EXTENSION * hover; }
    boolean cookieHit(float x, float y, float scale) {
        return Math.hypot(x - cx, y - cy) <= radius * scale;
    }
    int stripAt(float x, float y, float time, float[] hover, float cookieScale) {
        if (cookieHit(x, y, cookieScale)) return -1;
        for (int i = 0; i < ITEMS; i++) {
            float bottom = rowY(i), t = MainMenuMotion.strip(time, i);
            if (t <= 0 || y < bottom || y > bottom + rowHeight || x < stripLeft) continue;
            float edge = right(t, hover[i]) - SLANT * (1 - (y - bottom) / rowHeight);
            if (x <= edge) return i;
        }
        return -1;
    }
}
