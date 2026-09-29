package dev.osujava.ui;

/** A row click commits on release over the pressed identity unless movement cancels it. */
final class SongSelectPointer {
    // Independent local drag slop in logical UI units; not a recovered Stable threshold.
    private static final float DRAG_SLOP = 6;
    private String pressedKey;
    private float startX, startY;
    private boolean dragged;

    void press(String key, float x, float y) {
        pressedKey = key; startX = x; startY = y; dragged = false;
    }
    String update(boolean held, String hitKey, float x, float y) {
        if (pressedKey == null) return null;
        float dx = x - startX, dy = y - startY;
        dragged |= dx * dx + dy * dy > DRAG_SLOP * DRAG_SLOP;
        if (held) return null;
        String clicked = !dragged && pressedKey.equals(hitKey) ? pressedKey : null;
        cancel();
        return clicked;
    }
    void cancel() { pressedKey = null; dragged = false; }
}
