package dev.osujava.ui;

/** No decoded audio means no invented spectrum. The cookie still follows its separate beat clock. */
public final class DeterministicMenuAudioFallback implements MenuAudioAnalysis {
    private final float[] silence = new float[MenuVisualiser.BARS];
    @Override public float maximumAmplitude() { return .5f; }
    @Override public float[] frequencyAmplitudes() { return silence; }
    @Override public boolean available() { return false; }
}
