package dev.osujava.ruleset.osu;

import dev.osujava.gameplay.Judgement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static dev.osujava.ruleset.osu.OsuComboSets.Variant.*;
import static org.junit.jupiter.api.Assertions.*;

class OsuHealthValuesTest {
    @ParameterizedTest
    @CsvSource({"0,17.6,3.2,-6,-4", "2.5,9.9,1.8,-15.5,-9.5", "5,2.2,.4,-25,-15",
            "7.5,2.2,.4,-32.5,-21.5", "10,2.2,.4,-40,-28"})
    void observedHpEndpointsAndMidpointsIncludeLowHpRecovery(double hp, double hundred, double fifty, double miss, double nestedMiss) {
        var health = new OsuHealthValues(hp, 1, 1, 200);
        assertEquals(6, health.objectDelta(Judgement.HIT300, NONE));
        assertEquals(hundred, health.objectDelta(Judgement.HIT100, NONE), 1e-12);
        assertEquals(fifty, health.objectDelta(Judgement.HIT50, NONE), 1e-12);
        assertEquals(miss, health.objectDelta(Judgement.MISS, NONE), 1e-12);
        for (var part : new OsuScoreEvent[]{OsuScoreEvent.SLIDER_HEAD, OsuScoreEvent.SLIDER_TICK,
                OsuScoreEvent.SLIDER_REPEAT, OsuScoreEvent.SLIDER_TAIL})
            assertEquals(nestedMiss, health.nestedDelta(part, false), 1e-12);
    }

    @Test void calibratedNormalAndComboMultipliersRemainIndependentOfMisses() {
        var health = new OsuHealthValues(5, 3, 2, 100);
        assertEquals(12, health.objectDelta(Judgement.HIT300, NONE));
        assertEquals(30, health.objectDelta(Judgement.HIT300, NORMAL_END));
        assertEquals(42, health.objectDelta(Judgement.HIT300, KATU));
        assertEquals(54, health.objectDelta(Judgement.HIT300, GEKI));
        assertEquals(34.4, health.objectDelta(Judgement.HIT100, KATU), 1e-12);
        assertEquals(18.8, health.objectDelta(Judgement.HIT50, NORMAL_END), 1e-12);
        assertEquals(-25, health.objectDelta(Judgement.MISS, NONE));
        assertEquals(-15, health.nestedDelta(OsuScoreEvent.SLIDER_TAIL, false));
        assertEquals(6, health.nestedDelta(OsuScoreEvent.SLIDER_TICK, true));
        for (var part : new OsuScoreEvent[]{OsuScoreEvent.SLIDER_HEAD, OsuScoreEvent.SLIDER_REPEAT, OsuScoreEvent.SLIDER_TAIL})
            assertEquals(8, health.nestedDelta(part, true));
        assertEquals(3.4, health.spinnerDelta(OsuHealthValues.SpinnerEvent.HALF_ROTATION));
        assertEquals(3.4, health.spinnerDelta(OsuHealthValues.SpinnerEvent.ROTATION));
        assertEquals(4, health.spinnerDelta(OsuHealthValues.SpinnerEvent.BONUS));
    }

    @Test void recoveryAtTheCapAccumulatesSeparatelyForCalibration() {
        var health = new OsuHealthValues(5, 3, 2, 198);
        health.recordObject(Judgement.HIT300, NONE);
        health.recordObject(Judgement.HIT50, NONE);
        assertEquals(200, health.health());
        assertEquals(210.8, health.uncappedHealth(), 1e-12);
        health.recordObject(Judgement.MISS, NONE);
        assertEquals(175, health.health());
        assertEquals(185.8, health.uncappedHealth(), 1e-12);
        health.drain(180);
        assertEquals(0, health.health());
        assertEquals(5.8, health.uncappedHealth(), 1e-12);
        health.drain(10);
        assertEquals(0, health.uncappedHealth());
        // Whether recovery is permitted at zero is a runtime fail/NF decision, not arithmetic.
        health.recordNested(OsuScoreEvent.SLIDER_TICK, true);
        health.recordSpinner(OsuHealthValues.SpinnerEvent.ROTATION);
        assertEquals(9.4, health.health(), 1e-12);
        assertEquals(9.4, health.uncappedHealth(), 1e-12);
    }

    @Test void explicitSettingAndPartitionedDrainDoNotAssumeRuntimeInitialFilling() {
        var health = new OsuHealthValues(5, 1, 1, 0);
        assertEquals(0, health.health());
        health.setHealth(240);
        assertEquals(200, health.health());
        assertEquals(240, health.uncappedHealth());
        health.drain(40);
        assertEquals(160, health.health());
        assertEquals(200, health.uncappedHealth());
        var partitioned = new OsuHealthValues(5, 1, 1, 240);
        for (int i = 0; i < 400; i++) partitioned.drain(.1);
        assertEquals(health.health(), partitioned.health(), 1e-9);
        assertEquals(health.uncappedHealth(), partitioned.uncappedHealth(), 1e-9);
    }

    @Test void invalidParametersAndIncompatibleVariantsAreRejected() {
        for (double value : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(value, 1, 1, 200));
            assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(5, value, 1, 200));
            assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(5, 1, value, 200));
            assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(5, 1, 1, value));
        }
        assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(11, 1, 1, 200));
        assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(5, 0, 1, 200));
        assertThrows(IllegalArgumentException.class, () -> new OsuHealthValues(5, 1, 0, 200));
        var health = new OsuHealthValues(5, 1, 1, 150);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> health.drain(invalid));
            assertThrows(IllegalArgumentException.class, () -> health.setHealth(invalid));
            assertEquals(150, health.health());
            assertEquals(150, health.uncappedHealth());
        }
        assertThrows(IllegalArgumentException.class, () -> health.recordObject(Judgement.MISS, NORMAL_END));
        assertThrows(IllegalArgumentException.class, () -> health.recordObject(Judgement.HIT50, KATU));
        assertThrows(IllegalArgumentException.class, () -> health.recordObject(Judgement.HIT100, GEKI));
        assertThrows(IllegalArgumentException.class, () -> health.recordNested(OsuScoreEvent.SPINNER_SPIN, true));
        assertEquals(150, health.health());
        assertEquals(150, health.uncappedHealth());
    }

    @Test void overflowRejectionCannotPartiallyChangeCappedHealth() {
        var health = new OsuHealthValues(5, 1, 1e307, Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> health.recordObject(Judgement.HIT300, NONE));
        assertEquals(200, health.health());
        assertEquals(Double.MAX_VALUE, health.uncappedHealth());
        var deltaOverflow = new OsuHealthValues(5, 1, Double.MAX_VALUE, 100);
        assertThrows(IllegalArgumentException.class, () -> deltaOverflow.recordObject(Judgement.HIT300, NONE));
        assertEquals(100, deltaOverflow.health());
        assertEquals(100, deltaOverflow.uncappedHealth());
    }
}
