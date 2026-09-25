package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.JudgementWindows;
import dev.osujava.ruleset.Ruleset;

public final class OsuRuleset implements Ruleset {
    @Override
    public String id() {
        return "osu";
    }

    @Override
    public boolean supportsMode(int mode) {
        return mode == 0;
    }

    @Override
    public GameplaySession createSession(BeatmapDifficulty difficulty, GameClock clock) {
        if (!supportsMode(difficulty.mode())) {
            throw new IllegalArgumentException("OsuRuleset does not support mode " + difficulty.mode());
        }
        return new OsuGameplaySession(difficulty, clock,
                JudgementWindows.fromOverallDifficulty(difficulty.settings().overallDifficulty()));
    }
}
