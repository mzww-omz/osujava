package dev.osujava.score;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.gameplay.GameplayRunMode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalPlayHistoryTest {
    @TempDir Path root;
    private final BeatmapContentKey content=new BeatmapContentKey("a".repeat(64),0);
    private final DifficultyIdentity location=new DifficultyIdentity("set","map.osu");
    @Test void startTerminalStatesRetryAndRestartKeepDistinctAttemptIdsAndStartTimes() throws Exception {
        var store=new LocalPlayHistory(root);
        for (var outcome:LocalPlayHistory.Outcome.values()) {
            var id=UUID.randomUUID(); assertTrue(store.start(id,location,content,100,GameplayRunMode.MANUAL));
            assertFalse(store.start(id,location,content,200,GameplayRunMode.MANUAL));
            if(outcome!=LocalPlayHistory.Outcome.UNKNOWN) {
                assertTrue(store.finish(id,200,outcome)); assertFalse(store.finish(id,300,LocalPlayHistory.Outcome.ABORTED));
            }
            var bytes=Files.readAllBytes(root.resolve(id+".properties"));
            var reopened=new LocalPlayHistory(root); assertEquals(store.attempt(id),reopened.attempt(id));
            assertEquals(100L,reopened.lastPlayed(content)); assertArrayEquals(bytes,Files.readAllBytes(root.resolve(id+".properties")));
        }
        var retry=UUID.randomUUID(); assertTrue(store.start(retry,location,content,400,GameplayRunMode.MANUAL));
        assertEquals(400L,new LocalPlayHistory(root).lastPlayed(content));
        try(var files=Files.list(root)) { assertEquals(5,files.count()); }
    }
    @Test void debugAutoAndUnidentifiedChartsNeverCreateFilesOrRevision() throws Exception {
        var store=new LocalPlayHistory(root);
        assertFalse(store.start(UUID.randomUUID(),location,content,100,GameplayRunMode.DEBUG_AUTO));
        assertFalse(store.start(UUID.randomUUID(),location,null,100,GameplayRunMode.MANUAL));
        assertFalse(store.start(UUID.randomUUID(),null,content,100,GameplayRunMode.MANUAL));
        assertEquals(0,store.revision()); try(var files=Files.list(root)) { assertEquals(0,files.count()); }
        assertNull(store.lastPlayed(new BeatmapContentKey("b".repeat(64),0)));
    }
    @Test void malformedAndFutureFilesRemainOnDiskAndCannotBeOverwrittenBySameId() throws Exception {
        var store=new LocalPlayHistory(root); var id=UUID.randomUUID(); var good=UUID.randomUUID();
        store.start(id,location,content,100,GameplayRunMode.MANUAL); store.start(good,location,content,200,GameplayRunMode.MANUAL);
        var path=root.resolve(id+".properties"); Files.writeString(path,Files.readString(path).replace("schemaVersion=1","schemaVersion=2"));
        byte[] bytes=Files.readAllBytes(path);
        assertFalse(store.finish(id,300,LocalPlayHistory.Outcome.COMPLETED)); assertEquals(LocalPlayHistory.Outcome.UNKNOWN,store.attempt(id).outcome());
        var loaded=new LocalPlayHistory(root); assertEquals(LocalPlayHistory.Status.PARTIAL,loaded.status());
        assertNull(loaded.attempt(id)); assertNotNull(loaded.attempt(good));
        assertFalse(loaded.start(id,location,content,300,GameplayRunMode.MANUAL)); assertArrayEquals(bytes,Files.readAllBytes(path));
        Files.writeString(path,"schemaVersion=1\nplayId=bad\n"); bytes=Files.readAllBytes(path);
        assertEquals(LocalPlayHistory.Status.PARTIAL,new LocalPlayHistory(root).status()); assertArrayEquals(bytes,Files.readAllBytes(path));
    }
    @Test void storageFailureDoesNotPublishAnAttemptOrEraseUnfinishedAttempt() throws Exception {
        var blocked=root.resolve("blocked"); Files.writeString(blocked,"file"); var unavailable=new LocalPlayHistory(blocked);
        assertFalse(unavailable.start(UUID.randomUUID(),location,content,100,GameplayRunMode.MANUAL));
        assertEquals(LocalPlayHistory.Status.UNAVAILABLE,unavailable.status()); assertEquals(0,unavailable.revision());
        var store=new LocalPlayHistory(root.resolve("history")); var id=UUID.randomUUID(); store.start(id,location,content,100,GameplayRunMode.MANUAL);
        Files.delete(root.resolve("history/"+id+".properties"));
        assertFalse(store.finish(id,200,LocalPlayHistory.Outcome.COMPLETED)); assertEquals(1,store.revision());
        assertEquals(LocalPlayHistory.Outcome.UNKNOWN,store.attempt(id).outcome());
    }
    @Test void backwardsWallClockKeepsValidEndAndInvalidRecordsAreRejected() {
        var store=new LocalPlayHistory(); var id=UUID.randomUUID(); store.start(id,location,content,100,GameplayRunMode.MANUAL);
        assertTrue(store.finish(id,50,LocalPlayHistory.Outcome.ABORTED)); assertEquals(100L,store.attempt(id).endedAt());
        assertThrows(IllegalArgumentException.class,() -> new LocalPlayHistory.Attempt(id,location,content,100,99L,LocalPlayHistory.Outcome.FAILED));
        assertThrows(IllegalArgumentException.class,() -> new LocalPlayHistory.Attempt(id,location,content,100,null,LocalPlayHistory.Outcome.COMPLETED));
    }
}
