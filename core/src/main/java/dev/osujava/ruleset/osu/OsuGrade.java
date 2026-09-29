package dev.osujava.ruleset.osu;

/** Stable osu! judgement-ratio grades, independent of rendering and score value. */
public enum OsuGrade {
    SS, S, A, B, C, D;

    /** b20230727.9 060012ec: Single division promoted to Double for the literal thresholds.
     * Keep the old integer-ratio path below for records created by the previous scoring version. */
    public static OsuGrade calculateStable(int n300, int n100, int n50, int misses) {
        if (n300 < 0 || n100 < 0 || n50 < 0 || misses < 0) return D;
        long total = (long) n300 + n100 + n50 + misses;
        double ratio300 = (float) n300 / (float) total;
        double ratio50 = (float) n50 / (float) total;
        if (ratio300 == 1) return SS;
        if (ratio300 > .9 && ratio50 <= .01 && misses == 0) return S;
        if (ratio300 > .8 && misses == 0 || ratio300 > .9) return A;
        if (ratio300 > .7 && misses == 0 || ratio300 > .8) return B;
        if (ratio300 > .6) return C;
        return D;
    }

    public static OsuGrade calculate(int n300, int n100, int n50, int misses) {
        if (n300 < 0 || n100 < 0 || n50 < 0 || misses < 0) return D;
        long total = (long) n300 + n100 + n50 + misses;
        if (total == 0) return D;
        if (n300 == total) return SS;
        if (100L * n300 > 90 * total && 100L * n50 <= total && misses == 0) return S;
        if (100L * n300 > 90 * total || 100L * n300 > 80 * total && misses == 0) return A;
        if (100L * n300 > 80 * total || 100L * n300 > 70 * total && misses == 0) return B;
        if (100L * n300 > 60 * total) return C;
        return D;
    }
}
