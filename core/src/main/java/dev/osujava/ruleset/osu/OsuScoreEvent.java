package dev.osujava.ruleset.osu;

/** Base scores from lazer ScoreProcessor for osu!standard nested events. */
public enum OsuScoreEvent {
    SLIDER_TICK(30, true),
    SLIDER_REPEAT(30, true),
    SLIDER_TAIL(150, true),
    SPINNER_SPIN(10, false),
    SPINNER_BONUS(50, false);

    private final int baseScore;
    private final boolean affectsCombo;

    OsuScoreEvent(int baseScore, boolean affectsCombo) {
        this.baseScore = baseScore;
        this.affectsCombo = affectsCombo;
    }

    public int baseScore() { return baseScore; }

    public boolean affectsCombo() { return affectsCombo; }

    public static OsuScoreEvent fromSliderEvent(SliderEvent.Type type) {
        return switch (type) {
            case TICK -> SLIDER_TICK;
            case REPEAT -> SLIDER_REPEAT;
            case TAIL -> SLIDER_TAIL;
        };
    }
}
