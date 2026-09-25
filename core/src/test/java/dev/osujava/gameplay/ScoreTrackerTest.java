package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreTrackerTest {
    @Test
    void bonusScoreDoesNotAffectAccuracyCountsOrCombo() {
        ScoreTracker tracker = new ScoreTracker();
        tracker.record(Judgement.HIT300);
        tracker.recordBonusScore(60);

        ScoreState result = tracker.snapshot();
        assertEquals(360, result.score());
        assertEquals(1, result.combo());
        assertEquals(1, result.count300());
        assertEquals(1, result.accuracy());
    }

    @Test
    void calculatesScoreAccuracyCountsAndCombo() {
        ScoreTracker tracker = new ScoreTracker();
        tracker.record(Judgement.HIT300);
        tracker.record(Judgement.HIT100);
        tracker.record(Judgement.HIT50);
        tracker.record(Judgement.MISS);

        ScoreState result = tracker.snapshot();
        assertEquals(450, result.score());
        assertEquals(0, result.combo());
        assertEquals(3, result.maxCombo());
        assertEquals(1, result.count300());
        assertEquals(1, result.count100());
        assertEquals(1, result.count50());
        assertEquals(1, result.misses());
        assertEquals(0.375, result.accuracy());
    }
}
