package dev.osujava.score;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.ScoreState;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ContentScoreQueryTest {
    @TempDir Path root;
    private final DifficultyIdentity location = new DifficultyIdentity("set","map.osu");
    private LocalScore score(String hash, long value) {
        return new LocalScore(UUID.randomUUID(),location,0,new ScoreState(value,0,1,1,0,0,0,1),
                hash.isEmpty() ? ScoreDetails.LEGACY : new ScoreDetails(ScoreDetails.SCORE_V1,hash,"",
                        null,null,null,null,null,null,null,null));
    }
    @Test void editedChartAtSamePathCannotInheritOldBestAndOldRecordsRemainReadable() throws Exception {
        var path = root.resolve("map.osu");
        String source = "osu file format v14\n[General]\nMode:0\n[Metadata]\nTitle:Local\nArtist:A\nCreator:C\nVersion:D\n[HitObjects]\n100,100,1000,1,0\n";
        Files.writeString(path,source);
        var parser = new BeatmapFileParser();
        var original = BeatmapContentKey.of(parser.parse(path).difficulty());
        var store = new LocalScoreStore(root.resolve("scores"));
        var known = score(original.sha256(),10); var legacy = score("",100);
        store.save(known,GameplayRunMode.MANUAL); store.save(legacy,GameplayRunMode.MANUAL);
        Files.writeString(path,source.replace("100,100","101,100"));
        var edited = BeatmapContentKey.of(parser.parse(path).difficulty());
        assertNotEquals(original,edited); assertNull(store.best(edited));
        assertEquals(known,store.best(original)); assertEquals(List.of(legacy),store.legacy(location));
        var reload = new LocalScoreStore(root.resolve("scores"));
        assertNull(reload.best(edited)); assertEquals(known,reload.best(original));
        assertEquals(2,reload.query(location).size());
        var moved = root.resolve("moved.osu"); Files.writeString(moved,source);
        assertEquals(known,reload.best(BeatmapContentKey.of(parser.parse(moved).difficulty())));
        assertNull(reload.best((BeatmapContentKey)null));
    }

    @Test void malformedDigestIsSkippedWithoutRewritingOrHidingValidScores() throws Exception {
        var store = new LocalScoreStore(root); var good = score("a".repeat(64),1); var bad = score("b".repeat(64),2);
        store.save(good,GameplayRunMode.MANUAL); store.save(bad,GameplayRunMode.MANUAL);
        var file = root.resolve(bad.playId()+".properties");
        Files.writeString(file,Files.readString(file).replace("b".repeat(64),"not-a-hash"));
        byte[] bytes = Files.readAllBytes(file);
        var reload = new LocalScoreStore(root);
        assertEquals(LocalScoreStore.Status.PARTIAL,reload.status());
        assertEquals(List.of(good),reload.query(location)); assertArrayEquals(bytes,Files.readAllBytes(file));
    }
}
