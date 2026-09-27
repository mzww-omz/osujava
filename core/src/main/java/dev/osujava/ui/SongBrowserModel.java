package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.text.Normalizer;
import java.util.*;
import java.util.random.RandomGenerator;

/** GL-free browser. Rebuilds only on state changes; identities never use display indices. */
final class SongBrowserModel {
    enum Sort {
        TITLE("Title"), ARTIST("Artist"), CREATOR("Creator"), BPM("BPM"), LENGTH("Length");
        final String label;
        Sort(String label) { this.label = label; }
    }
    enum Group {
        NONE("No Grouping"), ARTIST("Artist"), CREATOR("Creator"), BPM("BPM"), LENGTH("Length");
        final String label;
        Group(String label) { this.label = label; }
    }
    enum Kind { GROUP_HEADER, SET, DIFFICULTY }
    record Selection(String setId, String difficultyId) { }
    record Entry(Kind kind, String key, String label, BeatmapSet set, BeatmapDifficulty difficulty) { }
    private record Indexed(BeatmapSet set, List<String> fields, double bpm, double length) { }
    private record Bucket(int order, String label) { }
    /** Token processing is separate from metadata indexing, allowing future field predicates. */
    private record Query(List<String> tokens) {
        static Query parse(String raw) {
            String normalized = normalize(raw).strip();
            return new Query(normalized.isEmpty() ? List.of() : List.of(normalized.split("(?U)\\s+")));
        }
        boolean matches(Indexed item) {
            return tokens.stream().allMatch(token -> item.fields.stream().anyMatch(field -> field.contains(token)));
        }
    }
    static final int HISTORY_LIMIT = 64;
    private List<Indexed> library = List.of();
    private List<BeatmapSet> visible = List.of();
    private Map<String, BeatmapSet> visibleById = Map.of();
    private List<BeatmapSet> snapshot = List.of();
    private List<Entry> entries = List.of();
    private Selection selection, beforeSearch;
    private String search = "";
    private Sort sort = Sort.TITLE;
    private Group group = Group.NONE;
    private final Deque<Selection> history = new ArrayDeque<>();
    private final RandomGenerator random;

