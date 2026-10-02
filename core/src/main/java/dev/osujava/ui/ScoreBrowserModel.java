package dev.osujava.ui;

import dev.osujava.score.*;
import dev.osujava.beatmap.BeatmapContentKey;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Difficulty-scoped selection/scrolling; no beatmap filtering or GL responsibilities. */
public final class ScoreBrowserModel {
    public record Row(LocalScore score, String value, String accuracy, String combo, String date, boolean verified, String provenance) {
        public Row(LocalScore score, String value, String accuracy, String combo, String date) {
            this(score,value,accuracy,combo,date,false,"");
        }
    }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private final LocalScoreStore store;
    private DifficultyIdentity target;
    private BeatmapContentKey content;
    private long revision = -1;
    private List<Row> rows = List.of();
    private record Target(DifficultyIdentity location, BeatmapContentKey content) { }
    private final Map<Target, List<Row>> cache = new HashMap<>();
    private UUID selected;
    private int first, capacity = 1;
    private float remainder;
    public ScoreBrowserModel(LocalScoreStore store) { this.store = store; }
    public void target(DifficultyIdentity next) {
        target(next, null);
    }
    public void target(DifficultyIdentity next, BeatmapContentKey nextContent) {
        boolean changed = !Objects.equals(next, target) || !Objects.equals(nextContent, content);
        if (!changed && revision == store.revision()) return;
        target = next;
        content = nextContent;
        if (revision != store.revision()) { cache.clear(); revision = store.revision(); }
        rows = next == null ? List.of() : cache.computeIfAbsent(new Target(next,nextContent), key -> {
            var result = new ArrayList<Row>();
            for (var s : store.query(key.content())) result.add(row(s,true));
            for (var s : store.legacy(key.location())) result.add(row(s,false));
            return List.copyOf(result);
        });
        if (changed) { first = 0; remainder = 0; selected = null; }
        if (selected != null && rows.stream().noneMatch(r -> r.score().playId().equals(selected))) selected = null;
        clamp();
    }
    public DifficultyIdentity target() { return target; }
    private static Row row(LocalScore s, boolean verified) {
        return new Row(s,String.format(Locale.ROOT,"%,d",s.result().score()),
                String.format(Locale.ROOT,"%.2f%%",s.result().accuracy()*100),s.result().maxCombo()+"x",
                DATE.format(Instant.ofEpochMilli(s.playedAt())) + (verified ? "" : " · content unverified"),verified,
                ScoreContextPresentation.of(s.context(),s.details()));
    }
    public List<Row> rows() { return rows; }
    public UUID selected() { return selected; }
    public int first() { return first; }
    public int capacity() { return capacity; }
    public void capacity(int count) { capacity = Math.max(1, count); clamp(); }
    public void first(int index) { first = index; remainder = 0; clamp(); }
    public void scroll(float amount) {
        if (!Float.isFinite(amount)) return;
        remainder += Math.max(-10000, Math.min(10000, amount));
        int steps = (int) remainder; remainder -= steps; first += steps; clamp();
    }
    public void select(int index) { if (index >= 0 && index < rows.size()) selected = rows.get(index).score().playId(); }
    public ResultsSnapshot open(int index) {
        if (index < 0 || index >= rows.size()) return null;
        select(index);
        return ResultsSnapshot.saved(rows.get(index).score());
    }
    private void clamp() { first = Math.max(0, Math.min(first, Math.max(0, rows.size() - capacity))); }
}
