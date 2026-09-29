package dev.osujava.lwjgl3;

import dev.osujava.ui.MenuBeatTiming;
import java.nio.file.Path;
import java.util.List;

/** Opt-in check with locally generated tone.mp3/.ogg/.wav; no OpenAL device is needed. */
public final class MenuAnalysisSmoke {
    public static void main(String[] args) throws Exception {
        try (var analysis = new DesktopMenuAudioAnalysis()) {
            for (String extension : List.of("wav", "mp3", "ogg")) {
                analysis.select(Path.of(args[0], "tone." + extension));
                for (double position : new double[]{500, 1500, 3500, 500}) {
                    long deadline = System.nanoTime() + 5_000_000_000L;
                    do {
                        analysis.sample(position, MenuBeatTiming.at(List.of(), position, true));
                        if (analysis.available() && analysis.maximumAmplitude() > .5f) break;
                        Thread.sleep(5);
                    } while (System.nanoTime() < deadline);
                    if (!analysis.available() || analysis.maximumAmplitude() <= .5f)
                        throw new AssertionError(extension + " failed at " + position);
                    float[] bins = analysis.frequencyAmplitudes(); int peak = 0;
                    for (int i = 1; i < bins.length; i++) if (bins[i] > bins[peak]) peak = i;
                    if (peak != 32 || bins[32] < .5f) throw new AssertionError(extension + " spectrum differs: " + peak);
                    if (analysis.bufferedFrames() > 400) throw new AssertionError("Unbounded cache");
                }
                System.out.println(extension + ": PCM levels, FFT bin 32, playback lookup, backward loop passed");
            }
        }
    }
}
