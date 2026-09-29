package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;

/** Disjoint from carousel, metadata and bottom chrome, in shared virtual coordinates. */
public record ScoreBrowserBounds(float x, float bottom, float width, float top) {
    public static final float HEIGHT = 64, PITCH = 68;
    static float columnWidth(UiLayout layout) {
        return Math.min(layout.width() * .35f, 700 * .55f * layout.height() / SongSelectMetrics.LEGACY_CANVAS_HEIGHT);
    }
    public static ScoreBrowserBounds of(UiLayout layout) {
        return new ScoreBrowserBounds(18, SongSelectChrome.bottomHeight(layout.height()) + 48,
                columnWidth(layout), layout.height() - 194);
    }
    public boolean contains(float px, float py) { return px >= x && px < x + width && py > bottom && py < top; }
    public int capacity() { return Math.max(1, (int) ((top - bottom) / PITCH)); }
    public float rowY(int slot) { return top - slot * PITCH - HEIGHT; }
    public int slot(float px, float py) {
        if (!contains(px, py)) return -1;
        int slot = (int) ((top - py) / PITCH);
        return slot < capacity() && py >= rowY(slot) ? slot : -1;
    }
}
