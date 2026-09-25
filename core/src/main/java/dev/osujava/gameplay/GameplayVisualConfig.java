package dev.osujava.gameplay;

import com.badlogic.gdx.graphics.Color;

/** Small, renderer-only collection of the default osu!standard gameplay visual values. */
public final class GameplayVisualConfig {
    private static final GameplayVisualConfig DEFAULT = new GameplayVisualConfig();

    private final Color[] comboColors = {
            new Color(0.25f, 0.82f, 0.96f, 1),
            new Color(0.98f, 0.48f, 0.72f, 1),
            new Color(0.78f, 0.95f, 0.43f, 1),
            new Color(1f, 0.73f, 0.34f, 1),
            new Color(0.69f, 0.59f, 0.98f, 1)
    };

    public final Color background = new Color(0.035f, 0.032f, 0.055f, 1);
    public final Color playfieldTint = new Color(0, 0, 0, 0.42f);
    public final Color circleBorder = new Color(0.96f, 0.94f, 1f, 0.96f);
    public final Color circleOverlay = new Color(1f, 1f, 1f, 0.16f);
    public final Color circleMiss = new Color(0.54f, 0.5f, 0.61f, 1);
    public final Color approachCircle = new Color(1f, 1f, 1f, 0.84f);
    public final Color sliderBorder = new Color(0.075f, 0.065f, 0.11f, 0.94f);
    public final Color sliderRim = new Color(0.94f, 0.9f, 1f, 0.9f);
    public final Color sliderInner = new Color(0.17f, 0.13f, 0.23f, 0.92f);
    public final Color sliderBall = new Color(1f, 1f, 1f, 0.98f);
    public final Color followFill = new Color(0.4f, 0.95f, 0.75f, 0.16f);
    public final Color followBorder = new Color(0.5f, 1f, 0.82f, 0.82f);
    public final Color spinnerField = new Color(0.105f, 0.09f, 0.15f, 0.74f);
    public final Color spinnerRing = new Color(0.92f, 0.88f, 1f, 0.72f);
    public final Color spinnerProgress = new Color(1f, 0.68f, 0.31f, 1);
    public final Color spinnerComplete = new Color(0.43f, 0.96f, 0.7f, 1);
    public final Color spinnerMiss = new Color(1f, 0.38f, 0.47f, 1);
    public final Color hudText = new Color(0.98f, 0.97f, 1f, 1);
    public final Color hudSecondary = new Color(0.78f, 0.76f, 0.84f, 1);
    public final Color hudPanel = new Color(0.035f, 0.03f, 0.055f, 0.68f);
    public final Color judgement300 = new Color(0.62f, 0.96f, 0.79f, 1);
    public final Color judgement100 = new Color(0.66f, 0.84f, 1f, 1);
    public final Color judgement50 = new Color(1f, 0.79f, 0.48f, 1);
    public final Color judgementMiss = new Color(1f, 0.49f, 0.59f, 1);

    private GameplayVisualConfig() {
    }

    public static GameplayVisualConfig defaults() {
        return DEFAULT;
    }

    public Color comboColor(int comboNumber) {
        return comboColors[Math.floorMod(Math.max(1, comboNumber) - 1, comboColors.length)];
    }

    public Color judgementColor(Judgement judgement) {
        return switch (judgement) {
            case HIT300 -> judgement300;
            case HIT100 -> judgement100;
            case HIT50 -> judgement50;
            case MISS -> judgementMiss;
        };
    }
}
