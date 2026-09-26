package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimingAndJudgementTest {
    @Test
    void calculatesStableStylePreemptFromApproachRate() {
        assertEquals(1800, ApproachTimeCalculator.preemptMs(0));
        assertEquals(1200, ApproachTimeCalculator.preemptMs(5));
        assertEquals(450, ApproachTimeCalculator.preemptMs(10));
        assertEquals(1800, ApproachTimeCalculator.preemptMs(-3));
    }

    @Test
    void calculatesHitWindowsAndClassifiesOffsets() {
        JudgementWindows windows = JudgementWindows.fromOverallDifficulty(5);

        assertEquals(49.5, windows.hit300Ms());
        assertEquals(99.5, windows.hit100Ms());
        assertEquals(149.5, windows.hit50Ms());
        assertEquals(Judgement.HIT300, windows.judge(49));
        assertEquals(Judgement.HIT100, windows.judge(80));
        assertEquals(Judgement.HIT50, windows.judge(140));
        assertEquals(Judgement.MISS, windows.judge(150));
        JudgementWindows fractional = JudgementWindows.fromOverallDifficulty(5.3);
        assertEquals(47.5, fractional.hit300Ms());
        assertEquals(96.5, fractional.hit100Ms());
        assertEquals(146.5, fractional.hit50Ms());
    }
}
