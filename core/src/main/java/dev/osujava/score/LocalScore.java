package dev.osujava.score;

import dev.osujava.gameplay.ScoreState;
import dev.osujava.ruleset.osu.OsuGrade;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Immutable final gameplay snapshot. Uncollected context and outcome remain unknown. */
public record LocalScore(UUID playId, DifficultyIdentity difficulty, long playedAt, ScoreState result, ScoreDetails details,
                         PlayContext context) {
    public LocalScore(UUID playId, DifficultyIdentity difficulty, long playedAt, ScoreState result, ScoreDetails details) {
        this(playId,difficulty,playedAt,result,details,null);
    }
    public LocalScore(UUID playId, DifficultyIdentity difficulty, long playedAt, ScoreState result) {
        this(playId, difficulty, playedAt, result, ScoreDetails.LEGACY);
    }
    public static final Comparator<LocalScore> ORDER = Comparator
            .comparingLong((LocalScore s) -> s.result.score()).reversed()
            .thenComparing(Comparator.comparingDouble((LocalScore s) -> s.result.accuracy()).reversed())
            .thenComparing(Comparator.comparingLong(LocalScore::playedAt).reversed())
            .thenComparing(s -> s.playId.toString());
    public LocalScore {
        Objects.requireNonNull(playId); Objects.requireNonNull(difficulty); Objects.requireNonNull(result);
        Objects.requireNonNull(details);
        if (context != null && !context.matches(details)) throw new IllegalArgumentException("Score context differs from result source");
        if (playedAt < 0 || result.score() < 0 || result.combo() < 0 || result.maxCombo() < result.combo()
                || result.count300() < 0 || result.count100() < 0 || result.count50() < 0 || result.misses() < 0
                || !Double.isFinite(result.accuracy()) || result.accuracy() < 0 || result.accuracy() > 1)
            throw new IllegalArgumentException("Invalid score snapshot");
    }
    public OsuGrade grade() {
        return ScoreDetails.SCORE_V1.equals(details.scoringVersion())
                ? OsuGrade.calculateStable(result.count300(), result.count100(), result.count50(), result.misses())
                : OsuGrade.calculate(result.count300(), result.count100(), result.count50(), result.misses());
    }

    public LocalScore forStorage() {
        ScoreDetails stored = details.forStorage();
        return stored == details ? this : new LocalScore(playId, difficulty, playedAt, result, stored, context);
    }
}
