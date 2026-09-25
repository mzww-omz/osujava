package dev.osujava.ruleset.osu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpinnerRequirementsTest {
    @Test
    void matchesLazerClearAndBonusRotationRequirementsAcrossOverallDifficulty() {
        assertEquals(new SpinnerRequirements(4, 6), SpinnerRequirements.calculate(3000, 0));
        assertEquals(new SpinnerRequirements(7, 10), SpinnerRequirements.calculate(3000, 5));
        assertEquals(new SpinnerRequirements(11, 8), SpinnerRequirements.calculate(3000, 10));
    }

    @Test
    void shortSpinnerCanRequireNoFullRotations() {
        assertEquals(new SpinnerRequirements(0, 0), SpinnerRequirements.calculate(100, 5));
    }
}
