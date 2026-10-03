package dev.osujava.difficulty;

import dev.osujava.beatmap.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/** One bounded worker per Song Select, lazy-started. UI methods perform no disk I/O or calculation.
 * Library replacement invalidates queued/completed work by generation; close interrupts active work. */
public final class LocalDifficultyService implements AutoCloseable {
    public static final int QUEUE_LIMIT=32, COMPLETION_LIMIT=64;
    private static final DifficultyResult PENDING=DifficultyResult.pending();
    private final DifficultyCache cache;
    private final Function<BeatmapDifficulty,DifficultyResult> calculator;
    private final String algorithm,preprocessing;
    private final Map<BeatmapContentKey,DifficultyResult> published=new HashMap<>();
    private final Map<BeatmapDifficulty,Job> byDifficulty=new IdentityHashMap<>();
    private List<BeatmapSet> source;
    private List<Job> jobs=List.of();
    private final LinkedList<Job> queue=new LinkedList<>();
    private final ArrayDeque<Completed> completions=new ArrayDeque<>();
    private int cursor;
    private long generation,revision;
    private Thread worker;
    private boolean closed;
    private String warning="";
    private enum State { WAITING, QUEUED, RUNNING, DONE }
    private static final class Job {
        final DifficultyKey key; final BeatmapDifficulty chart; final long generation; final int index;
        State state=State.WAITING;
        Job(DifficultyKey key,BeatmapDifficulty chart,long generation,int index) { this.key=key;this.chart=chart;this.generation=generation;this.index=index; }
    }
    private record Completed(Job job,DifficultyResult result) { }
    public record Diagnostics(long generation,long revision,int queued,int awaitingPublication,int libraryContents,int published,String storageWarning,boolean closed) { }
    public LocalDifficultyService(Path cacheDirectory) {
        this(cacheDirectory,new StandardDifficultyCalculator()::calculate,StandardDifficultyCalculator.ALGORITHM_VERSION,StandardDifficultyCalculator.PREPROCESS_VERSION);
    }
    LocalDifficultyService(Path cacheDirectory,Function<BeatmapDifficulty,DifficultyResult> calculator,String algorithm,String preprocessing) {
        cache=new DifficultyCache(cacheDirectory);this.calculator=Objects.requireNonNull(calculator);this.algorithm=algorithm;this.preprocessing=preprocessing;
    }
    public synchronized void library(List<BeatmapSet> sets) {
        if(closed || source==sets) return;
        source=sets;generation++;cursor=0;queue.clear();completions.clear();byDifficulty.clear();
        var unique=new LinkedHashMap<BeatmapContentKey,Job>();
        for(var set:sets) for(var chart:set.difficulties()) {
            var content=BeatmapContentKey.of(chart);if(content==null) continue;
            Job job=unique.get(content);
            if(job==null) { job=new Job(new DifficultyKey(content,List.of(),algorithm,preprocessing),chart,generation,unique.size());unique.put(content,job); }
            byDifficulty.put(chart,job);
        }
        published.keySet().retainAll(unique.keySet());
        jobs=List.copyOf(unique.values());for(Job job:jobs) if(published.containsKey(job.key.content())) job.state=State.DONE;
        fill();
        if(worker==null && !jobs.isEmpty()) { worker=new Thread(this::work,"osu-java-local-difficulty");worker.setDaemon(true);worker.start(); }
        notifyAll();
    }
    /** Promote selected last (after visible rows) so it has highest priority. Never enlarges the queue. */
    public synchronized void prioritize(BeatmapDifficulty chart) {
        Job job=byDifficulty.get(chart);
        if(closed || job==null || job.state==State.DONE || job.state==State.RUNNING) return;
        if(job.state==State.QUEUED) queue.remove(job);
        else if(queue.size()==QUEUE_LIMIT) {
            Job displaced=queue.removeLast();displaced.state=State.WAITING;cursor=Math.min(cursor,displaced.index);
        }
        job.state=State.QUEUED;queue.addFirst(job);notifyAll();
    }
    public synchronized DifficultyResult result(BeatmapDifficulty chart) {
        Job job=byDifficulty.get(chart);
        return job==null ? PENDING : published.getOrDefault(job.key.content(),PENDING);
    }
    /** Called once per frame. One revision for the entire batch, with no full-library snapshot copy. */
    public synchronized List<BeatmapContentKey> drain() {
        if(closed || completions.isEmpty()) return List.of();
        var changed=new ArrayList<BeatmapContentKey>();
        while(!completions.isEmpty()) {
            Completed completed=completions.removeFirst();Job job=completed.job;
            if(job.generation!=generation) continue;
            var previous=published.put(job.key.content(),completed.result);
            if(!completed.result.equals(previous)) changed.add(job.key.content());
        }
        if(!changed.isEmpty()) revision++;
        fill();notifyAll();return List.copyOf(changed);
    }
    public synchronized Diagnostics diagnostics() { return new Diagnostics(generation,revision,queue.size(),completions.size(),jobs.size(),published.size(),warning,closed); }
    private void fill() {
        while(cursor<jobs.size() && queue.size()<QUEUE_LIMIT) {
            Job job=jobs.get(cursor++);
            if(job.state==State.WAITING) { job.state=State.QUEUED;queue.addLast(job); }
        }
    }
    private void work() {
        try {
            while(true) {
                Job job;
                synchronized(this) {
                    while(!closed && (queue.isEmpty() || completions.size()>=COMPLETION_LIMIT)) { fill();if(!queue.isEmpty() && completions.size()<COMPLETION_LIMIT) break;wait(); }
                    if(closed) return;
                    job=queue.removeFirst();job.state=State.RUNNING;fill();
                }
                var read=cache.read(job.key);
                DifficultyResult result=read.result();String storageWarning=read.warning();
                if(result==null) {
                    try { result=Objects.requireNonNull(calculator.apply(job.chart));if(result.status()==DifficultyResult.Status.PENDING) result=DifficultyResult.failed("Calculator returned no terminal result"); }
                    catch(CancellationException cancelled) { return; }
                    catch(RuntimeException failure) { result=DifficultyResult.failed("Difficulty calculation failed"); }
                    if(Thread.currentThread().isInterrupted()) return;
                    if(read.writable()) storageWarning=cache.save(job.key,result);
                }
                synchronized(this) {
                    job.state=State.DONE;
                    if(!storageWarning.isEmpty()) warning=storageWarning;
                    if(!closed && job.generation==generation) completions.addLast(new Completed(job,result));
                    fill();notifyAll();
                }
            }
        } catch(InterruptedException cancelled) { Thread.currentThread().interrupt(); }
    }
    @Override public synchronized void close() {
        if(closed) return;
        closed=true;generation++;queue.clear();completions.clear();jobs=List.of();byDifficulty.clear();published.clear();source=null;
        if(worker!=null) worker.interrupt();notifyAll();
    }
}
