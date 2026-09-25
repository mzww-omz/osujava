package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BeatmapLibrary {
    private final Map<String, BeatmapSet> sets = new LinkedHashMap<>();
    private final BeatmapLibraryStorage storage;

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
    }

    public synchronized List<BeatmapSet> all() {
        return List.copyOf(new ArrayList<>(sets.values()));
    }

    public synchronized int size() {
        return sets.size();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
