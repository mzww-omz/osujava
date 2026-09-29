package dev.osujava.score;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultStatisticsTest {
    @Test void populationVarianceAndSignedSubsetsMatchStableContracts() {
        var stats = ResultStatistics.of(List.of(-10, 0, 10));
        assertEquals(-10, stats.negativeMean());
        assertEquals(5, stats.nonnegativeMean());
        assertEquals(0, stats.mean());
        assertEquals(200.0 / 3, stats.variance());
        assertEquals(81.6496580927726, stats.unstableRate(), 1e-10);
        assertEquals(16.32993161855452, stats.spinnerDeviation(), 1e-10);
    }

    @Test void missingEmptyConstantAndAllNegativeSamplesKeepStableEdgeCases() {
        assertNull(ResultStatistics.of(null));
        assertNull(ResultStatistics.of(List.of()));
        assertEquals(0, ResultStatistics.of(List.of(12, 12, 12)).unstableRate());
        var negative = ResultStatistics.of(List.of(-30, -10));
        assertEquals(0, negative.maximum());
        assertEquals(-30, negative.minimum());
        assertEquals(0, negative.nonnegativeMean());
        assertEquals(100, negative.unstableRate());
    }
}
