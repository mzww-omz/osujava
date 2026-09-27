package dev.osujava.ui;

/** BPM/phase illustration, NOT PCM or FFT. Identical playback positions produce identical bins. */
public final class DeterministicMenuAudioFallback implements MenuAudioAnalysis {
    private final float[] bins = new float[MenuVisualiser.BARS];
    private float maximum;
    @Override public void sample(double playbackMs, MenuBeatTiming.Beat beat) {
        double envelope = Math.exp(-beat.phase() * 5);
        maximum = (float) (.06 + .18 * envelope);
        for (int i = 0; i < bins.length; i++) {
            double shape = .5 + .25 * Math.sin(i * .137 + playbackMs / 2900)
                    + .25 * Math.cos(i * .071 - playbackMs / 4100);
            bins[i] = (float) ((.015 + .13 * envelope) * shape * Math.exp(-i / 230.0));
        }
    }
    @Override public float maximumAmplitude() { return maximum; }
    @Override public float[] frequencyAmplitudes() { return bins; }
    @Override public boolean available() { return false; }
}
