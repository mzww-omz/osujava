package dev.osujava.beatmap;

import java.util.regex.Pattern;

/** Exact source content, independently of its local location or display metadata. */
public record BeatmapContentKey(String sha256, int mode) {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public BeatmapContentKey {
        if (sha256 == null || !HASH.matcher(sha256).matches() || mode < 0)
            throw new IllegalArgumentException("Invalid beatmap content key");
    }

    public static BeatmapContentKey of(BeatmapDifficulty difficulty) {
        return difficulty == null || difficulty.playData().sha256().isEmpty() || difficulty.mode() < 0
                ? null : new BeatmapContentKey(difficulty.playData().sha256(), difficulty.mode());
    }
}
