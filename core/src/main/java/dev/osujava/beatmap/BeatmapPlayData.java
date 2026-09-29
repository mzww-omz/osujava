package dev.osujava.beatmap;

import java.util.List;

/** Source facts needed by scoring and replay, preserved independently of resolved asset paths. */
public record BeatmapPlayData(List<BreakPeriod> breaks, int audioLeadInMs, String sha256, String md5) {
    public static final BeatmapPlayData UNKNOWN = new BeatmapPlayData(List.of(), 0, "", "");

    public BeatmapPlayData {
        breaks = List.copyOf(breaks);
        if (audioLeadInMs < 0) throw new IllegalArgumentException("AudioLeadIn cannot be negative");
        if (sha256 == null || md5 == null || !sha256.matches("(?:[0-9a-f]{64})?")
                || !md5.matches("(?:[0-9a-f]{32})?")) throw new IllegalArgumentException("Invalid beatmap digest");
    }
}
