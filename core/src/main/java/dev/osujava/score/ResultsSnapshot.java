package dev.osujava.score;

import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.ScoreState;
import java.util.Objects;

/** Frozen once on leaving gameplay. Browsing this value does not save another play. */
public record ResultsSnapshot(ScoreState score, ScoreDetails details, long playedAt,
                              GameplayRunMode runMode, boolean savedScore) {
    public ResultsSnapshot {
        Objects.requireNonNull(score); Objects.requireNonNull(details); Objects.requireNonNull(runMode);
        if (playedAt < 0) throw new IllegalArgumentException("Invalid result time");
    }
    public static ResultsSnapshot saved(LocalScore score) {
        return new ResultsSnapshot(score.result(), score.details().forStorage(), score.playedAt(), GameplayRunMode.MANUAL, true);
    }
}
