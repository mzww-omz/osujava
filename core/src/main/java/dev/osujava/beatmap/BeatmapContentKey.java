package dev.osujava.beatmap;

/** Exact source content, independently of its local location or display metadata. */
public record BeatmapContentKey(String sha256, int mode) {
    public BeatmapContentKey {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}") || mode < 0)
            throw new IllegalArgumentException("Invalid beatmap content key");
    }

    public static BeatmapContentKey of(BeatmapDifficulty difficulty) {
        return difficulty == null || difficulty.playData().sha256().isEmpty() || difficulty.mode() < 0
                ? null : new BeatmapContentKey(difficulty.playData().sha256(), difficulty.mode());
    }
}
