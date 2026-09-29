package dev.osujava.score;

import java.util.List;

/** Stable result-tooltip population statistics, including zero in the nonnegative subset. */
public record ResultStatistics(double negativeMean, double nonnegativeMean, double mean,
                               double variance, int maximum, int minimum) {
    public static ResultStatistics of(List<Integer> samples) {
        if (samples == null || samples.isEmpty()) return null;
        double negative = 0, nonnegative = 0;
        int negativeCount = 0, nonnegativeCount = 0, max = 0, min = Integer.MAX_VALUE;
        for (int value : samples) {
            if (value < 0) { negative += value; negativeCount++; }
            else { nonnegative += value; nonnegativeCount++; }
            max = Math.max(max, value);
            min = Math.min(min, value);
        }
        double mean = (negative + nonnegative) / samples.size(), variance = 0;
        for (int value : samples) variance += (value - mean) * (value - mean);
        return new ResultStatistics(negativeCount == 0 ? 0 : negative / negativeCount,
                nonnegativeCount == 0 ? 0 : nonnegative / nonnegativeCount,
                mean, variance / samples.size(), max, min);
    }

    public double unstableRate() { return Math.sqrt(variance) * 10; }
    public double spinnerDeviation() { return Math.sqrt(variance) * 2; }
}
