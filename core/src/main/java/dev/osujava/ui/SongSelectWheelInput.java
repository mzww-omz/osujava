package dev.osujava.ui;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;

/** Collect wheel deltas before routing one direction at the next Song Select input update. */
public final class SongSelectWheelInput extends InputMultiplexer {
    private double pending;

    public SongSelectWheelInput(InputProcessor input) { super(input); }

    @Override public boolean scrolled(float amountX, float amountY) {
        if (!Float.isFinite(amountY) || amountY == 0) return false;
        pending += amountY;
        return true;
    }

    void dispatch() {
        // Stable 0600409a/409e: sum deltas, notify once by sign, discard magnitude.
        // A double accumulator also keeps finite libGDX float batches from overflowing.
        float direction = (float) Math.signum(pending);
        pending = 0;
        if (direction != 0) super.scrolled(0, direction);
    }

    void cancel() { pending = 0; }
}
