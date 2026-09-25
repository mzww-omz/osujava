package dev.osujava.gameplay;

public record ScoreState(
        long score,
        int combo,
        int maxCombo,
        int count300,
        int count100,
        int count50,
        int misses,
        double accuracy) {
}
