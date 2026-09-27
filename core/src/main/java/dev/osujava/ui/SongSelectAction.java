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
        return bottom(x, y, SongSelectChrome.bottom(1280, 720, null));
    }
    static SongSelectAction bottom(float x, float y, SongSelectChrome.Bottom layout) {
        if (contains(layout.back(), x, y)) return BACK;
        if (contains(layout.importAction(), x, y)) return IMPORT;
        if (contains(layout.random(), x, y)) return RANDOM;
        return null;
    }
    private static boolean contains(SongSelectChrome.Bounds bounds, float x, float y) {
        return x >= bounds.x() && x < bounds.x() + bounds.width()
                && y >= bounds.y() && y < bounds.y() + bounds.height();
    }
}
