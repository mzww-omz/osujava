package dev.osujava.gameplay;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.score.*;
import java.time.Clock;
import java.util.UUID;

/** Lifecycle only. Creating a screen, previewing it or reading Results cannot start an attempt. */
public final class GameplayAttempt {
    private final LocalPlayHistory history;
    private final UUID playId;
    private final DifficultyIdentity location;
    private final BeatmapContentKey content;
    private final GameplayRunMode mode;
    private final Clock clock;
    private boolean shown, started, ended;
    public GameplayAttempt(LocalPlayHistory history, UUID playId, DifficultyIdentity location,
                           BeatmapContentKey content, GameplayRunMode mode, Clock clock) {
        this.history=history; this.playId=playId; this.location=location; this.content=content; this.mode=mode; this.clock=clock;
    }
    public void start() {
        if (shown) return;
        shown=true; started=history.start(playId,location,content,clock.millis(),mode);
    }
    public void finish(LocalPlayHistory.Outcome outcome) {
        if (!started || ended) return;
        if (outcome == null || outcome == LocalPlayHistory.Outcome.UNKNOWN)
            throw new IllegalArgumentException("Attempt must finish with a terminal outcome");
        ended=history.finish(playId,clock.millis(),outcome);
    }
}
