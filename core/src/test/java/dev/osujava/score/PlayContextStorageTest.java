package dev.osujava.score;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.gameplay.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlayContextStorageTest {
    @TempDir Path root;
    private final BeatmapContentKey content = new BeatmapContentKey("a".repeat(64),0);
    private final DifficultyIdentity location = new DifficultyIdentity("set","map.osu");
    private final ScoreDetails details = new ScoreDetails(ScoreDetails.SCORE_V1,content.sha256(),"b".repeat(32),
            null,null,null,null,null,null,List.of(-1,1),null);
    private PlayContext context(int mode, LocalPlayer player, List<String> mods, GameplayRunMode runMode) {
        return new PlayContext(new BeatmapContentKey(content.sha256(),mode),mode,"osu","osu-java-standard-1",
                ScoreDetails.SCORE_V1,mods,player,runMode);
    }
    private LocalScore score(PlayContext context) {
        return new LocalScore(UUID.randomUUID(),location,1234,new ScoreState(100,0,1,1,0,0,0,1),details,context);
    }
    @Test void schemaThreePreservesFrozenPlayerModsAndUnknownPassAfterReload() throws Exception {
        var player = new LocalPlayer(UUID.randomUUID(),"星の旅人");
        var mods = new ArrayList<>(List.of("HR","HD","HR"));
        var context = context(0,player,mods,GameplayRunMode.MANUAL); mods.clear();
        var original = score(context); var store = new LocalScoreStore(root);
        assertTrue(store.save(original,GameplayRunMode.MANUAL));
        var file = root.resolve(original.playId()+".properties"); byte[] bytes = Files.readAllBytes(file);
        assertTrue(Files.readString(file).contains("schemaVersion=3"));
        var loaded = new LocalScoreStore(root).best(content);
        assertEquals(original.forStorage(),loaded); assertEquals(List.of("HD","HR"),loaded.context().mods());
        assertEquals(player,loaded.context().player()); assertEquals("osu-java-standard-1",loaded.context().rulesetVersion());
        assertNull(loaded.details().passed()); assertNull(loaded.details().health()); assertNull(loaded.details().hitErrors());
        assertNotNull(original.details().hitErrors()); assertArrayEquals(bytes,Files.readAllBytes(file));
        assertEquals(context,ResultsSnapshot.saved(loaded).context());
        assertThrows(UnsupportedOperationException.class,() -> context.mods().add("NF"));
    }
    @Test void knownNoModAndUncollectedLegacyRemainDifferentAndModeScopesQuery() throws Exception {
        var store = new LocalScoreStore(root); var nm = score(context(0,null,List.of(),GameplayRunMode.MANUAL));
        var anotherMode = score(context(1,null,List.of(),GameplayRunMode.MANUAL));
        var old = score(null);
        for (var s : List.of(nm,anotherMode,old)) assertTrue(store.save(s,GameplayRunMode.MANUAL));
        var loaded = new LocalScoreStore(root);
        assertEquals(Set.of(nm.forStorage(),old.forStorage()),new HashSet<>(loaded.query(content)));
        var foundNm = loaded.query(content).stream().filter(s -> s.playId().equals(nm.playId())).findFirst().orElseThrow();
        assertTrue(foundNm.context().mods().isEmpty()); assertNull(foundNm.context().player());
        assertNull(loaded.query(location).stream().filter(s -> s.playId().equals(old.playId())).findFirst().orElseThrow().context());
        assertSame(loaded.query(content),loaded.query(content));
    }
    @Test void futureAndMalformedContextRemainOnDiskWithoutHidingValidScore() throws Exception {
        var store = new LocalScoreStore(root); var valid = score(context(0,null,List.of(),GameplayRunMode.MANUAL));
        var bad = score(context(0,null,List.of(),GameplayRunMode.MANUAL));
        var future = score(context(0,null,List.of(),GameplayRunMode.MANUAL));
        for (var s : List.of(valid,bad,future)) store.save(s,GameplayRunMode.MANUAL);
        Path badFile = root.resolve(bad.playId()+".properties"), futureFile = root.resolve(future.playId()+".properties");
        Files.writeString(badFile,Files.readString(badFile).replace("mods=","missingMods="));
        Files.writeString(futureFile,Files.readString(futureFile).replace("schemaVersion=3","schemaVersion=4"));
        byte[] badBytes = Files.readAllBytes(badFile), futureBytes = Files.readAllBytes(futureFile);
        var loaded = new LocalScoreStore(root);
        assertEquals(LocalScoreStore.Status.PARTIAL,loaded.status()); assertEquals(List.of(valid.forStorage()),loaded.query(content));
        assertArrayEquals(badBytes,Files.readAllBytes(badFile)); assertArrayEquals(futureBytes,Files.readAllBytes(futureFile));
    }
    @Test void mismatchedContextAndNonManualPlayCannotBeSavedAsManual() {
        var store = new LocalScoreStore(root); var debug = score(context(0,null,List.of(),GameplayRunMode.DEBUG_AUTO));
        assertFalse(store.save(debug,GameplayRunMode.MANUAL)); assertFalse(store.save(debug,GameplayRunMode.DEBUG_AUTO));
        assertEquals(0,store.revision());
        var changed = new PlayContext(new BeatmapContentKey("c".repeat(64),0),0,"osu","osu-java-standard-1",
                ScoreDetails.SCORE_V1,List.of(),null,GameplayRunMode.MANUAL);
        assertThrows(IllegalArgumentException.class,() -> score(changed));
        assertThrows(IllegalArgumentException.class,() -> new ResultsSnapshot(debug.result(),details,0,GameplayRunMode.MANUAL,false,debug.context()));
    }
}
