package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BeatmapLibrary {
    private final Map<String, BeatmapSet> sets = new LinkedHashMap<>();

    public synchronized void add(BeatmapSet beatmapSet) {
        Objects.requireNonNull(beatmapSet, "beatmapSet");
        sets.put(beatmapSet.id(), beatmapSet);
    }

    public synchronized List<BeatmapSet> all() {
        return List.copyOf(new ArrayList<>(sets.values()));
    }

    public synchronized int size() {
        return sets.size();
    }
}
