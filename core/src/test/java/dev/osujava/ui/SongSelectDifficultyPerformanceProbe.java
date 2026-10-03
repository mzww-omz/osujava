package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.difficulty.*;
import dev.osujava.library.*;
import dev.osujava.ui.theme.UiLayout;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;

/** Opt-in production update CPU probe with hashed synthetic sources and actual local ratings.
 * No GL, audio, artwork, archive import, real-library workload or GPU/FPS claims.
 * Cold completion/classification is measured before settled samples; waits are excluded.
 */
public final class SongSelectDifficultyPerformanceProbe {
    private static final List<String> FIXTURES = List.of("jumps", "rhythm", "linear-basic", "linear-repeat",
            "curve-bezier", "curve-perfect", "curve-catmull", "curve-mixed");
    private static final UiLayout LAYOUT = UiLayout.fromPixels(1280, 720);
    private static final long FRAME_NS = 1_000_000_000L / 60;
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final Method UPDATE = method("update", UiLayout.class, float.class);
    private static final Field BROWSER = field(SongSelectScreen.class, "browser");
    private static final Field KNOWN = field(SongBrowserModel.class, "knownRatings");
    private static final Field WORKER = field(LocalDifficultyService.class, "worker");

    public static void main(String[] args) throws Exception {
        int count = Integer.getInteger("osujava.difficultyUiProbeCount", 10_000);
        int seconds = Integer.getInteger("osujava.difficultyUiProbeTimeoutSeconds", 120);
        int settled = Integer.getInteger("osujava.difficultyUiProbeSamples", 300);
        require(count >= 32 && count <= 100_000 && count % 4 == 0, "Count must be a multiple of four between 32 and 100000");
        require(seconds >= 1 && seconds <= 600 && settled >= 1 && settled <= 3600, "Invalid timeout/sample count");
        require(THREADS.isThreadAllocatedMemorySupported(), "Thread allocation counters unavailable");
        THREADS.setThreadAllocatedMemoryEnabled(true);
        Gdx.input = stub(Input.class);
        Gdx.graphics = stub(Graphics.class);
        var sources = sources();
        // A distinct small corpus warms update/layout/reflection; target cache starts empty.
        run("jit", sets(sources, 32, "JIT"), sources, null, seconds, 40, false);
        var sets = sets(sources, count, "Measured 夜空");
        Path cache = Files.createTempDirectory("osujava-difficulty-ui-probe-");
        try {
            System.out.println("# Hashed synthetic sources, four difficulties/set; actual SongSelectScreen.update and local worker.");
            System.out.println("# No GPU/artwork/audio; rapid import is in-memory library replacement, excluding archive/parser/storage IO.");
            System.out.println("scenario,window,unique_contents,frames,mean_us,p95_us,p99_us,max_us,bytes_mean,classification_rebuilds,queue_max_sampled,completion_max_sampled,generation_delta,selection_events,import_events,max_selection_frame_ms,max_import_frame_ms");
            for (String scenario : List.of("title", "difficulty-sort", "difficulty-group", "stars-search", "rapid-selection-import"))
                run(scenario, sets, sources, cache.resolve(scenario), seconds, settled, true);
            budgetMeasurements(sources);
        } finally {
            try (var paths = Files.walk(cache)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void run(String scenario, List<BeatmapSet> sets, List<String> sources, Path cache,
                            int seconds, int settled, boolean report) throws Exception {
        var library = new BeatmapLibrary(new BeatmapLibraryStorage() {
            @Override public List<BeatmapSet> load() { return sets; }
            @Override public void save(BeatmapSet set) { /* Isolated in-memory replacement, no import IO. */ }
        });
        var service = new LocalDifficultyService(cache);
        var game = new OsuJavaGame(null, null) {
            @Override public BeatmapLibrary library() { return library; }
            @Override public LocalDifficultyService createLocalDifficultyService() { return service; }
        };
        SongSelectScreen screen = null;
        Thread worker = null;
        try {
            screen = new SongSelectScreen(game, sets.get(sets.size() / 2).id(), 1);
            worker = (Thread) WORKER.get(service);
            screen.show(); screen.resize(1280, 720);
            if (scenario.equals("difficulty-sort")) screen.browserMode(SongBrowserModel.Sort.DIFFICULTY, SongBrowserModel.Group.NONE);
            if (scenario.equals("difficulty-group")) screen.browserMode(SongBrowserModel.Sort.TITLE, SongBrowserModel.Group.DIFFICULTY);
            if (scenario.equals("stars-search")) screen.browserSearch("stars>=0", false);
            var browser = (SongBrowserModel) BROWSER.get(screen);
            Object known = KNOWN.get(browser);
            long initialGeneration = service.diagnostics().generation(), deadline = System.nanoTime() + seconds * 1_000_000_000L;
            long nextFrame = System.nanoTime();
            int expected = sets.size() * 4, settledFrames = 0, frame = 0;
            var completing = new Samples();
            var ready = new Samples();
            while (settledFrames < settled || service.diagnostics().published() != expected
                    || scenario.equals("rapid-selection-import") && frame <= 16) {
                waitUntil(nextFrame);
                require(System.nanoTime() < deadline, "Timeout in " + scenario + ": " + service.diagnostics());
                var before = service.diagnostics();
                bounds(before);
                boolean importEvent = scenario.equals("rapid-selection-import") && (frame == 8 || frame == 16);
                boolean completed = before.published() == expected && !importEvent;
                var sample = completed ? ready : completing;
                sample.queue = Math.max(sample.queue, before.queued());
                sample.completions = Math.max(sample.completions, before.awaitingPublication());
                boolean selectionEvent = scenario.equals("rapid-selection-import") && frame % 6 == 0;
                // Parsing/replacement fixture creation is outside frame timing, like worker import.
                var replacement = importEvent ? replacement(sources, "Revision " + frame) : null;
                long bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
                if (selectionEvent) screen.previewSelection((frame / 6) % sets.size(), (frame / 6) % 4);
                if (importEvent) { library.add(replacement); settledFrames = 0; }
                UPDATE.invoke(screen, LAYOUT, 1f / 60);
                long elapsed = System.nanoTime() - start;
                bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
                sample.record(elapsed, bytes, selectionEvent, importEvent);
                Object current = KNOWN.get(browser);
                if (current != known) { sample.rebuilds++; known = current; }
                var after = service.diagnostics();
                bounds(after);
                require(after.libraryContents() == expected, "Unexpected unique content count");
                require(after.storageWarning().isEmpty(), "Cache storage failure: " + after.storageWarning());
                if (completed) settledFrames++;
                frame++;
                nextFrame = start + FRAME_NS;
                if (nextFrame < System.nanoTime()) nextFrame = System.nanoTime() + FRAME_NS;
            }
            require(service.diagnostics().published() == expected, "Incomplete publication");
            for (var set : library.all()) for (var chart : set.difficulties())
                require(service.result(chart).status() == DifficultyResult.Status.SUCCESS, "Synthetic baseline chart did not receive a valid rating");
            long generations = service.diagnostics().generation() - initialGeneration;
            if (scenario.equals("rapid-selection-import")) {
                require(generations == 2, "Library replacements did not advance two generations");
                for (var old : sets.getFirst().difficulties())
                    require(service.result(old).status() == DifficultyResult.Status.PENDING, "Removed source still has an active rating job");
            }
            if (scenario.equals("stars-search")) require(browser.visibleSets().size() == sets.size(), "Known nonnegative-star search omitted valid sources");
            if (report) {
                completing.print(scenario, "cold-completion", expected, generations);
                ready.print(scenario, "settled", expected, generations);
            }
        } finally {
            if (screen != null) screen.dispose(); else service.close();
            if (worker != null) { worker.join(5_000); require(!worker.isAlive(), "Worker did not stop after screen disposal"); }
        }
    }

    private static List<String> sources() throws IOException {
        var values = new ArrayList<String>();
        for (String fixture : FIXTURES) {
            try (var in = SongSelectDifficultyPerformanceProbe.class.getResourceAsStream("/difficulty/reference-20220902/" + fixture + ".osu")) {
                if (in == null) throw new IOException("Missing source fixture " + fixture);
                values.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return List.copyOf(values);
    }
    private static List<BeatmapSet> sets(List<String> sources, int count, String prefix) throws Exception {
        var values = new ArrayList<BeatmapSet>();
        var keys = new HashSet<BeatmapContentKey>();
        for (int set = 0; set < count / 4; set++) {
            var difficulties = new ArrayList<BeatmapDifficulty>();
            for (int diff = 0; diff < 4; diff++) {
                int index = set * 4 + diff;
                var chart = chart(sources.get(index % sources.size()), prefix + " " + index);
                require(keys.add(BeatmapContentKey.of(chart)), "Sources did not receive unique hashes");
                difficulties.add(chart);
            }
            values.add(new BeatmapSet("set-" + set, prefix + " " + set, "Synthetic artist", "Local probe", null, null, difficulties, List.of()));
        }
        return List.copyOf(values);
    }
    private static BeatmapSet replacement(List<String> sources, String title) throws Exception {
        var values = new ArrayList<BeatmapDifficulty>();
        for (int i = 0; i < 4; i++) values.add(chart(sources.get(i), title + " " + i));
        return new BeatmapSet("set-0", title, "Synthetic artist", "Local probe", null, null, values, List.of());
    }
    private static BeatmapDifficulty chart(String source, String title) throws Exception {
        var chart = new BeatmapFileParser().parse(source.replaceFirst("(?m)^Title:[^\\r\\n]*", title), "probe.osu").difficulty();
        // Actual imported files have distinct paths; metadata-only fallback identities can
        // collide when several reference fixtures share a Version/Creator inside one Set.
        return chart.withAssets(null, null, Path.of("synthetic-" + chart.playData().sha256() + ".osu"));
    }
    private static final class Samples {
        final List<Long> nanos = new ArrayList<>();
        long bytes, selectionMax, importMax;
        int queue, completions, rebuilds, selections, imports;
        void record(long time, long allocation, boolean selection, boolean imported) {
            nanos.add(time); bytes += allocation;
            if (selection) { selections++; selectionMax = Math.max(selectionMax, time); }
            if (imported) { imports++; importMax = Math.max(importMax, time); }
        }
        void print(String scenario, String window, int count, long generations) {
            require(!nanos.isEmpty(), "No samples in " + scenario + "/" + window);
            nanos.sort(Long::compare);
            System.out.printf(Locale.ROOT, "%s,%s,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f,%d,%d,%d,%d,%d,%d,%.3f,%.3f%n",
                    scenario, window, count, nanos.size(), nanos.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e3,
                    percentile(.95) / 1e3, percentile(.99) / 1e3, nanos.getLast() / 1e3, bytes / (double) nanos.size(),
                    rebuilds, queue, completions, generations, selections, imports, selectionMax / 1e6, importMax / 1e6);
        }
        long percentile(double fraction) { return nanos.get((int) Math.ceil(nanos.size() * fraction) - 1); }
    }

    private static void budgetMeasurements(List<String> sources) throws Exception {
        var parser = new BeatmapFileParser();
        String header = "osu file format v14\n[Difficulty]\nSliderMultiplier:1.4\nSliderTickRate:1\n[TimingPoints]\n0,500\n[HitObjects]\n";
        var cases = new LinkedHashMap<String, BeatmapDifficulty>();
        for (int count : new int[]{200, 400}) {
            var text = new StringBuilder(header);
            for (int i = 0; i < count; i++) text.append("80,80,").append(1000 + i * 6000).append(",2,0,C|180:300|320:60|440:280,1,650\n");
            cases.put("catmull-" + count, parser.parse(text.toString(), "budget.osu").difficulty());
        }
        var text = new StringBuilder(header);
        for (int i = 0; i < 2002; i++) text.append(i * 5).append(",100,1000,1,0\n");
        cases.put("stack-comparison-limit", parser.parse(text.toString(), "stack.osu").difficulty());
        String nested = sources.get(2).replace("L|456:192,1,200", "L|456:192,500,10000").replace("SliderTickRate:1", "SliderTickRate:8");
        cases.put("nested-work-limit", parser.parse(nested, "nested.osu").difficulty());
        var calculator = new StandardDifficultyCalculator();
        var valid = chart(sources.getFirst(), "Valid after limit");
        System.out.println("# calculator-budget-case,status,reason,wall_ms,allocated_bytes (single synthetic case; not a throughput benchmark)");
        for (var entry : cases.entrySet()) {
            long bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()), started = System.nanoTime();
            var result = calculator.calculate(entry.getValue());
            long elapsed = System.nanoTime() - started;
            bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
            require(result.status() == (entry.getKey().equals("catmull-200") ? DifficultyResult.Status.SUCCESS : DifficultyResult.Status.UNSUPPORTED), "Unexpected budget outcome: " + entry.getKey() + ": " + result);
            if (result.status() == DifficultyResult.Status.UNSUPPORTED) require(result.reason().contains("work limit"), "Wrong budget rejection reason");
            require(calculator.calculate(valid).status() == DifficultyResult.Status.SUCCESS, "Budget rejection poisoned next calculation");
            System.out.printf(Locale.ROOT, "# %s,%s,%s,%.3f,%d%n", entry.getKey(), result.status(), result.reason(), elapsed / 1e6, bytes);
        }
    }
    private static void bounds(LocalDifficultyService.Diagnostics d) {
        require(d.queued() <= LocalDifficultyService.QUEUE_LIMIT && d.awaitingPublication() <= LocalDifficultyService.COMPLETION_LIMIT, "Worker bounds exceeded");
    }
    private static void waitUntil(long target) throws InterruptedException {
        long remaining;
        while ((remaining = target - System.nanoTime()) > 0) { LockSupport.parkNanos(remaining); if (Thread.interrupted()) throw new InterruptedException(); }
    }
    private static Method method(String name, Class<?>... parameters) {
        try { var value = SongSelectScreen.class.getDeclaredMethod(name, parameters); value.setAccessible(true); return value; }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static Field field(Class<?> type, String name) {
        try { var value = type.getDeclaredField(name); value.setAccessible(true); return value; }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("getWidth")) return 1280;
            if (method.getName().equals("getHeight")) return 720;
            if (method.getName().equals("getX")) return 640;
            if (method.getName().equals("getY")) return 360;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0f;
            return null;
        }));
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
