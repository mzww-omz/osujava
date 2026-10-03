package dev.osujava.gameplay;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.score.*;
import dev.osujava.support.MutableWallClock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameplayAttemptTest {
    private final LocalPlayHistory history=new LocalPlayHistory();
    private final MutableWallClock clock=new MutableWallClock("2026-10-02T00:00:00Z",ZoneOffset.UTC);
    private GameplayAttempt lifecycle(UUID id, GameplayRunMode mode) {
        return new GameplayAttempt(history,id,new DifficultyIdentity("set","map.osu"),new BeatmapContentKey("a".repeat(64),0),mode,clock);
    }
    @Test void constructionDoesNotRecordAndShowResumeCannotDuplicateStart() {
        var id=UUID.randomUUID(); var attempt=lifecycle(id,GameplayRunMode.MANUAL); assertNull(history.attempt(id));
        attempt.start(); var start=history.attempt(id);
        clock.set("2026-10-02T00:01:00Z"); attempt.start(); assertEquals(start,history.attempt(id));
        attempt.finish(LocalPlayHistory.Outcome.COMPLETED); attempt.finish(LocalPlayHistory.Outcome.ABORTED);
        assertEquals(LocalPlayHistory.Outcome.COMPLETED,history.attempt(id).outcome()); assertEquals(2,history.revision());
    }
    @Test void explicitAbortRecordsAttemptWithoutScoreAndRetryGetsNewId() {
        var first=UUID.randomUUID(); var attempt=lifecycle(first,GameplayRunMode.MANUAL); attempt.start(); attempt.finish(LocalPlayHistory.Outcome.ABORTED);
        clock.set("2026-10-02T00:01:00Z"); var retry=UUID.randomUUID(); lifecycle(retry,GameplayRunMode.MANUAL).start();
        assertEquals(LocalPlayHistory.Outcome.ABORTED,history.attempt(first).outcome());
        assertEquals(LocalPlayHistory.Outcome.UNKNOWN,history.attempt(retry).outcome());
        assertEquals(clock.millis(),history.attempt(retry).startedAt());
    }
    @Test void debugAndUnshownScreenFinalizationCannotCreateHistory() {
        var debug=lifecycle(UUID.randomUUID(),GameplayRunMode.DEBUG_AUTO); debug.start(); debug.finish(LocalPlayHistory.Outcome.COMPLETED);
        lifecycle(UUID.randomUUID(),GameplayRunMode.MANUAL).finish(LocalPlayHistory.Outcome.ABORTED); assertEquals(0,history.revision());
    }
    @Test void unknownOutcomeCannotSealTheAttemptAndFailedIsTerminal() {
        var id=UUID.randomUUID(); var attempt=lifecycle(id,GameplayRunMode.MANUAL); attempt.start();
        assertThrows(IllegalArgumentException.class, () -> attempt.finish(LocalPlayHistory.Outcome.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> attempt.finish(null));
        assertEquals(LocalPlayHistory.Outcome.UNKNOWN,history.attempt(id).outcome());
        assertNull(history.attempt(id).endedAt());
        attempt.finish(LocalPlayHistory.Outcome.FAILED);
        attempt.finish(LocalPlayHistory.Outcome.COMPLETED);
        attempt.finish(LocalPlayHistory.Outcome.ABORTED);
        assertEquals(LocalPlayHistory.Outcome.FAILED,history.attempt(id).outcome());
        assertEquals(2,history.revision());
    }

    @Test void storageFailureCanBeRetriedWithoutPublishingAnInventedEnd(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var diskHistory=new LocalPlayHistory(directory); var id=UUID.randomUUID();
        var attempt=new GameplayAttempt(diskHistory,id,new DifficultyIdentity("set","map.osu"),
                new BeatmapContentKey("a".repeat(64),0),GameplayRunMode.MANUAL,clock);
        attempt.start();
        var file=directory.resolve(id+".properties"); byte[] original=java.nio.file.Files.readAllBytes(file);
        java.nio.file.Files.writeString(file,"externally changed");
        attempt.finish(LocalPlayHistory.Outcome.FAILED);
        assertEquals(LocalPlayHistory.Outcome.UNKNOWN,diskHistory.attempt(id).outcome());
        assertEquals(1,diskHistory.revision());
        java.nio.file.Files.write(file,original);
        attempt.finish(LocalPlayHistory.Outcome.FAILED);
        assertEquals(LocalPlayHistory.Outcome.FAILED,diskHistory.attempt(id).outcome());
        assertEquals(LocalPlayHistory.Outcome.FAILED,new LocalPlayHistory(directory).attempt(id).outcome());
        assertEquals(2,diskHistory.revision());
    }
}
