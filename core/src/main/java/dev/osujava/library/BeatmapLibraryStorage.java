package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;

import java.io.IOException;
import java.util.List;

public interface BeatmapLibraryStorage {
    List<BeatmapSet> load() throws IOException;

    void save(BeatmapSet beatmapSet) throws IOException;
}
