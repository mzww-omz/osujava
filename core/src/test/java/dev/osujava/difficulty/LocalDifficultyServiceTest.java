package dev.osujava.difficulty;

import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalDifficultyServiceTest {
    @TempDir Path directory;
    private static final DifficultyResult VALUE=new DifficultyResult(DifficultyResult.Status.SUCCESS,3.25,1.25,2.5,"");
    private BeatmapDifficulty chart(int i) throws Exception {
        return new BeatmapFileParser().parse("osu file format v14\n[Metadata]\nTitle:Map "+i+"\nArtist:A\nCreator:C\nVersion:D\n[HitObjects]\n"+i+",100,1000,1,0","map.osu").difficulty().withAssets(null,null,Path.of("map-"+i+".osu"));
    }
    private List<BeatmapSet> sets(BeatmapDifficulty... charts) { return List.of(new BeatmapSet("set","Map","A","C",null,null,List.of(charts),List.of())); }
    private void until(BooleanSupplier condition) { assertTimeoutPreemptively(Duration.ofSeconds(10),()->{ while(!condition.getAsBoolean()) Thread.sleep(1); }); }
    private void finished(LocalDifficultyService service,int size) { until(()->{service.drain();return service.diagnostics().published()==size;}); }
    @Test void frameMethodsDoNoCalculationAndContentDuplicatesShareOneResultAndCache() throws Exception {
        var first=chart(100);var copy=first.withAssets(null,null,Path.of("moved.osu"));
        var calls=new AtomicInteger();var thread=new AtomicReference<Thread>();Thread ui=Thread.currentThread();
        try(var service=new LocalDifficultyService(directory,d->{calls.incrementAndGet();thread.set(Thread.currentThread());return VALUE;},"alg-1","pre-1")) {
            service.library(sets(first,copy));service.prioritize(copy);finished(service,1);
            assertNotSame(ui,thread.get());assertEquals(1,calls.get());assertEquals(VALUE,service.result(first));assertEquals(VALUE,service.result(copy));
            long revision=service.diagnostics().revision();for(int i=0;i<100;i++){service.result(first);service.prioritize(copy);assertTrue(service.drain().isEmpty());}
            assertEquals(revision,service.diagnostics().revision());assertEquals(1,calls.get());
        }
        try(var service=new LocalDifficultyService(directory,d->{fail("Warm cache must avoid recalculation");return VALUE;},"alg-1","pre-1")) {
            service.library(sets(copy));finished(service,1);assertEquals(VALUE,service.result(copy));
        }
        try(var paths=Files.list(directory)) { assertEquals(1,paths.count()); }
    }
    @Test void newlyVerifiedLinearRatingBypassesTheOldUnsupportedCacheAndSurvivesRestart() throws Exception {
        var chart=new BeatmapFileParser().parse(Path.of("src/test/resources/difficulty/reference-20220902/linear-repeat.osu")).difficulty();
        var old=new DifficultyKey(BeatmapContentKey.of(chart),List.of(),"osu-java-nm-20220902-1","circle-spinner-f32-v6-1");
        var cache=new DifficultyCache(directory);cache.save(old,DifficultyResult.unsupported("Slider preprocessing is not verified"));
        byte[] previous=Files.readAllBytes(cache.path(old));DifficultyResult calculated;
        try(var service=new LocalDifficultyService(directory)) {service.library(sets(chart));finished(service,1);calculated=service.result(chart);assertEquals(DifficultyResult.Status.SUCCESS,calculated.status());assertEquals(1.677664082435828,calculated.stars(),1e-9);}
        assertArrayEquals(previous,Files.readAllBytes(cache.path(old)));
        try(var service=new LocalDifficultyService(directory,d->{fail("Linear slider must use its warm versioned cache");return VALUE;},StandardDifficultyCalculator.ALGORITHM_VERSION,StandardDifficultyCalculator.PREPROCESS_VERSION)) {
            service.library(sets(chart));finished(service,1);assertEquals(calculated,service.result(chart));
        }
    }
    @Test void newlyVerifiedBezierIgnoresTheLinearOnlyUnsupportedCache() throws Exception {
        var chart=new BeatmapFileParser().parse(Path.of("src/test/resources/difficulty/reference-20220902/curve-bezier.osu")).difficulty();
        var old=new DifficultyKey(BeatmapContentKey.of(chart),List.of(),"osu-java-nm-20220902-2","linear-slider-f32-v8-1");
        var cache=new DifficultyCache(directory);cache.save(old,DifficultyResult.unsupported("Only Linear paths verified"));
        byte[] previous=Files.readAllBytes(cache.path(old));DifficultyResult calculated;
        try(var service=new LocalDifficultyService(directory)) {service.library(sets(chart));finished(service,1);calculated=service.result(chart);assertEquals(DifficultyResult.Status.SUCCESS,calculated.status());assertEquals(1.7442657382422755,calculated.stars(),1e-9);}
        assertArrayEquals(previous,Files.readAllBytes(cache.path(old)));
        try(var service=new LocalDifficultyService(directory,d->{fail("Bezier must use its warm curve cache");return VALUE;},StandardDifficultyCalculator.ALGORITHM_VERSION,StandardDifficultyCalculator.PREPROCESS_VERSION)) {
            service.library(sets(chart));finished(service,1);assertEquals(calculated,service.result(chart));
        }
    }
    @Test void algorithmPreprocessingModeAndNormalizedModsAreIndependentCacheKeys() throws Exception {
        var content=BeatmapContentKey.of(chart(100));
        var a=new DifficultyKey(content,List.of("HR","HD","HD"),"alg","pre");var b=new DifficultyKey(content,List.of("HD","HR"),"alg","pre");
        assertEquals(a,b);assertEquals(a.filename(),b.filename());
        for(var other:List.of(new DifficultyKey(content,List.of(),"alg","pre"),new DifficultyKey(content,List.of("HD","HR"),"alg-2","pre"),new DifficultyKey(content,List.of("HD","HR"),"alg","pre-2"),new DifficultyKey(new BeatmapContentKey(content.sha256(),1),List.of("HD","HR"),"alg","pre"))) assertNotEquals(a.filename(),other.filename());
        var chart=chart(100);var count=new AtomicInteger();
        for(String version:List.of("alg-1","alg-2")) try(var service=new LocalDifficultyService(directory,d->{count.incrementAndGet();return VALUE;},version,"pre")) { service.library(sets(chart));finished(service,1); }
        assertEquals(2,count.get());
    }
    @Test void unsupportedAndFailedResultsPersistWithoutPerFrameOrRestartRetry() throws Exception {
        var a=chart(100);var b=chart(101);var calls=new AtomicInteger();
        try(var service=new LocalDifficultyService(directory,d->{calls.incrementAndGet();if(d==a)throw new IllegalStateException();return DifficultyResult.unsupported("Unverified slider");},"alg","pre")) {
            service.library(sets(a,b));finished(service,2);
            assertEquals(DifficultyResult.Status.FAILED,service.result(a).status());assertEquals(DifficultyResult.Status.UNSUPPORTED,service.result(b).status());
            assertTrue(service.result(a).rating().isEmpty());assertTrue(service.result(b).rating().isEmpty());
            for(int i=0;i<100;i++) { service.prioritize(a);service.prioritize(b);service.drain(); }assertEquals(2,calls.get());
        }
        try(var service=new LocalDifficultyService(directory,d->{fail("Terminal result should be cached");return VALUE;},"alg","pre")) { service.library(sets(a,b));finished(service,2); }
    }
    @Test void malformedFutureCacheAndStorageFailureNeverHideCalculatedValuesOrOverwriteRecords() throws Exception {
        var chart=chart(100);var key=new DifficultyKey(BeatmapContentKey.of(chart),List.of(),"alg","pre");
        var path=new DifficultyCache(directory).path(key);String future="schema=999\nfuture=keep\n";Files.writeString(path,future);
        try(var service=new LocalDifficultyService(directory,d->VALUE,"alg","pre")) { service.library(sets(chart));finished(service,1);assertEquals(VALUE,service.result(chart));assertFalse(service.diagnostics().storageWarning().isEmpty()); }
        assertEquals(future,Files.readString(path));
        Files.writeString(path,"schema=1\nstatus=SUCCESS\nstars=NaN\n");byte[] corrupt=Files.readAllBytes(path);
        try(var service=new LocalDifficultyService(directory,d->VALUE,"alg","pre")) { service.library(sets(chart));finished(service,1);assertEquals(VALUE,service.result(chart)); }
        assertArrayEquals(corrupt,Files.readAllBytes(path));
        Path blocked=directory.resolve("file");Files.writeString(blocked,"keep");
        try(var service=new LocalDifficultyService(blocked,d->VALUE,"alg","pre")) { service.library(sets(chart));finished(service,1);assertEquals(VALUE,service.result(chart));assertFalse(service.diagnostics().storageWarning().isEmpty()); }
        assertEquals("keep",Files.readString(blocked));
    }
    @Test void selectedWorkHasPriorityAndTenThousandChartsDoNotCreateUnboundedQueues() throws Exception {
        var first=chart(0);var charts=new ArrayList<BeatmapDifficulty>();charts.add(first);
        for(int i=1;i<10_000;i++) {
            var data=new BeatmapPlayData(List.of(),0,String.format(Locale.ROOT,"%064x",i),"");
            charts.add(new BeatmapDifficulty("M","A","C","D",0,"","",first.settings(),List.of(),first.hitObjects(),null,null,Path.of(i+".osu"),0,first.metadata(),first.timingStatistics(),data));
        }
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var order=Collections.synchronizedList(new ArrayList<BeatmapDifficulty>());
        try(var service=new LocalDifficultyService(null,d->{order.add(d);if(d==first){entered.countDown();try{release.await();}catch(InterruptedException e){throw new CancellationException();}}return VALUE;},"alg","pre")) {
            service.library(sets(charts.toArray(BeatmapDifficulty[]::new)));assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=100;i<150;i++)service.prioritize(charts.get(i));service.prioritize(charts.getLast());
            assertEquals(10_000,service.diagnostics().libraryContents());assertTrue(service.diagnostics().queued()<=LocalDifficultyService.QUEUE_LIMIT);
            release.countDown();until(()->service.diagnostics().awaitingPublication()==LocalDifficultyService.COMPLETION_LIMIT);
            assertSame(charts.getLast(),order.get(1));assertEquals(LocalDifficultyService.COMPLETION_LIMIT,order.size());
            assertTrue(service.diagnostics().queued()<=LocalDifficultyService.QUEUE_LIMIT);
            service.drain();assertEquals(1,service.diagnostics().revision());
        } finally {release.countDown();}
    }
    @Test void libraryReplacementDiscardsOldCompletionAndCloseCancelsActiveCalculation() throws Exception {
        var old=chart(100);var next=chart(101);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var service=new LocalDifficultyService(null,d->{if(d==old){entered.countDown();try{release.await();}catch(InterruptedException e){throw new CancellationException();}}return VALUE;},"alg","pre")) {
            service.library(sets(old));assertTrue(entered.await(5,TimeUnit.SECONDS));long generation=service.diagnostics().generation();
            service.library(sets(next));assertTrue(service.diagnostics().generation()>generation);release.countDown();finished(service,1);
            assertEquals(VALUE,service.result(next));assertEquals(DifficultyResult.Status.PENDING,service.result(old).status());
            assertEquals(1,service.diagnostics().revision());
        } finally {release.countDown();}
        var running=new CountDownLatch(1);var cancelled=new CountDownLatch(1);
        var service=new LocalDifficultyService(directory,d->{running.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){cancelled.countDown();throw new CancellationException();}return VALUE;},"cancel","pre");
        service.library(sets(old));assertTrue(running.await(5,TimeUnit.SECONDS));service.close();assertTrue(cancelled.await(5,TimeUnit.SECONDS));
        assertTrue(service.drain().isEmpty());assertTrue(service.diagnostics().closed());assertEquals(0,service.diagnostics().queued());
        assertFalse(Files.exists(new DifficultyCache(directory).path(new DifficultyKey(BeatmapContentKey.of(old),List.of(),"cancel","pre"))));
    }
    @Test void rawContentChangeInvalidatesRatingButMovingTheSameContentPreservesIt() throws Exception {
        var original=chart(100);var edit=chart(101);var count=new AtomicInteger();
        try(var service=new LocalDifficultyService(null,d->{count.incrementAndGet();return VALUE;},"alg","pre")) {
            service.library(sets(original));finished(service,1);
            var moved=original.withAssets(null,null,Path.of("elsewhere.osu"));service.library(sets(moved));assertEquals(VALUE,service.result(moved));assertEquals(1,count.get());
            service.library(sets(edit));finished(service,1);assertEquals(2,count.get());assertEquals(DifficultyResult.Status.PENDING,service.result(moved).status());
        }
    }
}
