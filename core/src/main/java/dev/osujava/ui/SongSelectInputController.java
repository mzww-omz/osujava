package dev.osujava.ui;

/** Commands and pointer gestures share one owner, independent from drawing. */
final class SongSelectInputController extends SongSelectInput {
    enum PointerState { IDLE, HOVER, PRESSED, DRAGGING, RELEASED }
    private final SongSelectPointer clicks = new SongSelectPointer();
    private PointerState state = PointerState.IDLE;
    record Buttons(boolean left, boolean right, boolean leftPressed, boolean pressed,
                   boolean released, boolean context) { }
    private boolean previousLeft, previousRight, previousMiddle, active, rightScrolling;
    private SongSelectCarousel dragOwner;
    private String hoverKey;
    private float startX, startY, lastY;

    SongSelectInputController(SongSelectToolboxState toolbox, SongBrowserControls controls, Target target) {
        super(toolbox, controls, target);
    }
    PointerState pointerState() { return state; }
    String pressedKey() { return clicks.pressedKey(); }
    boolean rightScrolling() { return rightScrolling; }
    /** 06002af9: one down/up notification per snapshot, including mixed-button edges. */
    Buttons buttons(boolean left, boolean right, boolean middle) {
        var result = new Buttons(left, right, left && !previousLeft,
                left && !previousLeft || right && !previousRight || middle && !previousMiddle,
                !left && previousLeft || !right && previousRight || !middle && previousMiddle,
                previousRight);
        previousLeft = left; previousRight = right; previousMiddle = middle;
        return result;
    }

    /** Generic down captures a row but does not start or restart a left drag. Coordinates are window pixels. */
    void pressPointer(String hitKey, float x, float y, boolean rightHeld, SongSelectCarousel carousel) {
        carousel.pointerPressed();
        if (!active && !rightScrolling) hoverKey = hitKey;
        clicks.press(hoverKey, x, y);
        startX = x; startY = y;
        if (hoverKey == null && rightHeld) rightScrolling = true;
        if (!active) state = PointerState.PRESSED;
    }

    /** Generic up runs before this frame's motion sample, even when another button remains held. */
    String releasePointer(boolean leftHeld, boolean insidePressedRow) {
        String clicked = clicks.release(insidePressedRow, leftHeld);
        if (!leftHeld) {
            if (dragOwner != null) dragOwner.releaseDrag();
            dragOwner = null; active = false; state = PointerState.RELEASED;
        }
        return clicked;
    }

    void samplePointer(Buttons buttons, float x, float y, float uiScale, float referenceX,
                       float referenceY, float delta, SongSelectCarousel carousel) {
        if (buttons.left()) {
            if (active && delta > 0) dragOwner.drag((lastY - y) / uiScale, delta);
            // 06003253 starts the drag after sampling existing motion. The first frame adds no time.
            if (buttons.leftPressed() && hoverKey != null) {
                dragOwner = carousel; active = true; carousel.beginDrag(hoverKey);
            }
            if (active) state = x != startX || y != startY ? PointerState.DRAGGING : PointerState.PRESSED;
        }
        lastY = y;
        if (!buttons.right()) rightScrolling = false;
        if ((buttons.left() || buttons.right()) && carousel.pointerCancellationEnabled(referenceX)) clicks.sample(x, y);
        if (rightScrolling && !buttons.left()) carousel.rightScroll(referenceX, referenceY);
    }

    void cancelPointer() {
        if (dragOwner != null) dragOwner.cancelDrag();
        dragOwner = null; hoverKey = null; active = false; rightScrolling = false;
        state = PointerState.IDLE; clicks.cancel();
    }
    void cancel() { cancelKeys(); cancelPointer(); }
}
