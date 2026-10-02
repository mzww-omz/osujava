package dev.osujava.score;

import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.ScoreState;
import java.util.Objects;

/** Frozen once on leaving gameplay. Browsing this value does not save another play. */
public record ResultsSnapshot(ScoreState score, ScoreDetails details, long playedAt,
                              GameplayRunMode runMode, boolean savedScore, PlayContext context) {
    public ResultsSnapshot(ScoreState score, ScoreDetails details, long playedAt, GameplayRunMode runMode, boolean savedScore) {
        this(score,details,playedAt,runMode,savedScore,null);
    }
    public ResultsSnapshot {
        Objects.requireNonNull(score); Objects.requireNonNull(details); Objects.requireNonNull(runMode);
        if (context != null && (!context.matches(details) || context.runMode() != runMode))
            throw new IllegalArgumentException("Result context differs from play");
        if (playedAt < 0) throw new IllegalArgumentException("Invalid result time");
    }
    public static ResultsSnapshot saved(LocalScore score) {
        return new ResultsSnapshot(score.result(), score.details().forStorage(), score.playedAt(), GameplayRunMode.MANUAL, true, score.context());
    }
}
