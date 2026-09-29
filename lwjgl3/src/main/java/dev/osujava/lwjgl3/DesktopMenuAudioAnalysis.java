package dev.osujava.lwjgl3;

import dev.osujava.ui.MenuAudioAnalysis;
import dev.osujava.ui.MenuBeatTiming;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;

/** Cancellable streaming analysis, timestamped by PCM sample index and queried by Music position. */
final class DesktopMenuAudioAnalysis implements MenuAudioAnalysis {
    private record Frame(float peak, float[] bins) { }
    private static final Frame SILENT = new Frame(0, new float[1024]);
    private static final int MAX_FRAMES = 400;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "menu-audio-analysis"); t.setDaemon(true); return t;
    });
    private static final class Track {
        final Path path;
        final ConcurrentSkipListMap<Double, Frame> frames = new ConcurrentSkipListMap<>();
        volatile double requestedMs;
        volatile boolean cancelled;
        Track(Path path) { this.path = path; }
    }
    private Track track;
    private Future<?> job;
    private Frame current = SILENT;
    private boolean available, closed;
    @Override public void select(Path path) {
        cancel();
        if (closed || path == null) return;
        Track next = new Track(path); track = next;
        job = worker.submit(() -> decode(next));
    }
    private void cancel() {
        if (track != null) { track.cancelled = true; synchronized (track) { track.notifyAll(); } }
        if (job != null) job.cancel(true);
        track = null; job = null; current = SILENT; available = false;
    }
    @Override public void sample(double playbackMs, MenuBeatTiming.Beat beat) {
        if (track == null || !Double.isFinite(playbackMs)) return;
        double position = Math.max(0, playbackMs);
        if (position + 100 < track.requestedMs) select(track.path); // Music loop or backward seek.
        if (track == null) return;
        track.requestedMs = position;
        var entry = track.frames.floorEntry(position);
        available = entry != null && position - entry.getKey() < 100;
        current = available ? entry.getValue() : SILENT;
        track.frames.headMap(Math.max(0, position - 1000)).clear();
        synchronized (track) { track.notifyAll(); }
    }
    private void decode(Track target) {
        try (var decoder = MenuPcmDecoder.open(target.path)) {
            byte[] bytes = new byte[16384], block = new byte[16384];
            float[] samples = new float[MenuSpectrum.SIZE];
            float[] levels = new float[MenuSpectrum.SIZE];
            MenuSpectrum fft = new MenuSpectrum();
            int hop = Math.min(MenuSpectrum.SIZE, Math.max(1, decoder.sampleRate / 100));
            int filled = 0, carry = 0, frameBytes = decoder.channels * 2;
            long firstSample = 0;
            while (!target.cancelled && !Thread.currentThread().isInterrupted()) {
                // Read blocks are frame-aligned by the codecs. Retain a short tail defensively.
                int count = decoder.read(block);
                if (count <= 0) break;
                if (carry + count > bytes.length) bytes = Arrays.copyOf(bytes, carry + count);
                System.arraycopy(block, 0, bytes, carry, count); count += carry;
                int end = count - count % frameBytes;
                for (int i = 0; i < end && !target.cancelled; i += frameBytes) {
                    float mono = 0, level = 0;
                    for (int ch = 0; ch < decoder.channels; ch++) {
                        int p = i + ch * 2;
                        short pcm = decoder.bigEndian() ? (short) ((bytes[p] << 8) | (bytes[p + 1] & 255))
                                : (short) ((bytes[p + 1] << 8) | (bytes[p] & 255));
                        float value = pcm / 32768f; mono += value; level += Math.abs(value);
                    }
                    samples[filled] = mono / decoder.channels; levels[filled++] = level / decoder.channels;
                    if (filled < samples.length) continue;
                    double time = firstSample * 1000.0 / decoder.sampleRate;
                    synchronized (target) {
                        while (!target.cancelled && time > target.requestedMs + 2000) target.wait();
                    }
                    if (target.cancelled) return;
                    float peak = 0; for (float value : levels) peak = Math.max(peak, value);
                    target.frames.put(time, new Frame(peak, fft.transform(samples)));
                    while (target.frames.size() > MAX_FRAMES) target.frames.pollFirstEntry();
                    System.arraycopy(samples, hop, samples, 0, samples.length - hop);
                    System.arraycopy(levels, hop, levels, 0, levels.length - hop);
                    filled -= hop; firstSample += hop;
                }
                carry = count - end; System.arraycopy(bytes, end, bytes, 0, carry);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { /* Unreadable local audio disables analysis, never playback or navigation. */ }
    }
    @Override public float maximumAmplitude() { return current.peak; }
    @Override public float[] frequencyAmplitudes() { return current.bins; }
    @Override public boolean available() { return available; }
    int bufferedFrames() { return track == null ? 0 : track.frames.size(); }
    boolean awaitTermination() throws InterruptedException { return worker.awaitTermination(2, TimeUnit.SECONDS); }
    @Override public void close() { closed = true; cancel(); worker.shutdownNow(); }
}
