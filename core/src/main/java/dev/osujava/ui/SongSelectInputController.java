package dev.osujava.ui;

/** Commands and pointer gestures share one owner, independent from drawing. */
final class SongSelectInputController extends SongSelectInput {
    enum PointerState { IDLE, HOVER, PRESSED, DRAGGING, RELEASED }
    private static final float DRAG_THRESHOLD = 6; // Local slop in logical UI units; stable value unmeasured.
    private final SongSelectPointer clicks = new SongSelectPointer();
    private PointerState state = PointerState.IDLE;
    private boolean active;
    private float startX, startY, lastY;

    SongSelectInputController(SongSelectToolboxState toolbox, SongBrowserControls controls, Target target) {
        super(toolbox, controls, target);
    }
    PointerState pointerState() { return state; }
    void pressRow(String key, float x, float y) {
        cancelPointer();
        if (key == null) return;
        active = true; startX = x; startY = lastY = y;
        state = PointerState.PRESSED;
        clicks.press(key, x, y);
    }
    String pointer(boolean held, String hitKey, float x, float y, SongSelectCarousel carousel) {
        if (!active) { state = hitKey == null ? PointerState.IDLE : PointerState.HOVER; return null; }
        float dx = x - startX, dy = y - startY;
        if (state != PointerState.DRAGGING && dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD)
            state = PointerState.DRAGGING;
        if (state == PointerState.DRAGGING) carousel.dragBy(y - lastY);
        lastY = y;
        String clicked = clicks.update(held, hitKey, x, y);
        if (!held) { active = false; state = PointerState.RELEASED; }
        return clicked;
    }
    void cancelPointer() { active = false; state = PointerState.IDLE; clicks.cancel(); }
}
