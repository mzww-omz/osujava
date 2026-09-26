package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHudAnimationTest {
    private static ScoreState score(long score, int combo, double accuracy) {
        return new ScoreState(score, combo, combo, 0, 0, 0, 0, accuracy);
    }
    @Test void rollsScoreFor1000MsWithQuadraticOutAndRetargetsFromDisplayedInteger() {
        var hud = new LegacyHudAnimation();
        hud.changed(score(1000, 0, 1), 100);
        assertEquals(0, hud.at(100).score());
        assertEquals(750, hud.at(600).score());
        hud.changed(score(2000, 0, 1), 600);
        assertEquals(750, hud.at(600).score());
        assertEquals(1688, hud.at(1100).score()); // .NET banker rounding
        assertEquals(2000, hud.at(1600).score());
    }
    @Test void accuracyRollsOver375MsWithoutChangingScoreState() {
        var hud = new LegacyHudAnimation();
        var target = score(100, 0, 0.9);
        hud.changed(target, 100);
        assertEquals(1, hud.at(100).accuracy());
        assertEquals(0.925, hud.at(287.5).accuracy(), 1e-12);
        assertEquals(0.9, hud.at(475).accuracy());
        assertEquals(0.9, target.accuracy());
    }
    @Test void incrementShowsNewAdditivePopThenDelayedSmallPop() {
        var hud = new LegacyHudAnimation();
        assertEquals(0, hud.at(0).comboAlpha());
        hud.changed(score(300, 1, 1), 1000);
        assertEquals(0, hud.at(1000).combo());
        assertEquals(1, hud.at(1000).popCombo());
        assertEquals(1.56, hud.at(1000).popScale());
        assertEquals(0.6, hud.at(1000).popAlpha());
        assertEquals(0, hud.at(1159).combo());
        assertEquals(1, hud.at(1160).combo());
        assertEquals(1, hud.at(1160).comboScale());
        assertEquals(1.025, hud.at(1185).comboScale(), 1e-12);
        assertEquals(1.1, hud.at(1210).comboScale(), 1e-12);
        assertEquals(1.025, hud.at(1235).comboScale(), 1e-12);
        assertEquals(1, hud.at(1260).comboScale());
        assertEquals(0, hud.at(1300).popAlpha());
    }
    @Test void rapidIncrementsCancelOldScheduledIncrementAndPreserveLastValue() {
        var hud = new LegacyHudAnimation();
        hud.changed(score(1, 1, 1), 100);
        hud.changed(score(2, 2, 1), 150);
        assertEquals(1, hud.at(150).combo());
        assertEquals(1, hud.at(260).combo());
        assertEquals(2, hud.at(310).combo());
        assertEquals(2, hud.at(1000).combo());
    }
    @Test void resetRollsDown20MsPerCountThenFadesAndCancelsSmallPop() {
        var hud = new LegacyHudAnimation();
        hud.changed(score(100, 10, 1), 0); // non-increment jumps directly, no large pop
        assertEquals(10, hud.at(0).combo());
        assertEquals(0, hud.at(0).popAlpha());
        hud.changed(score(100, 0, 0.9), 100);
        assertEquals(10, hud.at(100).combo());
        assertEquals(5, hud.at(200).combo());
        assertEquals(0, hud.at(300).combo());
        assertEquals(0.9, hud.at(300).comboAlpha(), 1e-12);
        assertEquals(0, hud.at(400).comboAlpha());
        assertEquals(1, hud.at(200).comboScale());
    }
    @Test void resetBeforeFirstDelayedIncrementFadesZeroAndLeavesBigPopRunning() {
        var hud = new LegacyHudAnimation();
        hud.changed(score(100, 1, 1), 100);
        hud.changed(score(100, 0, 1), 110);
        assertEquals(0, hud.at(160).combo());
        assertEquals(0.5, hud.at(160).comboAlpha(), 1e-12);
        assertTrue(hud.at(160).popAlpha() > 0);
        assertEquals(0, hud.at(270).combo());
        assertEquals(0, hud.at(270).comboAlpha());
    }
    @Test void samplingFramesHasNoEffectOnFutureFramesOrFrozenSnapshots() {
        var sparse = new LegacyHudAnimation();
        var dense = new LegacyHudAnimation();
        sparse.changed(score(500, 1, 0.9912), 100);
        dense.changed(score(500, 1, 0.9912), 100);
        var frozen = dense.at(100);
        for (int i = 100; i < 500; i++) dense.at(i);
        assertEquals(sparse.at(500), dense.at(500));
        assertEquals(0, frozen.score());
        assertEquals(0, frozen.combo());
    }
    @Test void songProgressUsesIntroAndPlayableBoundsAndHandlesSingleObject() {
        var progress = new LegacySongProgress(0, 1000, 5000);
        assertEquals(0, progress.alphaAt(0));
        assertEquals(0.96875, progress.alphaAt(250));
        assertEquals(1, progress.alphaAt(500));
        assertEquals(new LegacySongProgress.Frame(0.5, true), progress.at(500));
        assertEquals(new LegacySongProgress.Frame(0, false), progress.at(1000));
        assertEquals(new LegacySongProgress.Frame(0.5, false), progress.at(3000));
        assertEquals(new LegacySongProgress.Frame(1, false), progress.at(6000));
        assertEquals(0, new LegacySongProgress(0, 1000, 1000).at(1000).progress());
    }
}
