package dev.osujava.beatmap;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record BeatmapSet(
        String id,
        String title,
        String artist,
        String creator,
        Path audioPath,
        Path backgroundPath,
        List<BeatmapDifficulty> difficulties,
        List<Path> assets) {

    public BeatmapSet {
        id = Objects.requireNonNull(id, "id");
        title = Objects.requireNonNullElse(title, "Unknown title");
        artist = Objects.requireNonNullElse(artist, "Unknown artist");
        creator = Objects.requireNonNullElse(creator, "Unknown creator");
        difficulties = List.copyOf(Objects.requireNonNullElse(difficulties, List.of()));
        assets = List.copyOf(Objects.requireNonNullElse(assets, List.of()));
        if (difficulties.isEmpty()) {
            throw new IllegalArgumentException("A beatmap set must contain at least one difficulty");
        }
    }
}
