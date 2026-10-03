package dev.osujava.difficulty;

import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** Opt-in service publication probe, not a UI/GPU or representative real-library benchmark.
 * Source creation, JIT warmup and CSV reporting are outside the measured frame samples.
 * Calculator callback CPU/allocation excludes cache I/O; frame samples exclude the 60 Hz wait.
 */
public final class LocalDifficultyPerformanceProbe {
    private static final long FRAME_NS = 1_000_000_000L / 60;
    private static final List<String> FIXTURES = List.of(
            "empty", "single", "pair", "three", "jumps", "stream", "rhythm", "simultaneous", "stacks", "spinner", "gaps", "fractional",
            "linear-basic", "linear-repeat", "linear-polyline", "linear-sv", "linear-stacks", "linear-late-tick", "linear-duplicate", "linear-no-timing",
            "linear-rhythm", "linear-single", "linear-spinner", "linear-future-timing", "curve-bezier", "curve-bezier-segments", "curve-bezier-high-degree",
            "curve-perfect", "curve-perfect-major", "curve-perfect-fallback", "curve-catmull", "curve-catmull-duplicates", "curve-mixed", "curve-stacks",
            "curve-fractional-controls", "curve-loop", "curve-catmull-v128", "curve-perfect-reverse");
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private record Corpus(List<BeatmapSet> sets, List<BeatmapDifficulty> unique, List<BeatmapDifficulty> copies) { }

