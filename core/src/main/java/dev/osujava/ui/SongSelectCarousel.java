package dev.osujava.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UI-only content coordinates (downwards), viewport and transient visual offsets. */
final class SongSelectCarousel {
    record Entry(String key, int setIndex, int difficultyIndex) { }
    static final class Row {
        final Entry entry;
        final float logicalY;
        float hoverAmount, separationY, selectedAmount;
        Row(Entry entry, float logicalY) { this.entry = entry; this.logicalY = logicalY; }
    }

    private List<Row> rows = List.of();
    private final Map<String, Row> byKey = new HashMap<>();
    private String selectedKey, hoverKey;
    private float hoverAbsence, viewportHeight, rowHeight = 76, scrollOffset, scrollTarget, maxScroll;
    private boolean initialized;

    List<Row> rows() { return rows; }
    float scrollOffset() { return scrollOffset; }
    float scrollTarget() { return scrollTarget; }
    float maxScroll() { return maxScroll; }
    float rowHeight() { return rowHeight; }

    /** Only content/filter/size changes call this; selection within an expanded set does not. */
    void content(List<Entry> entries, float height, float size, float step, String selection) {
        Row anchor = byKey.get(selection);
        if (anchor == null) {
            // A collapsed Set is replaced by its children: keep the chosen child at that Set's old Y.
            for (Entry entry : entries) if (entry.key().equals(selection)) {
                anchor = byKey.get(entry.key().substring(0, entry.key().lastIndexOf('#')) + "#-1");
                break;
            }
        }
        float anchorY = anchor == null ? 0 : anchor.logicalY - scrollOffset;
        Map<String, Row> previous = new HashMap<>(byKey);
        byKey.clear();
        viewportHeight = Math.max(1, height);
        rowHeight = size;
        List<Row> next = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Row row = new Row(entry, viewportHeight / 2 + i * step);
            Row old = previous.get(entry.key());
            if (old != null) {
                row.hoverAmount = old.hoverAmount;
                row.separationY = old.separationY;
                row.selectedAmount = old.selectedAmount;
            }
            next.add(row);
            byKey.put(entry.key(), row);
        }
        rows = List.copyOf(next);
        // Half-viewport end padding allows the first/last row to center even in a short result.
        maxScroll = Math.max(0, (entries.size() - 1) * step);
        Row newAnchor = byKey.get(selection);
        if (initialized && anchor != null && newAnchor != null)
            scrollOffset = newAnchor.logicalY - anchorY;
        scrollOffset = clamp(scrollOffset);
        select(selection);
        if (!initialized) {
            scrollOffset = scrollTarget;
            if (newAnchor != null) newAnchor.selectedAmount = 1;
            initialized = true;
        }
        if (!byKey.containsKey(hoverKey)) { hoverKey = null; hoverAbsence = 0; }
    }

    void select(String key) {
        selectedKey = key;
        Row selected = byKey.get(key);
        scrollTarget = clamp(selected == null ? scrollOffset : selected.logicalY - viewportHeight / 2);
    }

    /** Wheel browsing moves the viewport independently of selection and Set expansion. */
    void scrollBy(float distance) {
        if (Float.isFinite(distance)) scrollTarget = clamp(scrollTarget + distance);
    }

    /** Briefly retain hover across the gaps created by separation, avoiding reset flicker. */
    void advance(float delta, String hitKey) {
        float dt = Float.isFinite(delta) ? Math.max(0, delta) : 0;
        if (hitKey != null && byKey.containsKey(hitKey)) { hoverKey = hitKey; hoverAbsence = 0; }
        else if ((hoverAbsence += dt) >= .075f) hoverKey = null;
        float scrollEase = ease(dt, 11), hoverEase = ease(dt, 19), releaseEase = ease(dt, 13);
        scrollOffset = clamp(scrollOffset + (scrollTarget - scrollOffset) * scrollEase);
        Row hovered = byKey.get(hoverKey);
        for (Row row : rows) {
            boolean hover = row == hovered;
            row.hoverAmount += ((hover ? 1 : 0) - row.hoverAmount) * (hover ? hoverEase : releaseEase);
            float separation = hovered == null ? 0 : Math.signum(hovered.logicalY - row.logicalY) * 9;
            row.separationY += (separation - row.separationY) * releaseEase;
            row.selectedAmount += ((row.entry.key().equals(selectedKey) ? 1 : 0) - row.selectedAmount) * releaseEase;
        }
    }

    float renderY(Row row, float top) { return top - (row.logicalY - scrollOffset) - rowHeight / 2 + row.separationY; }
    float renderX(Row row, float width) {
        float normalized = (row.logicalY - scrollOffset - viewportHeight / 2) / (viewportHeight / 2);
        return curveX(normalized, width) - 16 * row.selectedAmount - 11 * row.hoverAmount;
    }

    /** A smooth bounded arch, designed for osujava rather than sampled from another client. */
    static float curveX(float distance, float width) {
        float squared = distance * distance;
        return width * (.57f + .15f * squared / (1 + squared));
    }

    static float skinRowHeight(float logicalWidth, float logicalHeight, float carouselWidth) {
        float aspect = logicalWidth / logicalHeight;
        if (!Float.isFinite(aspect) || aspect < 2.5f || aspect > 12) aspect = 6;
        return Math.max(68, Math.min(110, carouselWidth / aspect));
    }
    private float clamp(float value) { return Math.max(0, Math.min(maxScroll, value)); }
    private static float ease(float dt, float rate) { return (float) -Math.expm1(-dt * rate); }
}
