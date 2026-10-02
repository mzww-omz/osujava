package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;

/** Disjoint from carousel, metadata and bottom chrome, in shared virtual coordinates. */
public record ScoreBrowserBounds(float x, float bottom, float width, float top, float scale) {
    // 060013b3 advances score centres by 33 in the 480-high space.
    public static final float HEIGHT = 45, PITCH = 49.5f;
    public ScoreBrowserBounds(float x, float bottom, float width, float top) { this(x, bottom, width, top, 1); }
    static final float LEFT = 18, COLUMN_GAP = 16;
    static float columnWidth(UiLayout layout) {
        float available = Math.max(0, SongSelectMetrics.wheelLeft(layout.width(), layout.height()) - LEFT - COLUMN_GAP);
        return Math.min(available, Math.min(layout.width() * .35f,
                700 * .55f * layout.height() / SongSelectMetrics.LEGACY_CANVAS_HEIGHT));
    }
    public static ScoreBrowserBounds of(UiLayout layout) {
        return SongSelectLayout.create(layout, null).scores();
    }
    public boolean contains(float px, float py) { return px >= x && px < x + width && py > bottom && py < top; }
    public float rowHeight() { return HEIGHT * scale; }
    public float rowPitch() { return PITCH * scale; }
    public int capacity() { return width <= 0 ? 0 : Math.max(0, (int) ((top - bottom + rowPitch() - rowHeight()) / rowPitch())); }
    public float rowY(int slot) { return top - slot * rowPitch() - rowHeight(); }
    SongSelectChrome.Bounds rowClip(int slot) { return new SongSelectChrome.Bounds(x, rowY(slot), width, rowHeight()); }
    SongSelectChrome.Bounds clip() { return new SongSelectChrome.Bounds(x, bottom, width, Math.max(0, top - bottom)); }
    SongSelectLayout.Rect thumb(int first, int count) {
        float height = Math.max(0, top - bottom), size = count <= capacity() || capacity() == 0 ? 0
                : Math.min(height, Math.max(18 * scale, height * capacity() / count));
        float fraction = count <= capacity() ? 0 : Math.max(0, Math.min(1, first / (float) (count - capacity())));
        return new SongSelectLayout.Rect(x + width - 3 * scale, top - size - fraction * (height - size), 3 * scale, size);
    }
    public int slot(float px, float py) {
        if (!contains(px, py)) return -1;
        int slot = (int) ((top - py) / rowPitch());
        return slot < capacity() && py >= rowY(slot) ? slot : -1;
    }
}
