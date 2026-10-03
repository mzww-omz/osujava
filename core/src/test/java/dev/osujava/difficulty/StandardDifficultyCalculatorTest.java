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
    static Stream<String> fixtures() { return Stream.of("empty","single","pair","three","jumps","stream","rhythm","simultaneous","stacks","spinner","gaps","fractional","linear-basic","linear-repeat","linear-polyline","linear-sv","linear-stacks","linear-late-tick","linear-duplicate","linear-no-timing","linear-rhythm","linear-single","linear-spinner","linear-future-timing","curve-bezier","curve-bezier-segments","curve-bezier-high-degree","curve-perfect","curve-perfect-major","curve-perfect-fallback","curve-catmull","curve-catmull-duplicates","curve-mixed","curve-stacks","curve-fractional-controls","curve-loop","curve-catmull-v128","curve-perfect-reverse"); }
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
            series(expected.getProperty("object."+i),List.of(f.strainTime(),f.distance(),f.angle(),f.aim(),f.speed(),f.rhythm(),f.travelDistance(),f.travelTime(),f.minimumJumpDistance(),f.minimumJumpTime()));
        }
        var sliders=SliderPreprocessing.prepare(chart);
        float scale=(1f-.7f*((float)chart.settings().circleSize()-5)/5)/2;
        for(int i=0;i<sliders.length;i++) if(sliders[i]!=null) {
            var slider=sliders[i];var object=chart.hitObjects().get(i);
            float offset=inspected.heights().get(i)*scale*-6.4f;
            slider.cursor(offset,64*scale);
            var end=new SliderPreprocessing.Vec((float)object.x(),(float)object.y()).plus(slider.endRelative);
            series(expected.getProperty("slider."+i),List.of(slider.path.distance,slider.spanDuration,slider.end,(double)end.x(),(double)end.y(),slider.lazyTime,(double)slider.lazyDistance,(double)slider.lazy.x(),(double)slider.lazy.y()));
            assertEquals((int)expected.keySet().stream().filter(k->k.toString().startsWith("nested."+chart.hitObjects().indexOf(object)+".")).count(),slider.nested.size());
            for(int j=0;j<slider.nested.size();j++) {
                var n=slider.nested.get(j);var pos=slider.nestedPosition(n.relative(),offset);
                series(expected.getProperty("nested."+i+"."+j),List.of(n.time(),(double)pos.x(),(double)pos.y(),n.repeat()?1.0:0.0));
            }
            var positions=new ArrayList<Double>();
            for(double progress:new double[]{0,.25,.5,.75,1}) {var pos=slider.path.at(progress);positions.add((double)pos.x());positions.add((double)pos.y());}
            series(expected.getProperty("path."+i),positions);
        }
        series(expected.getProperty("aimPeaks"),inspected.aimPeaks());series(expected.getProperty("speedPeaks"),inspected.speedPeaks());
        assertEquals(result,new StandardDifficultyCalculator().calculate(chart));
    }
    @Test void unsupportedMapsRemainUnknownRatherThanAHeuristicRating() throws Exception {
        var parser=new BeatmapFileParser();
        for(String source : List.of("osu file format v5\n[HitObjects]\n100,100,1000,1,0", "osu file format v14\n[General]\nMode:1\n[HitObjects]\n100,100,1000,1,0", "osu file format v14\n[HitObjects]\n100,100,1000,2,0,B2|150:200|200:100,1,100")) {
            var result=new StandardDifficultyCalculator().calculate(parser.parse(source,"fixture.osu").difficulty());
            assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.rating().isEmpty());
        }
    }
    @Test void unverifiedOrExcessiveSliderPreprocessingStaysUnknownAndDoesNotPoisonTheNextJob() throws Exception {
        String header="osu file format v14\n[TimingPoints]\n0,500,4,0,0,100,1,0\n[HitObjects]\n";
        var sources=List.of(
                header.replace("v14","v7")+"100,100,1000,2,0,L|200:100,1,100",
                header+"100,100,1000,2,0,L|200:100,1,0",
                header+"100,100,1000,2,0,L|100:100,1,100",
                header+"100,100,1000,2,0,L|200:100,2,7",
                header+"100,100,1000,2,0,L|200:100,1,100001",
                header.replace("[HitObjects]","500,-33,4,0,0,100,0,0\n[HitObjects]")+"100,100,1000,2,0,L|200:100,1,100",
                header.replace("[HitObjects]","0,-50,4,0,0,100,0,0\n[HitObjects]")+"100,100,1000,2,0,L|200:100,1,100",
                header.replace("[HitObjects]","500,NaN,4,0,0,100,0,0\n[HitObjects]")+"100,100,1000,2,0,L|200:100,1,100");
        var parser=new BeatmapFileParser();var calculator=new StandardDifficultyCalculator();
        for(String text:sources) {
            var result=calculator.calculate(parser.parse(text,"bounded.osu").difficulty());
            assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status(),text+": "+result.reason());assertTrue(result.rating().isEmpty());
        }
        var base=parser.parse(source("linear-basic",".osu"),"valid.osu").difficulty();var slider=base.hitObjects().getFirst();
        var excessive=new dev.osujava.beatmap.HitObject(slider.x(),slider.y(),slider.timeMs(),slider.type(),slider.rawType(),slider.hitSound(),
                new dev.osujava.beatmap.SliderData(slider.sliderData().segments(),Integer.MAX_VALUE,100));
        assertEquals(DifficultyResult.Status.UNSUPPORTED,calculator.calculate(withObjects(base,List.of(excessive))).status());
        assertEquals(DifficultyResult.Status.SUCCESS,calculator.calculate(parser.parse(source("linear-repeat",".osu"),"valid.osu").difficulty()).status());
    }
    @Test void nestedObjectBudgetStopsOtherwiseValidLongRepeatSliders() throws Exception {
        String text=source("linear-basic",".osu").replace("L|456:192,1,200","L|456:192,500,10000").replace("SliderTickRate:1","SliderTickRate:8");
        var result=new StandardDifficultyCalculator().calculate(new BeatmapFileParser().parse(text,"budget.osu").difficulty());
        assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.reason().contains("work limit"),result.reason());
    }
    @Test void excessiveCurveControlsAndSubdivisionWorkStayBounded() throws Exception {
        var parser=new BeatmapFileParser();var calculator=new StandardDifficultyCalculator();
        for(var type:List.of("B","C")) {
            var path=new StringBuilder(type);
            for(int n=0;n<65;n++) path.append('|').append(n*5).append(':').append(n%2==0?0:300);
            var chart=parser.parse("osu file format v14\n[HitObjects]\n100,100,1000,2,0,"+path+",1,400","controls.osu").difficulty();
            var result=calculator.calculate(chart);assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.reason().contains("control point limit"),result.reason());
        }
        var path=new StringBuilder("B");
        for(int n=0;n<63;n++) path.append('|').append(n%2==0?100000:-100000).append(':').append(n%3==0?100000:-100000);
        var result=calculator.calculate(parser.parse("osu file format v14\n[HitObjects]\n100,100,1000,2,0,"+path+",1,1000","work.osu").difficulty());
        assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.reason().contains("work limit"),result.reason());
        assertEquals(DifficultyResult.Status.SUCCESS,calculator.calculate(parser.parse(source("curve-bezier",".osu"),"valid.osu").difficulty()).status());
    }
    @Test void invalidExplicitCurveSegmentIsUnknownWithoutChangingTheGameplayModel() throws Exception {
        var parser=new BeatmapFileParser();var chart=parser.parse("osu file format v14\n[HitObjects]\n100,100,1000,2,0,B|200:200|L,1,400","empty-segment.osu").difficulty();
        assertEquals(1,chart.hitObjects().size());assertEquals(2,chart.hitObjects().getFirst().sliderData().segments().size());
        var result=new StandardDifficultyCalculator().calculate(chart);assertEquals(DifficultyResult.Status.UNSUPPORTED,result.status());assertTrue(result.rating().isEmpty());
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
