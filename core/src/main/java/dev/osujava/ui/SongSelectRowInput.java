package dev.osujava.ui;

import java.util.List;
import java.util.Map;

/** Background sprite priority and logical hover are separate (stable 06002c1f/03256). */
final class SongSelectRowInput {
    private final SongSelectCarousel carousel;
    private final Map<SongSelectCarousel.Row, SongSelectForegroundAnimation> foreground;
    private String hoverKey;

    SongSelectRowInput(SongSelectCarousel carousel,
                       Map<SongSelectCarousel.Row, SongSelectForegroundAnimation> foreground) {
        this.carousel = carousel;
        this.foreground = foreground;
    }

    SongSelectRow hit(List<SongSelectRow> rows, float x, float y, float width, float bottom, float top,
                      boolean preserveHover) {
        SongSelectRow previous = null, candidate = null;
        float priority = Float.POSITIVE_INFINITY, opacity = 0;
        boolean inside = SongSelectRow.inViewport(x, y, width, bottom, top);
        for (var row : rows) {
            if (!row.interactive()) continue;
            if (row.key().equals(hoverKey)) previous = row;
            if (!inside || !row.boundsContain(x, y)) continue;
            var resident = carousel.row(row.key());
            var animation = foreground.get(resident);
            if (resident == null || !resident.resident || animation == null
                    || animation.spriteGeneration() != resident.spriteGeneration || animation.baseOpacity() <= .001) continue;
            // Strict comparison preserves the first candidate on equal creation depths.
            if (resident.mousePriority < priority) {
                candidate = row; priority = resident.mousePriority; opacity = animation.baseOpacity();
            }
        }
        if (preserveHover) return previous;
        if (candidate == null) {
            hoverKey = null;
            return null;
        }
        // A fading candidate still counts as a hit. It does not clear or replace the
        // prior logical hover, even when that row no longer contains the pointer.
        if (candidate.key().equals(hoverKey) || opacity == 1) {
            hoverKey = candidate.key();
            return candidate;
        }
        if (previous == null) hoverKey = null;
        return previous;
    }

    void clear() { hoverKey = null; }
}
