package dev.osujava.gameplay;

public final class ScoreTracker {
    private long score;
    private int combo;
    private int maxCombo;
    private int count300;
    private int count100;
    private int count50;
    private int misses;
    private long earnedAccuracy;
    private int judgedObjects;

    public void record(Judgement judgement) {
        score += judgement.scoreValue();
        judgedObjects++;
        earnedAccuracy += judgement.scoreValue();
        switch (judgement) {
            case HIT300 -> {
                count300++;
                combo++;
            }
            case HIT100 -> {
                count100++;
                combo++;
            }
            case HIT50 -> {
                count50++;
                combo++;
            }
            case MISS -> {
                misses++;
                combo = 0;
            }
        }
        maxCombo = Math.max(maxCombo, combo);
    }

    public ScoreState snapshot() {
        int total = judgedObjects;
        double accuracy = total == 0 ? 1.0 : (double) earnedAccuracy / (300 * total);
        return new ScoreState(score, combo, maxCombo, count300, count100, count50, misses, accuracy);
    }
}
