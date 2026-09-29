package dev.osujava.gameplay;

/** One ordinary input snapshot, timestamped once before dispatch. Buttons retain physical sources. */
public record GameplayInput(long timeMs, long sequence, double x, double y, int buttons) {
    public static final int MOUSE_LEFT = 1, MOUSE_RIGHT = 2, KEY_LEFT = 4, KEY_RIGHT = 8;

    public GameplayInput {
        if (sequence < 0 || !Double.isFinite(x) || !Double.isFinite(y) || (buttons & ~15) != 0)
            throw new IllegalArgumentException("Invalid gameplay input");
    }

    public boolean held(GameInputAction action) {
        return (buttons & (action == GameInputAction.LEFT ? MOUSE_LEFT | KEY_LEFT : MOUSE_RIGHT | KEY_RIGHT)) != 0;
    }
}
