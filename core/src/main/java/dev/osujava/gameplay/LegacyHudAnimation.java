package dev.osujava.gameplay;

/** Session-owned presentation state, driven only by score events and GameClock timestamps.
 * Evaluating a frame is pure, so rendering frequency cannot change the animations.
 * Source: LegacyScoreCounter, PercentageCounter and LegacyDefaultComboCounter in lazer.
 */
public final class LegacyHudAnimation {
    private record Roll(double from, double to, double start, double duration, boolean easeOut) {
        double at(double now) {
            double p = progress(now, start, duration);
            if (easeOut) p = 1 - (1 - p) * (1 - p);
            return from + (to - from) * p;
        }
    }

    private Roll score = new Roll(0, 0, 0, 0, true);
    private Roll accuracy = new Roll(1, 1, 0, 0, true);
    private int targetCombo;
    private int comboFrom;
    private double comboStart = Double.NEGATIVE_INFINITY;
    private double rollDuration;
    private boolean rolling;
    private double smallStart = Double.NEGATIVE_INFINITY;
    private double popStart = Double.NEGATIVE_INFINITY;
    private int popCombo;
    private double zeroFadeStart = Double.NEGATIVE_INFINITY;

    public void changed(ScoreState value, double time) {
        if (value.score() != score.to) score = new Roll(Math.rint(score.at(time)), value.score(), time, 1000, true);
        if (value.accuracy() != accuracy.to) accuracy = new Roll(accuracy.at(time), value.accuracy(), time, 375, true);
        if (value.combo() == targetCombo) return;
        LegacyHudVisual before = at(time);
        int previous = targetCombo;
        targetCombo = value.combo();
        comboStart = time;
        if (targetCombo == 0) {
            rolling = true;
            comboFrom = before.combo();
            rollDuration = comboFrom * 20.0;
            // Integer linear interpolation reaches zero in the final half-step.
            zeroFadeStart = time + Math.max(0, rollDuration - 10);
            smallStart = Double.NEGATIVE_INFINITY;
        } else {
            rolling = false;
            // Finish pending DisplayedCount transforms, then restore the previous bound value.
            smallStart = before.combo() + 1 == previous ? time : Double.NEGATIVE_INFINITY;
            if (targetCombo == previous + 1) {
                comboFrom = previous;
                popCombo = targetCombo;
                popStart = time;
            } else {
                comboFrom = targetCombo;
                if (before.combo() + 1 == targetCombo) smallStart = time;
            }
        }
    }

    public LegacyHudVisual at(double now) {
        int count;
        double alpha, scale = 1;
        if (rolling) {
            count = (int) Math.rint(comboFrom * (1 - progress(now, comboStart, rollDuration)));
            alpha = count == 0 ? 1 - progress(now, zeroFadeStart, 100) : 1;
        } else {
            boolean increment = comboFrom != targetCombo;
            count = increment && now < comboStart + 160 ? comboFrom : targetCombo;
            alpha = targetCombo == 0 ? 0 : 1;
            double start = increment && now >= comboStart + 160 ? comboStart + 160 : smallStart;
            double age = now - start;
            if (age >= 0 && age <= 100) {
                if (age <= 50) scale = 1 + 0.1 * Math.pow(age / 50, 2);
                else scale = 1.1 - 0.1 * (1 - Math.pow(1 - (age - 50) / 50, 2));
            }
        }
        double pop = progress(now, popStart, 300);
        return new LegacyHudVisual((long) Math.rint(score.at(now)), accuracy.at(now), count, alpha, scale,
                popCombo, 1.56 - 0.56 * pop, Double.isFinite(popStart) ? 0.6 * (1 - pop) : 0);
    }

    private static double progress(double now, double start, double duration) {
        if (!Double.isFinite(start)) return 1;
        if (duration == 0) return now >= start ? 1 : 0;
        return Math.max(0, Math.min(1, (now - start) / duration));
    }
}
