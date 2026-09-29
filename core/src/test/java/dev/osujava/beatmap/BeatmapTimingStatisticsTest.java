package dev.osujava.beatmap;

import dev.osujava.beatmap.parse.BeatmapFileParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BeatmapTimingStatisticsTest {
    private static TimingPoint red(double time, double bpm) {
        return new TimingPoint(time, 60000 / bpm, 4, 0, 0, 100, true, 0);
    }

    @Test void tempoSearchAggregatesRepeatedSegmentsAndIgnoresInheritedAndPostEndPoints() {
        var stats = BeatmapTimingStatistics.calculate(List.of(red(5000,120), red(20000,240),
                new TimingPoint(25000,-50,4,0,0,100,false,0), red(35000,120), red(70000,600)),
                10000, 60000, 60000, 0);
        assertEquals(120,stats.minimumBpm());
        assertEquals(240,stats.maximumBpm());
        assertEquals(120,stats.commonBpm(), "120 BPM lasts 45s across two segments; 240 lasts 15s");
    }

    @Test void tempoTieKeepsTheLastEncounteredTempoInTheFile() {
        var stats = BeatmapTimingStatistics.calculate(List.of(red(0,120),red(30000,240)),0,60000,60000,0);
        assertEquals(240,stats.commonBpm());
    }

    @Test void inheritedFirstPointExtendsTheFirstTempoBackToZero() {
        var stats = BeatmapTimingStatistics.calculate(List.of(new TimingPoint(1000,-50,4,0,0,100,false,0),
                red(10000,120),red(20000,240)),15000,35000,35000,0);
        assertEquals(120,stats.commonBpm(), "20s at 120 BPM includes the lead-in; 15s at 240 BPM");
    }

    @ParameterizedTest @CsvSource({"100.5,100", "101.5,102", "179.4,179", "179.6,180"})
    void temposRoundToEvenInteger(double bpm, double expected) {
        var stats = BeatmapTimingStatistics.calculate(List.of(red(0,bpm)),0,60000,60000,0);
        assertEquals(expected,stats.minimumBpm());
        assertEquals(expected,stats.maximumBpm());
        assertEquals(expected,stats.commonBpm());
    }

    @Test void emptyAndInvalidTimingHaveSafeStatistics() {
        assertEquals(new BeatmapTimingStatistics(-1,0,0,0,0),BeatmapTimingStatistics.fromObjects(List.of(),List.of()));
        var stats = BeatmapTimingStatistics.calculate(List.of(red(0,Double.NaN),red(1000,-120),
                new TimingPoint(2000,Double.POSITIVE_INFINITY,4,0,0,100,true,0)),0,60000,60000,0);
        assertEquals(0,stats.commonBpm());
        assertEquals(0,stats.minimumBpm());
        assertEquals(0,stats.maximumBpm());
    }

    @ParameterizedTest @CsvSource({"60999,60,49", "61000,61,50", "999,0,-10", "-999,0,-11"})
    void lengthAndDrainUseSeparateIntegerSeconds(int lastMs, int length, int drain) {
        var stats = BeatmapTimingStatistics.calculate(List.of(),1000,lastMs,lastMs,10000);
        assertEquals(length,stats.lengthSeconds());
        assertEquals(drain,stats.drainSeconds());
    }

    @ParameterizedTest @CsvSource(delimiter=';', value={
            "256,192,65999,1,0;65999;54",
            "256,192,65999,2,0,L|356:192,2,280;65999;54",
            "256,192,65999,8,0,70999;70999;59",
            "256,192,65999,128,0,75999:0:0:0:0:;75999;64"})
    void terminalObjectKindControlsLibraryLength(String lastObject, int length, int drain) throws Exception {
        var chart = new BeatmapFileParser().parse("""
                osu file format v14
                [Events]
                2,5000,10000
                Break,10000,15000
                [TimingPoints]
                0,500,4,0,0,100,1,0
                [HitObjects]
                256,192,1000,1,0
                %s
                """.formatted(lastObject), "terminal.osu").difficulty();
        assertEquals(length,chart.timingStatistics().lengthMs());
        assertEquals(drain,chart.timingStatistics().drainSeconds());
        assertEquals(chart.timingStatistics(),chart.withAssets(null,null).timingStatistics());
        assertEquals(chart.timingStatistics(),chart.withAssets(null,null,null).timingStatistics());
    }

    @Test void fileOrderAndSummedBreaksArePreservedSeparatelyFromGameplayObjectOrder() throws Exception {
        var chart = new BeatmapFileParser().parse("""
                osu file format v14
                [Events]
                2,5000,15000
                2,10000,20000
                2,90000,95000
                2,broken,10000
                Break,5000,4000
                [HitObjects]
                256,192,1000,1,0
                256,192,90000,1,0
                256,192,66999,1,0
                256,192,95000,128,0,NaN:0:0
                """, "order.osu").difficulty();
        assertEquals(66999,chart.timingStatistics().lengthMs(), "Native lightweight scan retains the last file entry");
        assertEquals(40,chart.timingStatistics().drainSeconds(), "Breaks are summed even if overlapping or after the last object");
        assertEquals(90000,chart.hitObjects().getLast().timeMs(), "Gameplay objects retain their sorted order");
        assertEquals(3,chart.hitObjects().size(), "Malformed hold remains a recoverable object-line error");
    }
}
