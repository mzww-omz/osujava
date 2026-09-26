package dev.osujava.ui;
import dev.osujava.skin.LegacyHudLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHudPlacementTest {
    @Test void progressRetainsInitial100PercentLayoutWhenAccuracyLosesADigit() {
        var zero = LegacyHudLayout.score(0, c -> new LegacyHudLayout.Size(20, 32), 0);
        var hundred = LegacyHudLayout.accuracy(1, c -> new LegacyHudLayout.Size(20, 32), 0);
        var ninetyNine = LegacyHudLayout.accuracy(.9912, c -> new LegacyHudLayout.Size(20, 32), 0);
        var p = LegacyHudPlacement.fit(1024, 768, zero, hundred);
        float initialGap = p.accuracyRight() - hundred.width() * LegacyHudPlacement.ACCURACY_SCALE - p.progressRight();
        float changedGap = p.accuracyRight() - ninetyNine.width() * LegacyHudPlacement.ACCURACY_SCALE - p.progressRight();
        assertEquals(20 * .576, changedGap - initialGap, 1e-4);
    }

    @Test void alignsToWholeScreenAtLazerScaleIncludingScaledMarginsAndRelativeProgress() {
        var score = LegacyHudLayout.score(0, c -> new LegacyHudLayout.Size(20, 32), 0);
        var accuracy = LegacyHudLayout.accuracy(1, c -> new LegacyHudLayout.Size(20, 32), 0);
        var p = LegacyHudPlacement.fit(1366, 768, score, accuracy);
        assertEquals(1, p.unit());
        assertEquals(1366 - 9.6, p.scoreRight(), 1e-4);
        assertEquals(768, p.scoreTop());
        assertEquals(1366 - 17 * .576, p.accuracyRight(), 1e-4);
        assertEquals(768 - 32 * .96 - 9 * .576, p.accuracyTop(), 1e-4);
        assertEquals(1366 - 140 * .576 - 18, p.progressRight(), 1e-4);
        assertEquals(p.accuracyTop() - 32 * .576 / 2, p.progressCentreY(), 1e-4);
        assertEquals(12.8, p.comboLeft(), 1e-4);
        assertEquals(44.8, LegacyHudPlacement.comboTop(12.8f, 32, 1, 1), 1e-4);
        assertEquals(.5, LegacyHudPlacement.fit(512, 768, score, accuracy).unit());
    }
}
