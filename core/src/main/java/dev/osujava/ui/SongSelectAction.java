package dev.osujava.ui;

import com.badlogic.gdx.Input;

import dev.osujava.skin.SongSelectSkinAssets.Selection;

/** Options routes to explicit unavailable feedback until the real Beatmap Options exists. */
enum SongSelectAction {
    BACK, IMPORT, MODE, MODS, RANDOM, PREVIOUS_RANDOM, OPTIONS, PLAY, DEBUG_AUTO;
    static SongSelectAction shortcut(int key, boolean shift) {
        return switch (key) {
            case Input.Keys.ESCAPE -> BACK;
            case Input.Keys.I -> IMPORT;
            case Input.Keys.F1 -> MODS;
            case Input.Keys.F2 -> shift ? PREVIOUS_RANDOM : RANDOM;
            case Input.Keys.F3 -> OPTIONS;
            case Input.Keys.ENTER, Input.Keys.SPACE -> PLAY;
            case Input.Keys.F6 -> DEBUG_AUTO;
            default -> null;
        };
    }
    static SongSelectAction bottom(float x, float y, SongSelectToolboxLayout layout) {
        if (layout.backInteraction.contains(x,y)) return BACK;
        if (layout.importAction.contains(x,y)) return IMPORT;
        for (var action : Selection.values()) if (layout.control(action).interaction().contains(x,y))
            return valueOf(action.name());
        return null;
    }
}
