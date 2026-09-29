package dev.osujava.score;
import dev.osujava.gameplay.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalScoreStoreTest {
    @TempDir Path root;
    @Test void schemaTwoPreservesKnownFieldsAndDropsOnlyTransientStatistics() throws Exception {
        var details = new ScoreDetails(ScoreDetails.SCORE_V1, "a".repeat(64), "b".repeat(32),
                3, 2, 42, false, null, null, List.of(-10, 0, 10), null);
        var original = score("set", "map.osu", 500, 1234, UUID.randomUUID());
        var enriched = new LocalScore(original.playId(), original.difficulty(), original.playedAt(), original.result(), details);
        var store = new LocalScoreStore(root);
        assertTrue(store.save(enriched, GameplayRunMode.MANUAL));
        var loaded = new LocalScoreStore(root).best(original.difficulty());
        assertEquals(enriched.forStorage(), loaded);
        assertNull(loaded.details().passed());
        assertNull(loaded.details().health());
        assertNull(loaded.details().hitErrors());
        assertEquals(List.of(-10, 0, 10), details.hitErrors(), "Saving cannot clear the live result snapshot");
        assertEquals(1234, ResultsSnapshot.saved(loaded).playedAt());
        assertTrue(ResultsSnapshot.saved(loaded).savedScore());
    }

    @Test void schemaOneRemainsUnknownAndIsNeverRewrittenDuringRead() throws Exception {
        var original = score("set", "map.osu", 500, 1234, UUID.randomUUID());
        new LocalScoreStore(root).save(original, GameplayRunMode.MANUAL);
        Path path = root.resolve(original.playId() + ".properties");
        byte[] bytes = Files.readAllBytes(path);
        var loaded = new LocalScoreStore(root).best(original.difficulty());
        assertEquals(original, loaded);
        assertNull(loaded.details().geki());
        assertNull(loaded.details().perfect());
        assertEquals("osujava-legacy-1", loaded.details().scoringVersion());
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }
    static LocalScore score(String set, String path, long value, long time, UUID id) {
        return new LocalScore(id,new DifficultyIdentity(set,path),time,new ScoreState(value,0,123,91,9,0,0,.94));
    }
    @Test void saveReloadMultipleUnicodeAndSeparateIdentities() {
        var store = new LocalScoreStore(root);
        var a = score("set", "星/難しい.osu",100,100,UUID.randomUUID());
        var b = score("set", "星/難しい.osu",200,200,UUID.randomUUID());
        var c = score("other", "星/難しい.osu",999,300,UUID.randomUUID());
        var d = score("set", "星/簡単.osu",888,400,UUID.randomUUID());
        for (var s : List.of(a,b,c,d)) assertTrue(store.save(s,GameplayRunMode.MANUAL));
        assertFalse(store.save(a,GameplayRunMode.MANUAL));
        var reload = new LocalScoreStore(root);
        assertEquals(List.of(b,a),reload.query(a.difficulty()));
        assertEquals(List.of(c),reload.query(c.difficulty())); assertEquals(List.of(d),reload.query(d.difficulty()));
        assertFalse(reload.save(a,GameplayRunMode.MANUAL));
        assertEquals(4,root.toFile().listFiles().length);
    }
    @Test void malformedAndFutureRecordsDoNotHideValidScores() throws Exception {
        var a=score("a","a.osu",1,1,UUID.randomUUID());
        var store=new LocalScoreStore(root); store.save(a,GameplayRunMode.MANUAL);
        Files.writeString(root.resolve("broken.properties"),"schemaVersion=1\nplayId=wrong");
        Files.writeString(root.resolve("future.properties"),"schemaVersion=2");
        var reload=new LocalScoreStore(root);
        assertEquals(LocalScoreStore.Status.PARTIAL,reload.status()); assertEquals(List.of(a),reload.query(a.difficulty()));
        assertTrue(Files.exists(root.resolve("future.properties")));
    }
    @Test void invalidNumericRecordIsSkipped() throws Exception {
        var a=score("a","a.osu",1,1,UUID.randomUUID()); var store=new LocalScoreStore(root); store.save(a,GameplayRunMode.MANUAL);
        Path file=root.resolve(a.playId()+".properties");
        Files.writeString(file,Files.readString(file).replace("accuracy=0.94","accuracy=NaN"));
        var reload=new LocalScoreStore(root); assertTrue(reload.query(a.difficulty()).isEmpty());
        assertEquals(LocalScoreStore.Status.PARTIAL,reload.status());
    }
    @Test void debugAutoNeverWrites() throws Exception {
        var store=new LocalScoreStore(root);
        assertFalse(store.save(score("a","a.osu",1,1,UUID.randomUUID()),GameplayRunMode.DEBUG_AUTO));
        assertEquals(0,Files.list(root).count()); assertEquals(0,store.revision());
    }
    @Test void emptyAndUnavailableAreDistinct() throws Exception {
        assertEquals(LocalScoreStore.Status.READY,new LocalScoreStore(root).status());
        assertTrue(new LocalScoreStore(root).query(new DifficultyIdentity("a","a.osu")).isEmpty());
        Path file=root.resolve("file"); Files.writeString(file,"not directory");
        var store=new LocalScoreStore(file); assertEquals(LocalScoreStore.Status.UNAVAILABLE,store.status());
        assertFalse(store.save(score("a","a.osu",1,1,UUID.randomUUID()),GameplayRunMode.MANUAL));
    }
    @Test void tiesAreDeterministicAcrossReloadAndInsertionOrder() {
        var a=score("a","a.osu",100,1,new UUID(0,1)); var b=score("a","a.osu",100,1,new UUID(0,2));
        var c=score("a","a.osu",100,2,new UUID(0,3));
        var store=new LocalScoreStore(root); for(var s:List.of(b,c,a)) store.save(s,GameplayRunMode.MANUAL);
        assertEquals(List.of(c,a,b),new LocalScoreStore(root).query(a.difficulty()));
        assertSame(store.query(a.difficulty()),store.query(a.difficulty()));
    }
    @Test void accuracyBreaksScoreTiesAndBestGradeFollowsScore() {
        var store=new LocalScoreStore();
        var low=new LocalScore(new UUID(0,1),new DifficultyIdentity("a","a.osu"),1,new ScoreState(100,0,10,10,0,0,0,1));
        var high=new LocalScore(new UUID(0,2),low.difficulty(),1,new ScoreState(200,0,10,0,0,0,10,0));
        store.save(low,GameplayRunMode.MANUAL); store.save(high,GameplayRunMode.MANUAL);
        assertEquals(dev.osujava.ruleset.osu.OsuGrade.D,store.best(low.difficulty()).grade());
        var tie=new LocalScore(new UUID(0,3),low.difficulty(),0,new ScoreState(200,0,10,10,0,0,0,1));
        store.save(tie,GameplayRunMode.MANUAL); assertEquals(tie,store.best(low.difficulty()));
    }
    @Test void defensiveValidation() {
        assertThrows(IllegalArgumentException.class,()->score("a","a.osu",-1,0,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->new DifficultyIdentity("", "a.osu"));
    }
}
