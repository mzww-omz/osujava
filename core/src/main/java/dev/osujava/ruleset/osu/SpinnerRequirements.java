package dev.osujava.ruleset.osu;

/** Difficulty-based rotation requirements for an osu!standard spinner. */
public record SpinnerRequirements(int spinsRequired, int maximumBonusSpins) {
    private static final double[] CLEAR_RPM = {90, 150, 225};
    private static final double[] COMPLETE_RPM = {250, 380, 430};
    private static final int BONUS_SPINS_GAP = 2;
    private static final double DURATION_ERROR_SECONDS = 0.0001;

    public SpinnerRequirements {
        if (spinsRequired < 0 || maximumBonusSpins < 0) {
            throw new IllegalArgumentException("Spinner requirements cannot be negative");
        }
    }

    public static SpinnerRequirements calculate(double durationMs, double overallDifficulty) {
        double seconds = Math.max(0, durationMs) / 1000;
        int required = (int) (difficultyRange(overallDifficulty, CLEAR_RPM) / 60 * seconds
                + DURATION_ERROR_SECONDS);
        int maximumBonus = Math.max(0, (int) (difficultyRange(overallDifficulty, COMPLETE_RPM) / 60 * seconds
                + DURATION_ERROR_SECONDS) - required - BONUS_SPINS_GAP);
        return new SpinnerRequirements(required, maximumBonus);
    }

    public int spinsRequiredForBonus() {
        return spinsRequired + BONUS_SPINS_GAP;
    }

    private static double difficultyRange(double difficulty, double[] range) {
        if (difficulty > 5) return range[1] + (range[2] - range[1]) * (difficulty - 5) / 5;
        if (difficulty < 5) return range[1] + (range[1] - range[0]) * (difficulty - 5) / 5;
        return range[1];
    }
}
