package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.BeatmapContentKey;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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
    private final Clock clock;
    private Map<BeatmapContentKey,Long> addedAt = Map.of();
    private final Set<BeatmapContentKey> observed = new HashSet<>();

    public BeatmapLibrary() {
        this(null);
    }

    public BeatmapLibrary(BeatmapLibraryStorage storage) {
        this(storage,Clock.systemUTC());
    }
    public BeatmapLibrary(BeatmapLibraryStorage storage, Clock clock) {
        this.storage = storage;
        this.clock = Objects.requireNonNull(clock);
        if (storage == null) return;
        try {
            for (BeatmapSet beatmapSet : storage.load()) {
                sets.put(beatmapSet.id(), beatmapSet);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not load local beatmap library: " + safeMessage(e));
        }
        snapshot = List.copyOf(sets.values());
        addedAt = Map.copyOf(storage.addedAt());
        for (var set:snapshot) for(var diff:set.difficulties()) {
            var key=BeatmapContentKey.of(diff); if(key!=null) observed.add(key);
        }
    }

    public synchronized void add(BeatmapSet beatmapSet) {
        Objects.requireNonNull(beatmapSet, "beatmapSet");
        var dates = new HashMap<>(addedAt);
        long now = clock.millis();
        for(var diff:beatmapSet.difficulties()) {
            var key=BeatmapContentKey.of(diff);
            if(key!=null && !observed.contains(key) && now>=0) dates.putIfAbsent(key,now);
        }
        if (storage != null) {
            try {
                storage.save(beatmapSet,dates);
            } catch (IOException e) {
                throw new LibraryStorageException("Could not save beatmap library entry", e);
            }
        }
        sets.put(beatmapSet.id(), beatmapSet);
        addedAt = Map.copyOf(dates);
        for(var diff:beatmapSet.difficulties()) {
            var key=BeatmapContentKey.of(diff); if(key!=null) observed.add(key);
        }
        snapshot = List.copyOf(sets.values());
        revision++;
    }

    public synchronized List<BeatmapSet> all() {
        return snapshot;
    }

    public synchronized long revision() { return revision; }
    public synchronized Long addedAt(BeatmapContentKey content) { return content==null ? null : addedAt.get(content); }

    public synchronized int size() {
        return sets.size();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
