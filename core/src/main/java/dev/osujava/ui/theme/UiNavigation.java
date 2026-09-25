package dev.osujava.ui.theme;

/** Defers one screen action briefly so pressed feedback and a fade can be seen. */
public final class UiNavigation {
    private static final float DURATION = 0.12f;
    private Runnable action;
    private float elapsed;

    public void request(Runnable next) {
        if (action != null) return;
        action = next;
        elapsed = 0;
    }
    public boolean pending() { return action != null; }
    public float opacity() { return action == null ? 0 : Math.min(1, elapsed / DURATION); }
    /** Returns true if the queued action ran and the former screen must stop rendering. */
    public boolean advance(float delta) {
        if (action == null) return false;
        elapsed += Math.max(0, delta);
        if (elapsed < DURATION) return false;
        Runnable next = action;
        action = null;
        next.run();
        return true;
    }
}