    public static void main(String[] args) throws Exception {
        int count = Integer.getInteger("osujava.difficultyProbeCount", 10_000);
        int duplicates = Integer.getInteger("osujava.difficultyProbeDuplicates", 1_000);
        int seconds = Integer.getInteger("osujava.difficultyProbeTimeoutSeconds", 120);
        require(count >= FIXTURES.size() && count <= 100_000, "Unique content count must be between 38 and 100000");
        require(duplicates >= 0 && duplicates <= count, "Duplicate count must be between zero and unique count");
        require(seconds >= 1 && seconds <= 600, "Timeout must be between 1 and 600 seconds per phase");
        require(THREADS.isThreadCpuTimeSupported() && THREADS.isThreadAllocatedMemorySupported(), "Thread CPU/allocation counters unavailable");
        THREADS.setThreadCpuTimeEnabled(true);
        THREADS.setThreadAllocatedMemoryEnabled(true);
        var sources = loadSources();
        var warmup = corpus(sources, FIXTURES.size(), 0, "JIT");
        var calculator = new StandardDifficultyCalculator();
        for (int round = 0; round < 50; round++) for (var chart : warmup.unique())
            require(calculator.calculate(chart).status() == DifficultyResult.Status.SUCCESS, "Unverified baseline fixture: " + chart.title());
        // Warm the service path separately, without creating records for the measured corpus.
        run("jit-service", null, warmup, seconds, false, false);
        var corpus = corpus(sources, count, duplicates, "Measured 夜空");
        Path cache = Files.createTempDirectory("osujava-difficulty-probe-");
        try {
            System.out.println("# Synthetic original-38-fixture service probe; no GL, artwork, UI classification or importer timing.");
            System.out.println("# Real wall-clock 60 Hz drain; calculator counters exclude cache I/O and include measurement overhead.");
            System.out.println("phase,unique_contents,duplicate_rows,frames,registration_ms,registration_bytes,publication_ms,selected_publication_ms,frame_mean_us,frame_p95_us,frame_p99_us,frame_max_us,frame_bytes_mean,queue_max_sampled,completion_max_sampled,calculator_calls,calculator_cpu_ms,calculator_bytes,success,unsupported,failed,heap_peak_sampled_bytes,gc_count,gc_ms");
            var cold = run("cold", cache, corpus, seconds, true, true);
            var warm = run("reopened-warm", cache, corpus, seconds, true, true);
            require(cold.equals(warm), "Cold/warm result values differ");
        } finally {
            try (var paths = Files.walk(cache)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static List<String> loadSources() throws IOException {
        var sources = new ArrayList<String>();
        for (String name : FIXTURES) {
            String path = "/difficulty/reference-20220902/" + name + ".osu";
            try (var in = LocalDifficultyPerformanceProbe.class.getResourceAsStream(path)) {
                if (in == null) throw new IOException("Missing fixture: " + path);
                sources.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return List.copyOf(sources);
    }

    private static Corpus corpus(List<String> sources, int count, int duplicates, String prefix) throws Exception {
        var parser = new BeatmapFileParser();
        var unique = new ArrayList<BeatmapDifficulty>();
        var copies = new ArrayList<BeatmapDifficulty>();
        var sets = new ArrayList<BeatmapSet>();
        var keys = new HashSet<BeatmapContentKey>();
        for (int i = 0; i < count; i++) {
            var chart = parser.parse(distinctSource(sources, prefix, i), "source-" + i + ".osu").difficulty();
            require(keys.add(BeatmapContentKey.of(chart)), "Generated sources share a content hash");
            unique.add(chart);
            sets.add(set("original-" + i, chart));
        }
        for (int i = 0; i < duplicates; i++) {
            var chart = parser.parse(distinctSource(sources, prefix, i), "copy-" + i + ".osu").difficulty();
            require(chart != unique.get(i) && BeatmapContentKey.of(chart).equals(BeatmapContentKey.of(unique.get(i))), "Duplicate content contract failed");
            copies.add(chart);
            sets.add(set("copy-" + i, chart));
        }
        return new Corpus(List.copyOf(sets), List.copyOf(unique), List.copyOf(copies));
    }

    private static String distinctSource(List<String> sources, String prefix, int index) {
        String source = sources.get(index % sources.size());
        String title = "Title:" + prefix + " " + index + " " + FIXTURES.get(index % FIXTURES.size());
        require(source.contains("\nTitle:"), "Fixture lacks explicit metadata Title");
        return source.replaceFirst("(?m)^Title:[^\\r\\n]*", title);
    }

    private static BeatmapSet set(String id, BeatmapDifficulty chart) {
        return new BeatmapSet(id, chart.title(), chart.artist(), chart.creator(), null, null, List.of(chart), List.of());
    }

    private static List<DifficultyResult> run(String phase, Path cache, Corpus corpus, int seconds, boolean paced, boolean report) throws Exception {
        var measured = new MeasuredCalculator();
        var service = new LocalDifficultyService(cache, measured, StandardDifficultyCalculator.ALGORITHM_VERSION, StandardDifficultyCalculator.PREPROCESS_VERSION);
        Thread worker = null;
        try {
            long uiThread = Thread.currentThread().threadId();
            var frames = new ArrayList<Long>();
            long frameBytes = 0, peakHeap = heap(), gcCount = gc(false), gcMs = gc(true);
            long started = System.nanoTime(), registrationBytes = THREADS.getThreadAllocatedBytes(uiThread);
            service.library(corpus.sets());
            long registrationNs = System.nanoTime() - started;
            registrationBytes = THREADS.getThreadAllocatedBytes(uiThread) - registrationBytes;
            var workerField = LocalDifficultyService.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            worker = (Thread) workerField.get(service);
            require(worker != null, "No worker started for hashed corpus");
            var selected = corpus.unique().getLast();
            service.prioritize(selected);
            long selectedNs = -1, nextFrame = System.nanoTime(), deadline = started + Duration.ofSeconds(seconds).toNanos();
            int queueMax = 0, completionMax = 0;
            while (true) {
                if (paced) waitUntil(nextFrame);
                require(System.nanoTime() < deadline, "Publication timeout in " + phase + ": " + service.diagnostics());
                var before = service.diagnostics();
                queueMax = Math.max(queueMax, before.queued());
                completionMax = Math.max(completionMax, before.awaitingPublication());
                bounds(before);
                long bytes = THREADS.getThreadAllocatedBytes(uiThread), frameStart = System.nanoTime();
                service.drain();
                service.prioritize(selected);
                var selectedResult = service.result(selected);
                var after = service.diagnostics();
                long elapsed = System.nanoTime() - frameStart;
                bytes = THREADS.getThreadAllocatedBytes(uiThread) - bytes;
                frames.add(elapsed);
                frameBytes += bytes;
                if (selectedNs < 0 && selectedResult.status() != DifficultyResult.Status.PENDING) selectedNs = System.nanoTime() - started;
                bounds(after);
                queueMax = Math.max(queueMax, after.queued());
                completionMax = Math.max(completionMax, after.awaitingPublication());
                peakHeap = Math.max(peakHeap, heap());
                require(after.libraryContents() == corpus.unique().size(), "Duplicates created extra jobs");
                if (after.published() == corpus.unique().size()) break;
                // Skip missed deadlines rather than issuing several artificial catch-up frames.
                nextFrame = frameStart + FRAME_NS;
                if (nextFrame < System.nanoTime()) nextFrame = System.nanoTime() + FRAME_NS;
                if (!paced) Thread.yield();
            }
            long publicationNs = System.nanoTime() - started;
            long collections = gc(false) - gcCount, collectionMs = gc(true) - gcMs;
            var results = new ArrayList<DifficultyResult>();
            var status = new EnumMap<DifficultyResult.Status, Integer>(DifficultyResult.Status.class);
            for (var chart : corpus.unique()) {
                var result = service.result(chart);
                require(result.status() == DifficultyResult.Status.SUCCESS, "Baseline source rejected: " + chart.title() + ": " + result.reason());
                results.add(result);
                status.merge(result.status(), 1, Integer::sum);
            }
            for (int i = 0; i < corpus.copies().size(); i++)
                require(service.result(corpus.copies().get(i)).equals(results.get(i)), "Content duplicate did not share its result");
            require(selectedNs >= 0 && service.diagnostics().storageWarning().isEmpty(), "Selected result missing or cache storage failed");
            long expectedCalls = phase.equals("reopened-warm") ? 0 : corpus.unique().size();
            require(measured.calls.get() == expectedCalls, "Unexpected calculator count in " + phase + ": " + measured.calls.get());
            require(measured.uiCalls.get() == 0, "Calculator ran on caller thread");
            if (report) {
                frames.sort(Long::compare);
                System.out.printf(Locale.ROOT, "%s,%d,%d,%d,%.3f,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%d,%d,%d,%.3f,%d,%d,%d,%d,%d,%d,%d%n",
                        phase, corpus.unique().size(), corpus.copies().size(), frames.size(), registrationNs / 1e6, registrationBytes,
                        publicationNs / 1e6, selectedNs / 1e6, frames.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e3,
                        percentile(frames, .95) / 1e3, percentile(frames, .99) / 1e3, frames.getLast() / 1e3, frameBytes / (double) frames.size(),
                        queueMax, completionMax, measured.calls.get(), measured.cpuNs / 1e6, measured.bytes,
                        status.getOrDefault(DifficultyResult.Status.SUCCESS, 0), status.getOrDefault(DifficultyResult.Status.UNSUPPORTED, 0),
                        status.getOrDefault(DifficultyResult.Status.FAILED, 0), peakHeap, collections, collectionMs);
            }
            return List.copyOf(results);
        } finally {
            service.close();
            if (worker != null) {
                worker.join(5_000);
                require(!worker.isAlive(), "Worker did not terminate after close");
            }
        }
    }

    private static final class MeasuredCalculator implements Function<BeatmapDifficulty, DifficultyResult> {
        final StandardDifficultyCalculator delegate = new StandardDifficultyCalculator();
        final AtomicLong calls = new AtomicLong(), uiCalls = new AtomicLong();
        final long caller = Thread.currentThread().threadId();
        long cpuNs, bytes;
        @Override public DifficultyResult apply(BeatmapDifficulty chart) {
            long thread = Thread.currentThread().threadId();
            if (thread == caller) uiCalls.incrementAndGet();
            long cpu = THREADS.getCurrentThreadCpuTime(), allocation = THREADS.getThreadAllocatedBytes(thread);
            try { return delegate.calculate(chart); }
            finally {
                cpuNs += THREADS.getCurrentThreadCpuTime() - cpu;
                bytes += THREADS.getThreadAllocatedBytes(thread) - allocation;
                calls.incrementAndGet();
            }
        }
    }

    private static void bounds(LocalDifficultyService.Diagnostics diagnostics) {
        require(diagnostics.queued() <= LocalDifficultyService.QUEUE_LIMIT, "Queue limit exceeded");
        require(diagnostics.awaitingPublication() <= LocalDifficultyService.COMPLETION_LIMIT, "Completion limit exceeded");
    }
    private static void waitUntil(long target) throws InterruptedException {
        long remaining;
        while ((remaining = target - System.nanoTime()) > 0) {
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) throw new InterruptedException("Probe interrupted");
        }
    }
    private static long percentile(List<Long> sorted, double proportion) { return sorted.get((int) Math.ceil(sorted.size() * proportion) - 1); }
    private static long heap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    private static long gc(boolean time) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> time ? bean.getCollectionTime() : bean.getCollectionCount()).sum();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
