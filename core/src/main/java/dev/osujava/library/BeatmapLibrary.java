package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BeatmapLibrary {
    private final Map<String, BeatmapSet> sets = new LinkedHashMap<>();
    private final BeatmapLibraryStorage storage;
    private List<BeatmapSet> snapshot = List.of();
    private long revision;

    public BeatmapLibrary() {
        this(null);
    }

    public BeatmapLibrary(BeatmapLibraryStorage storage) {
        this.storage = storage;
        if (storage == null) return;
        try {
            for (BeatmapSet beatmapSet : storage.load()) {
                sets.put(beatmapSet.id(), beatmapSet);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not load local beatmap library: " + safeMessage(e));
        }
        snapshot = List.copyOf(sets.values());
    }

    public synchronized void add(BeatmapSet beatmapSet) {
        Objects.requireNonNull(beatmapSet, "beatmapSet");
        if (storage != null) {
            try {
                storage.save(beatmapSet);
            } catch (IOException e) {
                throw new LibraryStorageException("Could not save beatmap library entry", e);
            }
        }
        sets.put(beatmapSet.id(), beatmapSet);
        snapshot = List.copyOf(sets.values());
        revision++;
    }

    public synchronized List<BeatmapSet> all() {
        return snapshot;
    }

    public synchronized long revision() { return revision; }

    public synchronized int size() {
        return sets.size();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
