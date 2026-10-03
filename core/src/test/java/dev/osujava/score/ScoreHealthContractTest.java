package dev.osujava.score;

import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.ScoreState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScoreHealthContractTest {
    @TempDir Path directory;

    private ScoreDetails details(Boolean passed, List<ScoreDetails.HealthPoint> points) {
        return new ScoreDetails(ScoreDetails.SCORE_V1, "a".repeat(64), "b".repeat(32),
                0, 0, 20, false, passed, points, List.of(4), null);
    }

    @Test void liveSnapshotRejectsReversedAndExcessiveHealthBeforeSaving() {
        var early = new ScoreDetails.HealthPoint(-100, 1);
        var late = new ScoreDetails.HealthPoint(500, .5f);
        assertThrows(IllegalArgumentException.class, () -> details(false, List.of(late, early)));
        assertThrows(IllegalArgumentException.class,
                () -> details(false, Collections.nCopies(ScoreDetails.MAX_HEALTH_POINTS + 1, early)));
        assertEquals(ScoreDetails.MAX_HEALTH_POINTS,
                details(false, Collections.nCopies(ScoreDetails.MAX_HEALTH_POINTS, early)).health().size());
    }

    @Test void healthAcceptsLeadInAndSameTimeJudgementsAndCopiesItsInput() {
        var input = new ArrayList<>(List.of(new ScoreDetails.HealthPoint(-100, 1),
                new ScoreDetails.HealthPoint(500, .5f), new ScoreDetails.HealthPoint(500, 0)));
        var details = details(false, input);
        input.clear();
        assertEquals(3, details.health().size());
        assertThrows(UnsupportedOperationException.class, () -> details.health().clear());
        assertEquals(details.health(), details.forStorage().health());
    }

    @Test void nonFiniteAndOutOfRangeSamplesAreRejected() {
        for (float time : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new ScoreDetails.HealthPoint(time, .5f));
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -.01f, 1.01f})
            assertThrows(IllegalArgumentException.class, () -> new ScoreDetails.HealthPoint(0, value));
    }

    @Test void partialFailedScoreAndHealthSurviveRestartWithoutSynthesizingMisses() {
        var points = List.of(new ScoreDetails.HealthPoint(-100, 1), new ScoreDetails.HealthPoint(500, 0));
        var score = new LocalScore(UUID.randomUUID(), new DifficultyIdentity("set", "map.osu"), 1234,
                new ScoreState(300, 0, 1, 1, 0, 0, 1, .5), details(false, points));
        assertTrue(new LocalScoreStore(directory).save(score, GameplayRunMode.MANUAL));
        var reloaded = new LocalScoreStore(directory).best(score.difficulty());
        assertEquals(score.forStorage(), reloaded);
        assertEquals(1, reloaded.result().misses());
        assertFalse(reloaded.details().passed());
        assertEquals(points, reloaded.details().health());
        assertNull(reloaded.details().hitErrors());
    }

    @Test void corruptedHealthDoesNotHideOtherScoresAndIsPreservedOnDisk() throws Exception {
        var good = new LocalScore(UUID.randomUUID(), new DifficultyIdentity("set", "map.osu"), 1234,
                new ScoreState(300, 0, 1, 1, 0, 0, 1, .5), details(false, List.of(new ScoreDetails.HealthPoint(500, 0))));
        var broken = new LocalScore(UUID.randomUUID(), good.difficulty(), 1235, good.result(), good.details());
        var store = new LocalScoreStore(directory);
        assertTrue(store.save(good, GameplayRunMode.MANUAL));
        assertTrue(store.save(broken, GameplayRunMode.MANUAL));
        var path = directory.resolve(broken.playId() + ".properties");
        for (String health : List.of("500:0.5;100:0.2", "NaN:0.5", "100:2")) {
            var properties = new Properties();
            try (var in = Files.newBufferedReader(path)) { properties.load(in); }
            properties.setProperty("health", health);
            try (var out = Files.newBufferedWriter(path)) { properties.store(out, "corruption fixture"); }
            byte[] bytes = Files.readAllBytes(path);
            var reload = new LocalScoreStore(directory);
            assertEquals(LocalScoreStore.Status.PARTIAL, reload.status());
            assertEquals(List.of(good.forStorage()), reload.query(good.difficulty()));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
    }

    @Test void unknownAndCollectedEmptyRemainDifferent() {
        assertNull(details(null, null).health());
        assertNull(details(null, null).passed());
        assertEquals(List.of(), details(true, List.of()).health());
    }
}
