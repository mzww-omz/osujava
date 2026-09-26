package dev.osujava.ui;

import dev.osujava.skin.LegacyHudLayout;

/** LegacySkin default layout in the full window, independent of the object playfield. */
public record LegacyHudPlacement(float unit, float scoreRight, float scoreTop, float accuracyRight,
                                 float accuracyTop, float progressRight, float progressCentreY,
                                 float comboLeft, float comboBottom) {
    public static final float SCORE_SCALE = 0.96f;
    public static final float ACCURACY_SCALE = 0.6f * 0.96f;
    public static final float COMBO_SCALE = 1.28f;

    public static LegacyHudPlacement fit(float width, float height, LegacyHudLayout initialScore, LegacyHudLayout initialAccuracy) {
        // Drawable margins are part of OriginPosition and are scaled with the component.
        float unit = Math.min(width / 1024, height / 768);
        float accuracyTop = height - (initialScore.height() * SCORE_SCALE + 9 * ACCURACY_SCALE) * unit;
        return new LegacyHudPlacement(unit, width - 10 * SCORE_SCALE * unit, height, width - 17 * ACCURACY_SCALE * unit,
                accuracyTop, width - (initialAccuracy.width() * ACCURACY_SCALE + 18) * unit,
                accuracyTop - initialAccuracy.height() * ACCURACY_SCALE * unit / 2, 10 * COMBO_SCALE * unit, 10 * COMBO_SCALE * unit);
    }

    /** Combo custom origin/position from LegacyDefaultComboCounter.updateLayout. */
    public static float comboTop(float bottom, float height, float baseScale, float popScale) {
        return bottom + ((1 - 0.625f) * height - 9) * baseScale
                + (0.625f * height + 9) * baseScale * popScale;
    }
}
