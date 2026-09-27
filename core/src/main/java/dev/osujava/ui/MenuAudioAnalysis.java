package dev.osujava.ui;

/** Playback-position analysis seam. available() means real PCM analysis, never synthetic bins. */
public interface MenuAudioAnalysis extends AutoCloseable {
    default void sample(double playbackMs, MenuBeatTiming.Beat beat) { }
    float maximumAmplitude();
    float[] frequencyAmplitudes();
    boolean available();
    @Override default void close() { }
}
