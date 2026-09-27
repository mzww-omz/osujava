package dev.osujava.ui;

import com.badlogic.gdx.Input;

/** Only implemented actions are routed; Mode/Mods/Options can be added when they exist. */
enum SongSelectAction {
    BACK, IMPORT, RANDOM, PREVIOUS_RANDOM, PLAY, DEBUG_AUTO;
    static SongSelectAction shortcut(int key, boolean shift) {
        return switch (key) {
            case Input.Keys.ESCAPE -> BACK;
            case Input.Keys.I -> IMPORT;
            case Input.Keys.F2 -> shift ? PREVIOUS_RANDOM : RANDOM;
            case Input.Keys.ENTER, Input.Keys.SPACE -> PLAY;
            case Input.Keys.F6 -> DEBUG_AUTO;
            default -> null;
        };
    }
    static SongSelectAction bottom(float x, float y, float bottom) {
        if (y < 0 || y >= bottom) return null;
        if (x >= 0 && x < 154) return BACK;
        if (x >= 172 && x < 282) return IMPORT;
        if (x >= 298 && x < 380) return RANDOM;
        return null;
    }
}
