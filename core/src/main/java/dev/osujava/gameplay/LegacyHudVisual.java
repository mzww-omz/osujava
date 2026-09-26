package dev.osujava.gameplay;

/** Immutable presentation values. None of these values participate in scoring or judgement. */
public record LegacyHudVisual(long score, double accuracy, int combo, double comboAlpha,
                              double comboScale, int popCombo, double popScale, double popAlpha) {
    public static LegacyHudVisual immediate(ScoreState score) {
        return new LegacyHudVisual(score.score(), score.accuracy(), score.combo(),
                score.combo() == 0 ? 0 : 1, 1, 0, 1, 0);
    }
}
