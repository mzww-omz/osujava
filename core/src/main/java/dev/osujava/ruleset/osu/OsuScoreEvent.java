package dev.osujava.ruleset.osu;

/** ScoreV1 nested points. Successful slider parts add combo; only some losses reset it. */
public enum OsuScoreEvent {
    SLIDER_HEAD(30, true),
    SLIDER_TICK(10, true),
    SLIDER_REPEAT(30, true),
    SLIDER_TAIL(30, false),
    SPINNER_SPIN(10, false),
    SPINNER_BONUS(50, false);

    private final int baseScore;
    private final boolean breaksComboOnMiss;

    OsuScoreEvent(int baseScore, boolean breaksComboOnMiss) {
        this.baseScore = baseScore;
        this.breaksComboOnMiss = breaksComboOnMiss;
    }

    public int baseScore() { return baseScore; }

    public boolean breaksComboOnMiss() { return breaksComboOnMiss; }

    public static OsuScoreEvent fromSliderEvent(SliderEvent.Type type) {
        return switch (type) {
            case TICK -> SLIDER_TICK;
            case REPEAT -> SLIDER_REPEAT;
            case TAIL -> SLIDER_TAIL;
        };
    }
}
