package dev.osujava.ruleset;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;

public interface Ruleset {
    String id();

    boolean supportsMode(int mode);

    GameplaySession createSession(BeatmapDifficulty difficulty, GameClock clock);
}