    SongBrowserModel(List<BeatmapSet> sets) { this(sets, RandomGenerator.getDefault()); }
    SongBrowserModel(List<BeatmapSet> sets, RandomGenerator random) { this.random = random; library(sets); }
    void library(List<BeatmapSet> sets) {
        library = sets.stream().map(set -> {
            List<String> fields = new ArrayList<>(List.of(normalize(set.title()), normalize(set.artist()), normalize(set.creator())));
            for (var d : set.difficulties()) {
                fields.add(normalize(d.title())); fields.add(normalize(d.artist()));
                fields.add(normalize(d.creator())); fields.add(normalize(d.version()));
            }
            double bpm = set.difficulties().stream().flatMap(d -> d.timingPoints().stream())
                    .filter(p -> p.uninherited() && p.beatLength() > 0 && Double.isFinite(p.beatLength()))
                    .mapToDouble(p -> 60000 / p.beatLength()).filter(Double::isFinite).max().orElse(Double.NaN);
            double length = set.difficulties().stream().filter(d -> !d.hitObjects().isEmpty())
                    .mapToDouble(SongBrowserModel::objectSpan).filter(Double::isFinite).max().orElse(Double.NaN);
            return new Indexed(set, List.copyOf(fields), bpm, length);
        }).toList();
        snapshot = sets.stream().sorted(Comparator.comparing((BeatmapSet s) -> normalize(s.title()))
                .thenComparing(s -> normalize(s.artist())).thenComparing(s -> normalize(s.creator())).thenComparing(BeatmapSet::id)).toList();
        rebuild();
    }
    List<BeatmapSet> librarySets() { return snapshot; }
    private static double objectSpan(BeatmapDifficulty d) {
        double first = d.hitObjects().stream().mapToDouble(HitObject::timeMs).filter(Double::isFinite).min().orElse(Double.NaN);
        double last = d.hitObjects().stream().mapToDouble(HitObject::endTimeMs).filter(Double::isFinite).max().orElse(Double.NaN);
        return Math.max(0, last - first);
    }
    List<BeatmapSet> visibleSets() { return visible; }
    List<Entry> entries() { return entries; }
    String search() { return search; }
    Sort sort() { return sort; }
    Group group() { return group; }
    Selection selection() { return selection; }
    BeatmapSet selectedSet() { return find(selection); }
    BeatmapDifficulty selectedDifficulty() {
        var set = selectedSet();
        return set == null ? null : set.difficulties().stream()
                .filter(d -> difficultyId(d).equals(selection.difficultyId())).findFirst().orElse(set.difficulties().getFirst());
    }
    void sort(Sort next) { if (sort != next) { sort = Objects.requireNonNull(next); rebuild(); } }
    void group(Group next) { if (group != next) { group = Objects.requireNonNull(next); rebuild(); } }
    void search(String next) {
        next = Objects.requireNonNullElse(next, "");
        if (search.equals(next)) return;
        if (search.isBlank() && !next.isBlank()) beforeSearch = selection;
        boolean clearing = !search.isBlank() && next.isBlank();
        search = next;
        if (clearing && beforeSearch != null) selection = beforeSearch;
        rebuild();
        if (clearing) beforeSearch = null;
    }
    void select(String setId, int difficultyIndex) {
        BeatmapSet set = visible.stream().filter(s -> s.id().equals(setId)).findFirst().orElse(null);
        if (set == null) return;
        var difficulty = set.difficulties().get(Math.max(0, Math.min(difficultyIndex, set.difficulties().size() - 1)));
        Selection next = new Selection(set.id(), difficultyId(difficulty));
        if (next.equals(selection)) return;
        boolean expansionChanged = selection == null || !selection.setId().equals(setId);
        selection = next;
        if (expansionChanged) expand();
    }
    void random() {
        if (visible.isEmpty()) return;
        List<BeatmapSet> candidates = visible.stream().filter(s -> selection == null || !s.id().equals(selection.setId())).toList();
        if (candidates.isEmpty()) return;
        if (selection != null && !selection.equals(history.peekLast())) history.addLast(selection);
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
        select(candidates.get(random.nextInt(candidates.size())).id(), 0);
    }
    void previousRandom() {
        if (visible.isEmpty()) return;
        // Filtered-out identities remain available if the query is later cleared.
        var iterator = history.descendingIterator();
        while (iterator.hasNext()) {
            var previous = iterator.next();
            if (previous.equals(selection)) { iterator.remove(); continue; }
            var set = find(previous);
            if (set == null) continue;
            var difficulty = set.difficulties().stream().filter(d -> difficultyId(d).equals(previous.difficultyId()))
                    .findFirst().orElse(set.difficulties().getFirst());
            iterator.remove(); selection = new Selection(set.id(), difficultyId(difficulty)); expand(); return;
        }
    }
    int historySize() { return history.size(); }
    private BeatmapSet find(Selection selected) {
        return selected == null ? null : visibleById.get(selected.setId());
    }
    private void rebuild() {
        Query query = Query.parse(search);
        Comparator<Indexed> secondary = Comparator.comparing((Indexed i) -> i.fields.get(0))
                .thenComparing(i -> i.fields.get(1)).thenComparing(i -> i.fields.get(2)).thenComparing(i -> i.set.id());
        Comparator<Indexed> primary = switch (sort) {
            case TITLE -> secondary;
            case ARTIST -> Comparator.comparing(i -> i.fields.get(1));
            case CREATOR -> Comparator.comparing(i -> i.fields.get(2));
            case BPM -> Comparator.comparingDouble(i -> i.bpm);
            case LENGTH -> Comparator.comparingDouble(i -> i.length);
        };
        Comparator<Indexed> ordering = primary.thenComparing(secondary);
        if (group != Group.NONE) ordering = Comparator.comparing((Indexed i) -> bucket(i).order())
                .thenComparing(i -> bucket(i).label()).thenComparing(ordering);
        visible = library.stream().filter(query::matches).sorted(ordering).map(Indexed::set).toList();
        Map<String, BeatmapSet> nextById = new HashMap<>();
        for (var item : visible) nextById.put(item.id(), item);
        visibleById = Map.copyOf(nextById);
        var set = find(selection);
        if (set == null && !visible.isEmpty()) {
            set = visible.getFirst(); selection = new Selection(set.id(), difficultyId(set.difficulties().getFirst()));
        } else if (set != null && set.difficulties().stream().noneMatch(d -> difficultyId(d).equals(selection.difficultyId()))) {
            selection = new Selection(set.id(), difficultyId(set.difficulties().getFirst()));
        }
        // Retain identity through zero results, but expose no playable selection.
        expand();
    }
    private void expand() {
        Map<String, Indexed> byId = new HashMap<>();
        for (var item : library) byId.put(item.set.id(), item);
        List<Entry> result = new ArrayList<>();
        Bucket previous = null;
        for (var set : visible) {
            if (group != Group.NONE) {
                Bucket bucket = bucket(byId.get(set.id()));
                if (!bucket.equals(previous)) result.add(new Entry(Kind.GROUP_HEADER,
                        "group:" + group + ":" + bucket.label(), bucket.label(), null, null));
                previous = bucket;
            }
            if (selection != null && set.id().equals(selection.setId())) {
                for (var d : set.difficulties()) result.add(new Entry(Kind.DIFFICULTY,
                        set.id() + "#" + difficultyId(d), "", set, d));
            } else result.add(new Entry(Kind.SET, set.id() + "#set", "", set, null));
        }
        entries = List.copyOf(result);
    }
    private Bucket bucket(Indexed item) {
        return switch (group) {
            case NONE -> new Bucket(0, "");
            case ARTIST -> initial(item.set.artist());
            case CREATOR -> initial(item.set.creator());
            case BPM -> {
                if (Double.isNaN(item.bpm)) yield new Bucket(Integer.MAX_VALUE, "Unknown BPM");
                int band = (int)Math.min(6, item.bpm / 50);
                yield new Bucket(band, band == 6 ? "300+ BPM" : band * 50 + "–<" + (band + 1) * 50 + " BPM");
            }
            case LENGTH -> {
                if (Double.isNaN(item.length)) yield new Bucket(Integer.MAX_VALUE, "Unknown length");
                double minutes = item.length / 60000;
                int band = minutes < 2 ? 0 : minutes < 4 ? 1 : minutes < 6 ? 2 : minutes < 10 ? 3 : 4;
                yield new Bucket(band, new String[]{"Under 2 minutes", "2–<4 minutes", "4–<6 minutes", "6–<10 minutes", "10+ minutes"}[band]);
            }
        };
    }
    private static Bucket initial(String raw) {
        String value = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) return new Bucket(3, "Unknown");
        int codePoint = value.codePointAt(0);
        if (Character.isDigit(codePoint)) return new Bucket(0, "0–9");
        if (Character.isLetter(codePoint)) return new Bucket(1, new String(Character.toChars(codePoint)));
        return new Bucket(2, "Symbols");
    }
    static String difficultyId(BeatmapDifficulty d) {
        return d.beatmapPath() != null ? "path:" + d.beatmapPath().normalize()
                : "metadata:" + d.mode() + ":" + identityField(d.version()) + identityField(d.creator()) + identityField(d.audioFilename());
    }
    private static String identityField(String value) { return value.length() + ":" + value; }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
