package dev.osujava.ui;

import java.util.List;

/** Immutable shared drawing/input geometry. Coordinates have already incorporated all motion. */
record SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                     float x, float y, float width, float height, float hoverAmount, float revealAmount,
                     int logicalIndex, float targetX, float targetY, String key, boolean groupExpanded, float focusAmount) {
    SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                  float x, float y, float width, float height, float hoverAmount, float revealAmount,
                  int logicalIndex, float targetX, float targetY, String key, boolean groupExpanded) {
        this(setIndex, difficultyIndex, header, selected, sibling, x, y, width, height,
                hoverAmount, revealAmount, logicalIndex, targetX, targetY, key, groupExpanded, 0);
    }
    SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                  float x, float y, float width, float height, float hoverAmount, float revealAmount,
                  int logicalIndex, float targetX, float targetY) {
        this(setIndex, difficultyIndex, header, selected, sibling, x, y, width, height,
                hoverAmount, revealAmount, logicalIndex, targetX, targetY, null, false);
    }
    boolean group() { return setIndex < 0 && key != null && header != null; }
    boolean interactive() { return setIndex >= 0 || group(); }
    SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                  float x, float y, float width, float height, float hoverAmount, float revealAmount) {
        this(setIndex, difficultyIndex, header, selected, sibling, x, y, width, height,
                hoverAmount, revealAmount, -1, x, y);
    }
    boolean contains(float px, float py) {
        return interactive() && revealAmount >= .05f && boundsContain(px, py);
    }
    boolean boundsContain(float px, float py) {
        // Native top-left rectangle includes left/top and excludes right/bottom; Java Y is up.
        return px >= x && px < x + width && py > y && py <= y + height;
    }

    static SongSelectRow hit(List<SongSelectRow> rows, float x, float y, float bottom, float top) {
        if (y <= bottom || y >= top) return null;
        // Existing input approximation; native sprite hit/depth arbitration is deferred to phase 8b.
        // This priority is independent of the browser-order draw pass.
        for (var row : rows) if (row.selected && row.contains(x, y)) return row;
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).contains(x, y)) return rows.get(i);
        return null;
    }
}
