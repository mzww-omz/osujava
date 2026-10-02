package dev.osujava.ui;

import java.util.List;

/** Local ranking thumb capture; drawing and input use the same bounds. */
final class ScoreBrowserScroll {
    private ScoreBrowserBounds bounds;
    private List<ScoreBrowserModel.Row> rows;
    private float grab, size;
    private boolean captured;

    boolean captured() { return captured; }
    void cancel() { bounds = null; rows = null; } // Swallow the cancelled gesture through release.

    boolean press(ScoreBrowserBounds next, ScoreBrowserModel scores, float x, float y) {
        var thumb = next.thumb(scores.first(), scores.rows().size());
        if (!thumb.contains(x, y)) return false;
        bounds = next; rows = scores.rows(); size = thumb.height();
        grab = y - thumb.y(); captured = true;
        return true;
    }

    /** Release is consumed too, including outside the ranking column. No work when idle. */
    boolean update(ScoreBrowserBounds next, ScoreBrowserModel scores, float y, boolean left, boolean enabled) {
        if (!captured) return false;
        if (!enabled || !next.equals(bounds) || rows != scores.rows()) cancel();
        if (bounds != null && Float.isFinite(y)) {
            float travel = bounds.top() - bounds.bottom() - size;
            if (travel > 0) {
                float fraction = Math.max(0, Math.min(1, (bounds.top() - size - (y - grab)) / travel));
                scores.first(Math.round(fraction * Math.max(0, rows.size() - scores.capacity())));
            }
        }
        if (!left) { cancel(); captured = false; }
        return true;
    }
}
