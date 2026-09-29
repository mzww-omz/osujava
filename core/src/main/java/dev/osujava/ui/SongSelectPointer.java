package dev.osujava.ui;

/** A row click commits on release over the pressed identity unless movement cancels it. */
final class SongSelectPointer {
    // 06003253: squared distance > 6400, in window pixels before display scaling.
    private static final float CANCEL_DISTANCE_SQUARED = 6400;
    private String pressedKey;
    private float startX, startY;
    private boolean dragged;

    void press(String key, float x, float y) {
        pressedKey = key; startX = x; startY = y; dragged = false;
    }
    String pressedKey() { return pressedKey; }
    String update(boolean held, boolean insidePressedRow, float x, float y) {
        if (pressedKey == null) return null;
        if (held) {
            float dx = x - startX, dy = y - startY;
            dragged |= dx * dx + dy * dy > CANCEL_DISTANCE_SQUARED;
            return null;
        }
        // 06003244 tests the pressed sprite itself, not the frontmost release hit.
        String clicked = !dragged && insidePressedRow ? pressedKey : null;
        cancel();
        return clicked;
    }
    void cancel() { pressedKey = null; dragged = false; }
}
