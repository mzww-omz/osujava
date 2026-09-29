package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.ScoreState;

/** Independently implemented NoMod ScoreV1 (stable 0600264d:10f3, 06003c7a). */
public final class OsuScoreV1 {
    private final int difficultyMultiplier;
    private long score;
    private int combo, maxCombo, count300, count100, count50, misses;

    public OsuScoreV1(int difficultyMultiplier) {
        if (difficultyMultiplier < 0) throw new IllegalArgumentException("Negative difficulty multiplier");
        this.difficultyMultiplier = difficultyMultiplier;
    }

    /** A slider's final judgement enters accuracy and score, but not combo a second time. */
    public void record(Judgement judgement, boolean changesCombo) {
        int value = judgement.scoreValue();
        score += value + (long) Math.max(0, combo - 1) * (value / 25) * difficultyMultiplier;
        switch (judgement) {
            case HIT300 -> count300++;
            case HIT100 -> count100++;
            case HIT50 -> count50++;
            case MISS -> misses++;
        }
        if (changesCombo) changeCombo(judgement != Judgement.MISS, true);
    }

    public void nested(int points, boolean hit, boolean breaksCombo) {
        if (points < 0) throw new IllegalArgumentException("Negative nested score");
        if (hit) score += points;
        changeCombo(hit, breaksCombo);
    }

    public void bonus(int points) {
        if (points < 0) throw new IllegalArgumentException("Negative bonus score");
        score += points;
    }

    private void changeCombo(boolean hit, boolean breaksCombo) {
        if (hit) combo++;
        else if (breaksCombo) combo = 0;
        maxCombo = Math.max(combo, maxCombo);
    }

    public ScoreState snapshot() {
        int total = count300 + count100 + count50 + misses;
        // Stable stores this ratio as Single, independently of its displayed decimal precision.
        double accuracy = total == 0 ? 1 : (float) (300L * count300 + 100L * count100 + 50L * count50) / (300f * total);
        return new ScoreState(score, combo, maxCombo, count300, count100, count50, misses, accuracy);
    }

    public static int difficultyMultiplier(BeatmapDifficulty map) {
        var objects = map.hitObjects().stream().filter(o -> o.type() != HitObject.Type.UNKNOWN).toList();
        if (objects.isEmpty()) return 0;
        long breaks = map.playData().breaks().stream().mapToLong(b -> (long) b.endTimeMs() - b.startTimeMs()).sum();
        long seconds = (objects.getLast().timeMs() - objects.getFirst().timeMs() - breaks) / 1000;
        float density = Math.max(0, Math.min(16, (float) objects.size() / seconds * 8));
        float sum = (float) map.settings().hpDrainRate() + (float) map.settings().overallDifficulty();
        sum += (float) map.settings().circleSize();
        float value = (sum + density) / 38 * 5;
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite score difficulty");
        return Math.max(0, (int) Math.rint(value));
    }
}
