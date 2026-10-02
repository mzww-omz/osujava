package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.score.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Revision-driven browser projection. Rendering never looks up score identities or storage.
 * Only scores for actual library difficulties mark a Set played; one completed difficulty suffices. */
final class SongSelectScoreSnapshot {
    private record Entry(boolean played, Map<BeatmapDifficulty, LocalScore> best) { }
    private final LocalScoreStore store;
    private final Map<BeatmapSet, Entry> entries = new IdentityHashMap<>();
    private List<BeatmapSet> library;
    private long revision = -1;
    SongSelectScoreSnapshot(LocalScoreStore store) { this.store = store; }
    boolean refresh(List<BeatmapSet> next) {
        if (library == next && revision == store.revision()) return false;
        entries.clear(); library = next; revision = store.revision();
        for (BeatmapSet set : next) {
            var best = new IdentityHashMap<BeatmapDifficulty,LocalScore>();
            for (var diff : set.difficulties()) {
                var identity = DifficultyIdentity.of(set.id(),diff);
                var score = identity == null ? null : store.best(BeatmapContentKey.of(diff));
                if (score != null) best.put(diff,score);
            }
            entries.put(set,new Entry(!best.isEmpty(),best));
        }
        return true;
    }
    boolean played(BeatmapSet set) { var e = entries.get(set); return e != null && e.played(); }
    boolean played(BeatmapSet set, BeatmapDifficulty diff) { return best(set,diff) != null; }
    LocalScore best(BeatmapSet set, BeatmapDifficulty diff) {
        var entry = entries.get(set); return entry == null ? null : entry.best().get(diff);
    }
}
