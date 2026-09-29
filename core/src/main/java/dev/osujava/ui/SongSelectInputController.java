package dev.osujava.ui;

/** Commands and pointer gestures share one owner, independent from drawing. */
final class SongSelectInputController extends SongSelectInput {
    enum PointerState { IDLE, HOVER, PRESSED, DRAGGING, RELEASED }
    private final SongSelectPointer clicks = new SongSelectPointer();
    private PointerState state = PointerState.IDLE;
    private boolean active, firstSample, rightScrolling;
    private SongSelectCarousel dragOwner;
    private float startX, startY, lastY;

    SongSelectInputController(SongSelectToolboxState toolbox, SongBrowserControls controls, Target target) {
        super(toolbox, controls, target);
    }
    PointerState pointerState() { return state; }
    String pressedKey() { return clicks.pressedKey(); }
    boolean rightScrolling() { return rightScrolling; }
    /** Pointer coordinates are window pixels (Y down), never framebuffer or logical units. */
    void pressRow(String key, float x, float y, SongSelectCarousel carousel) {
        cancelLeftPointer();
        if (key == null) return;
        active = true; firstSample = true; dragOwner = carousel;
        carousel.beginDrag(key); startX = x; startY = lastY = y;
        state = PointerState.PRESSED;
        clicks.press(key, x, y);
    }
    String pointer(boolean held, boolean insidePressedRow, float x, float y, float uiScale, float delta) {
        if (!active) { state = insidePressedRow ? PointerState.HOVER : PointerState.IDLE; return null; }
        float dx = x - startX, dy = y - startY;
        if (state != PointerState.DRAGGING && (dx != 0 || dy != 0))
            state = PointerState.DRAGGING;
        float distance = (lastY - y) / uiScale;
        // 06003253 samples motion only while held and time advances. Always update lastY.
        if (held && delta > 0)
            dragOwner.drag(distance, firstSample && distance == 0 ? 0 : delta);
        firstSample = false;
        lastY = y;
        String clicked = clicks.update(held, insidePressedRow, x, y);
        if (!held) { dragOwner.releaseDrag(); dragOwner = null; active = false; state = PointerState.RELEASED; }
        return clicked;
    }
    void pressRight(boolean overRow) { rightScrolling = !overRow; }
    void rightPointer(boolean held, boolean leftHeld, float referenceX, float referenceY, SongSelectCarousel carousel) {
        if (!held) rightScrolling = false;
        if (rightScrolling && !leftHeld) carousel.rightScroll(referenceX, referenceY);
    }
    void cancelLeftPointer() {
        if (dragOwner != null) dragOwner.cancelDrag();
        dragOwner = null; active = false; state = PointerState.IDLE; clicks.cancel();
    }
    void cancelPointer() { cancelLeftPointer(); rightScrolling = false; }
    void cancel() { cancelKeys(); cancelPointer(); }
}
