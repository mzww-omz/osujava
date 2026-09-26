package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.TimingPoint;

import java.util.ArrayList;
import java.util.List;

/** Resolves standard osu!sample basenames from hit flags and the active timing point. */
public final class OsuHitsoundSamples {
    private OsuHitsoundSamples() {
    }

    public static List<String> hit(int hitSound, TimingPoint timing) {
        String bank = bank(timing);
        String index = index(timing);
        List<String> samples = new ArrayList<>();
        samples.add(bank + "-hitnormal" + index);
        if ((hitSound & 2) != 0) samples.add(bank + "-hitwhistle" + index);
        if ((hitSound & 4) != 0) samples.add(bank + "-hitfinish" + index);
        if ((hitSound & 8) != 0) samples.add(bank + "-hitclap" + index);
        return List.copyOf(samples);
    }

    public static List<String> sliderTick(TimingPoint timing) {
        return List.of(bank(timing) + "-slidertick" + index(timing));
    }

    public static List<String> spinnerSpin(TimingPoint timing, boolean bonus) {
        return List.of(bonus ? "spinnerbonus" : "spinnerspin");
    }

    public static float volume(TimingPoint timing) {
        return timing == null ? 1 : Math.max(0, Math.min(1, timing.volume() / 100f));
    }

    private static String bank(TimingPoint timing) {
        if (timing == null) return "normal";
        return switch (timing.sampleSet()) {
            case 2 -> "soft";
            case 3 -> "drum";
            default -> "normal";
        };
    }

    private static String index(TimingPoint timing) {
        return timing != null && timing.sampleIndex() > 1 ? Integer.toString(timing.sampleIndex()) : "";
    }
}
