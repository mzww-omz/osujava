package dev.osujava.ui;

import dev.osujava.beatmap.*;
import dev.osujava.gameplay.*;
import dev.osujava.score.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectScoreSnapshotTest {
    @TempDir Path root;
    private BeatmapSet set(String id) {
        var diffs = List.of("Easy","Hard").stream().map(name -> new BeatmapDifficulty(id,"Artist","Mapper",name,0,
                "","",DifficultySettings.defaults(),List.of(),List.of(),null,null)
                .withAssets(null,null,Path.of("maps",id,name+".osu"))).toList();
        return new BeatmapSet(id,id,"Artist","Mapper",null,null,diffs,List.of());
    }
    private LocalScore score(BeatmapSet set, int difficulty, long value) {
        return new LocalScore(UUID.randomUUID(),DifficultyIdentity.of(set.id(),set.difficulties().get(difficulty)),0,
                new ScoreState(value,0,10,10,0,0,0,1));
    }
    @Test void oneCompletedDifficultyMarksOnlyItsSetAndDifficultyPlayed() {
        var a = set("a"); var b = set("b"); var sets = List.of(a,b);
        var store = new LocalScoreStore(root); var snapshot = new SongSelectScoreSnapshot(store);
        assertTrue(snapshot.refresh(sets)); assertFalse(snapshot.played(a)); assertFalse(snapshot.played(b));
        var first = score(a,1,100); assertTrue(store.save(first,GameplayRunMode.MANUAL));
        assertTrue(snapshot.refresh(sets)); assertTrue(snapshot.played(a)); assertFalse(snapshot.played(b));
        assertFalse(snapshot.played(a,a.difficulties().get(0))); assertTrue(snapshot.played(a,a.difficulties().get(1)));
        assertSame(first,snapshot.best(a,a.difficulties().get(1)));
        var best = score(a,1,200); assertTrue(store.save(best,GameplayRunMode.MANUAL)); snapshot.refresh(sets);
        assertSame(best,snapshot.best(a,a.difficulties().get(1))); assertTrue(snapshot.played(a));
    }
    @Test void reloadAndNewSongSelectProjectionPreservePlayedState() {
        var set = set("夜空"); var sets = List.of(set); var store = new LocalScoreStore(root);
        var saved = score(set,0,123); store.save(saved,GameplayRunMode.MANUAL);
        var reloaded = new SongSelectScoreSnapshot(new LocalScoreStore(root)); reloaded.refresh(sets);
        assertTrue(reloaded.played(set)); assertEquals(saved,reloaded.best(set,set.difficulties().getFirst()));
    }
    @Test void unchangedRevisionDoesNotRebuildAndLibraryReplacementDoes() {
        var set = set("a"); var sets = List.of(set); var store = new LocalScoreStore();
        var snapshot = new SongSelectScoreSnapshot(store); snapshot.refresh(sets);
        for (int frame = 0; frame < 1000; frame++) {
            assertFalse(snapshot.refresh(sets)); assertFalse(snapshot.played(set)); assertNull(snapshot.best(set,set.difficulties().getFirst()));
        }
        store.save(score(set,0,100),GameplayRunMode.MANUAL); assertTrue(snapshot.refresh(sets));
        var replaced = set("a"); assertTrue(snapshot.refresh(List.of(replaced))); assertTrue(snapshot.played(replaced));
    }
    @Test void debugAutoAndScoresForUnknownDifficultiesNeverMarkLibraryPlayed() {
        var set = set("a"); var store = new LocalScoreStore(); var snapshot = new SongSelectScoreSnapshot(store);
        assertFalse(store.save(score(set,0,100),GameplayRunMode.DEBUG_AUTO));
        var unknown = new LocalScore(UUID.randomUUID(),new DifficultyIdentity("a","deleted.osu"),0,new ScoreState(0,0,0,0,0,0,0,1));
        store.save(unknown,GameplayRunMode.MANUAL); snapshot.refresh(List.of(set));
        assertFalse(snapshot.played(set)); assertNull(snapshot.best(set,set.difficulties().getFirst()));
    }
    @Test void selectionHierarchyAlwaysWinsOverPlayedState() {
        for (boolean played : new boolean[]{false,true}) {
            assertEquals(SongSelectRowPresentation.Tone.SELECTED,SongSelectRowPresentation.tone(true,true,played));
            assertEquals(SongSelectRowPresentation.Tone.SELECTED,SongSelectRowPresentation.tone(true,false,played));
            assertEquals(SongSelectRowPresentation.Tone.SIBLING,SongSelectRowPresentation.tone(false,true,played));
        }
        assertEquals(SongSelectRowPresentation.Tone.PLAYED,SongSelectRowPresentation.tone(false,false,true));
        assertEquals(SongSelectRowPresentation.Tone.UNPLAYED,SongSelectRowPresentation.tone(false,false,false));
    }
}
