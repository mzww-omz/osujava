package dev.osujava.score;

import dev.osujava.beatmap.BeatmapDifficulty;
import java.util.Objects;

/** Same real-map identity as SongBrowserModel. Synthetic maps are never persisted. */
public record DifficultyIdentity(String setId, String osuPath) {
    public DifficultyIdentity {
        Objects.requireNonNull(setId); Objects.requireNonNull(osuPath);
        if (setId.isBlank() || osuPath.isBlank()) throw new IllegalArgumentException("Missing difficulty identity");
        osuPath = java.nio.file.Path.of(osuPath).normalize().toString();
    }
    public static DifficultyIdentity of(String setId, BeatmapDifficulty difficulty) {
        return difficulty == null || difficulty.beatmapPath() == null ? null
                : new DifficultyIdentity(setId, difficulty.beatmapPath().normalize().toString());
    }
}
