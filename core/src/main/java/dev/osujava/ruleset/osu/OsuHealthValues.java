package dev.osujava.ruleset.osu;

import dev.osujava.gameplay.Judgement;
import java.util.Objects;

/** Independent NM health arithmetic from the documented b20230727.9 contracts.
 * Calibration, gameplay timing, failure and graph sampling have separate owners.
 * Initial health is explicit: the calibration reset does not establish runtime initial filling. */
final class OsuHealthValues {
    enum SpinnerEvent { HALF_ROTATION, ROTATION, BONUS }

    private final double hpDrainRate, comboRecoveryMultiplier, normalRecoveryMultiplier;
    private double health, uncappedHealth;

    OsuHealthValues(double hpDrainRate, double comboRecoveryMultiplier, double normalRecoveryMultiplier, double initialHealth) {
        if (!Double.isFinite(hpDrainRate) || hpDrainRate < 0 || hpDrainRate > 10)
            throw new IllegalArgumentException("HP setting outside the verified NM range");
        requirePositive(comboRecoveryMultiplier);
        requirePositive(normalRecoveryMultiplier);
        this.hpDrainRate = hpDrainRate;
        this.comboRecoveryMultiplier = comboRecoveryMultiplier;
        this.normalRecoveryMultiplier = normalRecoveryMultiplier;
        setHealth(initialHealth);
    }

    double health() { return health; }
    double uncappedHealth() { return uncappedHealth; }

    void setHealth(double value) {
        requireNonNegative(value);
        health = Math.min(200, value);
        uncappedHealth = value;
    }

    /** Amount is already calculated by the clock/drain owner; no rate is assumed here. */
    void drain(double amount) {
        requireNonNegative(amount);
        health = Math.max(0, health - amount);
        uncappedHealth = Math.max(0, uncappedHealth - amount);
    }

    double objectDelta(Judgement judgement, OsuComboSets.Variant variant) {
        Objects.requireNonNull(judgement);
        Objects.requireNonNull(variant);
        if (variant == OsuComboSets.Variant.GEKI && judgement != Judgement.HIT300
                || variant == OsuComboSets.Variant.KATU && judgement != Judgement.HIT300 && judgement != Judgement.HIT100
                || judgement == Judgement.MISS && variant != OsuComboSets.Variant.NONE)
            throw new IllegalArgumentException("Combo-set variant does not match the judgement");
        double base = switch (judgement) {
            case HIT300 -> 6 * normalRecoveryMultiplier;
            case HIT100 -> normalRecoveryMultiplier * range(17.6, 2.2, 2.2);
            case HIT50 -> normalRecoveryMultiplier * range(3.2, .4, .4);
            case MISS -> range(-6, -25, -40);
        };
        double bonus = switch (variant) {
            case NONE -> 0;
            case NORMAL_END -> 6 * comboRecoveryMultiplier;
            case KATU -> 10 * comboRecoveryMultiplier;
            case GEKI -> 14 * comboRecoveryMultiplier;
        };
        return finiteDelta(base + bonus);
    }

    double nestedDelta(OsuScoreEvent event, boolean hit) {
        Objects.requireNonNull(event);
        double successful = switch (event) {
            case SLIDER_TICK -> 3;
            case SLIDER_HEAD, SLIDER_REPEAT, SLIDER_TAIL -> 4;
            case SPINNER_SPIN, SPINNER_BONUS -> throw new IllegalArgumentException("Spinner event is not a slider part");
        };
        return finiteDelta(hit ? successful * normalRecoveryMultiplier : range(-4, -15, -28));
    }

    double spinnerDelta(SpinnerEvent event) {
        Objects.requireNonNull(event);
        return finiteDelta((event == SpinnerEvent.BONUS ? 2 : 1.7) * normalRecoveryMultiplier);
    }

    void recordObject(Judgement judgement, OsuComboSets.Variant variant) { apply(objectDelta(judgement, variant)); }
    void recordNested(OsuScoreEvent event, boolean hit) { apply(nestedDelta(event, hit)); }
    void recordSpinner(SpinnerEvent event) { apply(spinnerDelta(event)); }

    private void apply(double delta) {
        if (delta <= 0) {
            drain(-delta);
            return;
        }
        double nextUncapped = uncappedHealth + delta;
        if (!Double.isFinite(nextUncapped))
            throw new IllegalArgumentException("Uncapped health overflow");
        // Commit both values together so rejected input cannot partially mutate the state.
        health = Math.max(0, Math.min(200, health + delta));
        uncappedHealth = nextUncapped;
    }

    private double range(double low, double middle, double high) {
        // Preserve the observed double operation order of stable's NM interpolation.
        if (hpDrainRate > 5) return middle + (high - middle) * (hpDrainRate - 5) / 5;
        if (hpDrainRate < 5) return middle - (middle - low) * (5 - hpDrainRate) / 5;
        return middle;
    }

    private static double finiteDelta(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite health delta");
        return value;
    }
    private static void requireNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid health amount");
    }
    private static void requirePositive(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid recovery multiplier");
    }
}
