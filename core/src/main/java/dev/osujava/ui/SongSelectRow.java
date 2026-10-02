package dev.osujava.ui;

import java.util.List;

/** Immutable shared drawing/input geometry. Coordinates have already incorporated all motion. */
record SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                     float x, float y, float width, float height, float hoverAmount, float revealAmount,
                     int logicalIndex, float targetX, float targetY, String key, boolean groupExpanded, float focusAmount,
                     SongSelectChrome.Bounds interaction) {
    SongSelectRow {
        if (interaction == null) interaction = new SongSelectChrome.Bounds(x, y, width, height);
    }
    SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                  float x, float y, float width, float height, float hoverAmount, float revealAmount,
                  int logicalIndex, float targetX, float targetY, String key, boolean groupExpanded, float focusAmount) {
        this(setIndex, difficultyIndex, header, selected, sibling, x, y, width, height,
                hoverAmount, revealAmount, logicalIndex, targetX, targetY, key, groupExpanded, focusAmount, null);
    }
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
        return px >= interaction.x() && px < interaction.x() + interaction.width()
                && py > interaction.y() && py <= interaction.y() + interaction.height();
    }

    static boolean inViewport(float x, float y, float width, float bottom, float top) {
        return x >= 0 && x < width && y > bottom && y < top;
    }

    static SongSelectRow hit(List<SongSelectRow> rows, float x, float y, float bottom, float top) {
        if (y <= bottom || y >= top) return null;
        // Existing arbitration: native negative creation-depth priority and full-opacity
        // hover acquisition must be applied together, including sprite lifetime across re-sort.
        // Geometry already shares the rendered background canvas.
        for (var row : rows) if (row.selected && row.contains(x, y)) return row;
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).contains(x, y)) return rows.get(i);
        return null;
    }
}
