package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.ui.theme.UiLayout;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Opt-in CPU/allocation probe of the production update path; never part of timed unit tests.
 * No GL, audio, disk artwork, vsync or frame limiter. Selection event cost is included separately.
 */
public final class SongSelectPerformanceProbe {
    private static final int WARMUP = 1200, SAMPLES = 1200;
    private static final float DT = 1f / 60;
    private static final UiLayout LAYOUT = UiLayout.fromPixels(1280, 720);
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        Gdx.input = stub(Input.class);
        Gdx.graphics = stub(Graphics.class);
        System.out.println("sets,scenario,mean_us,p95_us,bytes_per_frame,gc_count,gc_ms");
        for (int count : new int[]{100, 1000, 10000}) {
            var library = new BeatmapLibrary();
            for (int i = 0; i < count; i++) {
                String title = "Local song " + String.format(Locale.ROOT, "%05d", i);
                var difficulties = new ArrayList<BeatmapDifficulty>();
                for (int d = 0; d < 4; d++) difficulties.add(new BeatmapDifficulty(title, "Artist 夜空", "Mapper",
                        "Difficulty " + d, 0, "", "", DifficultySettings.defaults(), List.of(), List.of(), null, null));
                library.add(new BeatmapSet("set" + i, title, "Artist 夜空", "Mapper", null, null, difficulties, List.of()));
            }
            var game = new OsuJavaGame(null, null) {
                @Override public BeatmapLibrary library() { return library; }
            };
            var screen = new SongSelectScreen(game, "set" + count / 2, 1);
            try {
                screen.show(); screen.resize(1280, 720);
                var update = method("update", UiLayout.class, float.class);
                var carouselField = SongSelectScreen.class.getDeclaredField("carousel");
                carouselField.setAccessible(true);
                var carousel = (SongSelectCarousel) carouselField.get(screen);
                for (String scenario : List.of("idle", "wheel", "fast-wheel", "selection")) {
                    screen.previewSelection(count / 2, 1);
                    Runnable event = new Runnable() {
                        int frame;
                        public void run() {
                            int f = frame++;
                            if (scenario.equals("wheel") && f % 6 == 0) carousel.wheel((f / 120 % 2 == 0) ? 1 : -1);
                            if (scenario.equals("fast-wheel")) carousel.wheel((f / 120 % 2 == 0) ? 4 : -4);
                            if (scenario.equals("selection") && f % 12 == 0) screen.previewSelection(count / 2 + f / 12 % 20, f / 12 % 4);
                        }
                    };
                    measure(count, scenario, () -> { event.run(); update.invoke(screen, LAYOUT, DT); });
                }
                // Isolate confirmed scan sites in a settled frame (not added to total update timing).
                for (int i = 0; i < 240; i++) update.invoke(screen, LAYOUT, DT);
                measure(count, "carousel", () -> carousel.advance(DT, null));
                var layoutRows = method("layoutRows", UiLayout.class, float.class, boolean.class);
                measure(count, "layoutRows", () -> layoutRows.invoke(screen, LAYOUT, 0f, false));
                for (String stage : List.of("advanceRowColours", "advanceRowStars", "advanceRowForeground", "prepareRowPresentations")) {
                    var m = method(stage, float.class);
                    measure(count, stage, () -> m.invoke(screen, DT));
                }
            } finally { screen.dispose(); }
        }
    }
    private interface Sample { void run() throws Exception; }
    private static void measure(int count, String name, Sample sample) throws Exception {
        for (int i = 0; i < WARMUP; i++) sample.run();
        long[] times = new long[SAMPLES];
        long gcCount = gc(false), gcTime = gc(true);
        long bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime(); sample.run(); times[i] = System.nanoTime() - start;
        }
        bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
        long collections = gc(false) - gcCount, milliseconds = gc(true) - gcTime;
        Arrays.sort(times);
        System.out.printf(Locale.ROOT, "%d,%s,%.3f,%.3f,%.1f,%d,%d%n", count, name,
                Arrays.stream(times).average().orElseThrow() / 1000, times[SAMPLES * 95 / 100] / 1000.0,
                bytes / (double) SAMPLES, collections, milliseconds);
    }
    private static long gc(boolean time) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(b -> time ? b.getCollectionTime() : b.getCollectionCount()).sum();
    }
    private static Method method(String name, Class<?>... types) throws Exception {
        var m = SongSelectScreen.class.getDeclaredMethod(name, types); m.setAccessible(true); return m;
    }
    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (p, m, a) -> {
            if (m.getName().equals("getWidth")) return 1280;
            if (m.getName().equals("getHeight")) return 720;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == int.class) return 0;
            if (m.getReturnType() == long.class) return 0L;
            return null;
        }));
    }
}
