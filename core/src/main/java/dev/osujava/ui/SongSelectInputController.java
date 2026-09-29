package dev.osujava.ui;

/** Commands and pointer gestures share one owner, independent from drawing. */
final class SongSelectInputController extends SongSelectInput {
    enum PointerState { IDLE, HOVER, PRESSED, DRAGGING, RELEASED }
    private final SongSelectPointer clicks = new SongSelectPointer();
    private PointerState state = PointerState.IDLE;
    private boolean active, firstSample;
    private SongSelectCarousel dragOwner;
    private float startX, startY, lastY;

    SongSelectInputController(SongSelectToolboxState toolbox, SongBrowserControls controls, Target target) {
        super(toolbox, controls, target);
    }
    PointerState pointerState() { return state; }
    void pressRow(String key, float x, float y, SongSelectCarousel carousel) {
        cancelPointer();
        if (key == null) return;
        active = true; firstSample = true; dragOwner = carousel;
        carousel.beginDrag(); startX = x; startY = lastY = y;
        state = PointerState.PRESSED;
        clicks.press(key, x, y);
    }
    String pointer(boolean held, String hitKey, float x, float y, float delta) {
        if (!active) { state = hitKey == null ? PointerState.IDLE : PointerState.HOVER; return null; }
        float dx = x - startX, dy = y - startY;
        if (state != PointerState.DRAGGING && (dx != 0 || dy != 0))
            state = PointerState.DRAGGING;
        float distance = y - lastY;
        // Polling adaptation: sample a final release movement, but not a stationary release interval.
        if (held || distance != 0)
            dragOwner.drag(distance, firstSample && distance == 0 ? 0 : delta);
        firstSample = false;
        lastY = y;
        String clicked = clicks.update(held, hitKey, x, y);
        if (!held) { dragOwner.releaseDrag(); dragOwner = null; active = false; state = PointerState.RELEASED; }
        return clicked;
    }
    void cancelPointer() {
        if (dragOwner != null) dragOwner.cancelDrag();
        dragOwner = null; active = false; state = PointerState.IDLE; clicks.cancel();
    }
}
