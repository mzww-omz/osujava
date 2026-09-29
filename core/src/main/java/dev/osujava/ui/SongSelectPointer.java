package dev.osujava.ui;

/** A row click commits on release over the pressed identity unless movement cancels it. */
final class SongSelectPointer {
    // 06003253: squared distance > 6400, in window pixels before display scaling.
    private static final float CANCEL_DISTANCE_SQUARED = 6400;
    private String pressedKey;
    private float startX, startY;
    private boolean dragged;

    void press(String key, float x, float y) {
        pressedKey = key; pressPosition(x, y);
    }
    void pressPosition(float x, float y) { startX = x; startY = y; }
    String pressedKey() { return pressedKey; }
    void sample(float x, float y) {
        float dx = x - startX, dy = y - startY;
        dragged |= dx * dx + dy * dy > CANCEL_DISTANCE_SQUARED;
    }
    String release(boolean insidePressedRow, boolean leftHeld) {
        // 06003244 tests the pressed sprite itself, not the frontmost release hit.
        String clicked = !dragged && insidePressedRow ? pressedKey : null;
        // A failed release keeps the candidate; cancellation resets only with left up.
        if (!leftHeld) dragged = false;
        if (clicked != null) pressedKey = null;
        return clicked;
    }
    void cancel() { pressedKey = null; dragged = false; }
}
