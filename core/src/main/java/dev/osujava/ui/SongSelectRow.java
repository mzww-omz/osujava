package dev.osujava.ui;

/** Immutable shared drawing/input geometry. Coordinates have already incorporated all motion. */
record SongSelectRow(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
                     float x, float y, float width, float height, float hoverAmount, float revealAmount,
                     int logicalIndex, float targetX, float targetY, String key, boolean groupExpanded, float focusAmount,
                     SongSelectChrome.Bounds interaction) {
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
        // Missing artwork shares the existing procedural body, without an extra bounds allocation.
        if (interaction == null) return px >= x && px < x + width && py > y && py <= y + height;
        return px >= interaction.x() && px < interaction.x() + interaction.width()
                && py > interaction.y() && py <= interaction.y() + interaction.height();
    }

    static boolean inViewport(float x, float y, float width, float bottom, float top) {
        return x >= 0 && x < width && y > bottom && y < top;
    }

}
