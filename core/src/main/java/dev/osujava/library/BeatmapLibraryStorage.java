package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.BeatmapContentKey;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface BeatmapLibraryStorage {
    List<BeatmapSet> load() throws IOException;

    void save(BeatmapSet beatmapSet) throws IOException;
    default Map<BeatmapContentKey,Long> addedAt() { return Map.of(); }
    default void save(BeatmapSet beatmapSet, Map<BeatmapContentKey,Long> addedAt) throws IOException { save(beatmapSet); }
}
