package dev.osujava.ruleset.osu;

/** Stable osu! judgement-ratio grades, independent of rendering and score value. */
public enum OsuGrade {
    SS, S, A, B, C, D;

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
