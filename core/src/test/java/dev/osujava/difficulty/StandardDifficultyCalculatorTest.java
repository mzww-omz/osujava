package dev.osujava.difficulty;

import dev.osujava.beatmap.parse.BeatmapFileParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class StandardDifficultyCalculatorTest {
    // Fixed before comparing results. Stars/skill ratings: 1e-9 absolute;
    // per-object and section values: 1e-7 absolute + 1e-9 relative (single-precision geometry).
    static Stream<String> fixtures() { return Stream.of("empty","single","pair","three","jumps","stream","rhythm","simultaneous","stacks","spinner","gaps","fractional"); }
    private String source(String name,String extension) throws IOException {
        try(var in=getClass().getResourceAsStream("/difficulty/reference-20220902/"+name+extension)) {
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private void close(double expected,double actual) { assertEquals(expected,actual,1e-7+Math.abs(expected)*1e-9); }
    private void series(String expected,List<Double> actual) {
        var values=expected.isEmpty()?new String[0]:expected.split(",");assertEquals(values.length,actual.size());
        for(int i=0;i<values.length;i++) close(Double.parseDouble(values[i]),actual.get(i));
    }
    @ParameterizedTest @MethodSource("fixtures") void matchesPinnedPublicReferenceIncludingIntermediateValues(String name) throws Exception {
        var chart=new BeatmapFileParser().parse(source(name,".osu"),name+".osu").difficulty();
        var expected=new Properties();expected.load(new StringReader(source(name,".properties")));
        var inspected=new StandardDifficultyCalculator().inspect(chart);var result=inspected.result();
        assertEquals(DifficultyResult.Status.SUCCESS,result.status(),result.reason());
        assertEquals(Double.parseDouble(expected.getProperty("stars")),result.stars(),1e-9);
        assertEquals(Double.parseDouble(expected.getProperty("aim")),result.aim(),1e-9);
        assertEquals(Double.parseDouble(expected.getProperty("speed")),result.speed(),1e-9);
        assertEquals(expected.getProperty("heights"),String.join(",",inspected.heights().stream().map(String::valueOf).toList()));
        assertEquals((int)expected.keySet().stream().filter(k->k.toString().startsWith("object.")).count(),inspected.objects().size());
        for(int i=0;i<inspected.objects().size();i++) {
            var f=inspected.objects().get(i);
            series(expected.getProperty("object."+i),List.of(f.strainTime(),f.distance(),f.angle(),f.aim(),f.speed(),f.rhythm()));
        }
        series(expected.getProperty("aimPeaks"),inspected.aimPeaks());series(expected.getProperty("speedPeaks"),inspected.speedPeaks());
        assertEquals(result,new StandardDifficultyCalculator().calculate(chart));
    }
    @Test void unsupportedMapsRemainUnknownRatherThanAHeuristicRating() throws Exception {
        var parser=new BeatmapFileParser();
        for(String source : List.of("osu file format v5\n[HitObjects]\n100,100,1000,1,0", "osu file format v14\n[General]\nMode:1\n[HitObjects]\n100,100,1000,1,0", "osu file format v14\n[HitObjects]\n100,100,1000,2,0,L|200:100,1,100")) {
            var result=new StandardDifficultyCalculator().calculate(parser.parse(source,"fixture.osu").difficulty());
            assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.rating().isEmpty());
        }
    }
    private dev.osujava.beatmap.BeatmapDifficulty withObjects(dev.osujava.beatmap.BeatmapDifficulty base,List<dev.osujava.beatmap.HitObject> objects) {
        return new dev.osujava.beatmap.BeatmapDifficulty(base.title(),base.artist(),base.creator(),base.version(),base.mode(),base.audioFilename(),base.backgroundFilename(),
                base.settings(),base.timingPoints(),objects,null,null,null);
    }
    @Test void excessiveObjectCountDurationAndStackWorkAreBoundedWithoutSyntheticRatings() throws Exception {
        var base=new BeatmapFileParser().parse(source("single",".osu"),"fixture.osu").difficulty();
        var circle=base.hitObjects().getFirst();
        var excessive=withObjects(base,Collections.nCopies(StandardDifficultyCalculator.MAX_OBJECTS+1,circle));
        var longMap=withObjects(base,List.of(circle,new dev.osujava.beatmap.HitObject(300,200,circle.timeMs()+6*60*60*1000L+1,dev.osujava.beatmap.HitObject.Type.CIRCLE,1,0)));
        var comparisons=new ArrayList<dev.osujava.beatmap.HitObject>();
        for(int i=0;i<2002;i++)comparisons.add(new dev.osujava.beatmap.HitObject(i*5,100,1000,dev.osujava.beatmap.HitObject.Type.CIRCLE,1,0));
        for(var chart:List.of(excessive,longMap,withObjects(base,comparisons))) {
            var result=new StandardDifficultyCalculator().calculate(chart);assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.rating().isEmpty());
        }
    }
    @Test void invalidGeometryAndOutOfOrderObjectsFailRatherThanPoisoningOtherJobs() throws Exception {
        var base=new BeatmapFileParser().parse(source("pair",".osu"),"fixture.osu").difficulty();
        for(var objects:List.of(List.of(base.hitObjects().getLast(),base.hitObjects().getFirst()),
                List.of(new dev.osujava.beatmap.HitObject(Double.NaN,100,1000,dev.osujava.beatmap.HitObject.Type.CIRCLE,1,0)))) {
            var result=new StandardDifficultyCalculator().calculate(withObjects(base,objects));assertEquals(DifficultyResult.Status.FAILED,result.status());assertTrue(result.rating().isEmpty());
        }
        assertEquals(DifficultyResult.Status.SUCCESS,new StandardDifficultyCalculator().calculate(base).status());
    }
    @Test void cancellationDoesNotBecomeAStoredFailedRating() throws Exception {
        var chart=new BeatmapFileParser().parse(source("jumps",".osu"),"fixture.osu").difficulty();
        Thread.currentThread().interrupt();
        try { assertThrows(java.util.concurrent.CancellationException.class,()->new StandardDifficultyCalculator().calculate(chart)); }
        finally { Thread.interrupted(); }
    }
    @Test void emptyVerifiedChartIsKnownZeroAndMissingRatingIsUnknown() throws Exception {
        var result=new StandardDifficultyCalculator().calculate(new BeatmapFileParser().parse(source("empty",".osu"),"empty.osu").difficulty());
        assertEquals(0,result.rating().orElseThrow());assertTrue(DifficultyResult.pending().rating().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new DifficultyResult(DifficultyResult.Status.SUCCESS,Double.NaN,0.0,0.0,""));
    }
}
