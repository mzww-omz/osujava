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
    /** Stable row states, from 06003257/3268; singleton means one matching difficulty. */
    enum RowState { HIDDEN, COLLAPSED, SINGLETON, EXPANDED, SELECTED }
    static final class Row {
        final String key;
        BeatmapSet set;
        BeatmapDifficulty difficulty;
        Row parent, representative;
        String label = "";
        int matchingChildren;
        boolean excluded, expanded;
        RowState state = RowState.HIDDEN;
        Row(String key) { this.key = key; }
        boolean group() { return set == null; }
        boolean visible() { return state != RowState.HIDDEN; }
    }
    private final Map<String, Row> rowsByKey = new HashMap<>();
    private List<Row> rows = List.of();
    private String openGroupKey;
    // Last directly activated Group anchors scrolling without replacing the playable selection.
    private String groupTargetKey, focusKey;
    private boolean revealSelection = true;
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
    List<Row> rows() { return rows; }
    Row row(String key) { return rowsByKey.get(key); }
    String groupTargetKey() { return groupTargetKey; }
    String focusKey() { return focusKey; }
    String scrollTargetKey() {
        return focusKey != null ? focusKey : groupTargetKey != null ? groupTargetKey : selectedKey();
    }
    /** 06003250: when the selected beatmap's parent is closed, track the current Group. */
    String selectionTrackingKey() {
        Row selected = rowsByKey.get(selectedKey());
        if (selected != null && selected.parent != null && !selected.parent.expanded)
            return openGroupKey != null ? openGroupKey : groupTargetKey;
        return selectedKey();
    }
    String selectedKey() { return selectedSet() == null ? null : rowKey(selection.setId(), selection.difficultyId()); }
    static String rowKey(String setId, String difficultyId) {
        return "beatmap:" + identityField(setId) + identityField(difficultyId);
    }
    void toggleGroup(String key) {
        Row row = rowsByKey.get(key);
        if (row == null || !row.group() || row.excluded) return;
        openGroupKey = row.expanded ? null : key;
        groupTargetKey = key;
        expand();
    }
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
    void group(Group next) { if (group != next) { group = Objects.requireNonNull(next); revealSelection = true; groupTargetKey = null; focusKey = null; rebuild(); } }
    void search(String next) {
        next = Objects.requireNonNullElse(next, "");
        if (search.equals(next)) return;
        if (search.isBlank() && !next.isBlank()) beforeSearch = selection;
        boolean clearing = !search.isBlank() && next.isBlank();
        search = next;
        revealSelection = true; groupTargetKey = null; focusKey = null;
        if (clearing && beforeSearch != null) selection = beforeSearch;
        rebuild();
        if (clearing) beforeSearch = null;
    }
    void select(String setId, int difficultyIndex) {
        BeatmapSet set = visible.stream().filter(s -> s.id().equals(setId)).findFirst().orElse(null);
        if (set == null) return;
        var difficulty = set.difficulties().get(Math.max(0, Math.min(difficultyIndex, set.difficulties().size() - 1)));
        Selection next = new Selection(set.id(), difficultyId(difficulty));
        if (next.equals(selection) && groupTargetKey == null && focusKey == null) return;
        selection = next;
        if (!search.isBlank()) beforeSearch = next;
        revealSelection = true; groupTargetKey = null; focusKey = null;
        expand();
    }
    /** 06003247: Up/Down and Page use the same traversal and only differ in distance. */
    void moveDifficulty(int direction) { moveRows(Integer.signum(direction), true, false); }
    void movePage(int direction) { moveRows(10 * Integer.signum(direction), true, false); }

    /** Left/Right first confirm a focus; otherwise traverse outside the selected family. */
    void moveSet(int direction) {
        if (direction == 0 || confirmFocus()) return;
        moveRows(Integer.signum(direction), false, true);
    }

    /** Confirming a focus selects/expands it; callers must not start gameplay on this action. */
    boolean confirmFocus() {
        Row focused = rowsByKey.get(focusKey);
        if (focused == null) return false;
        activate(focused);
        return true;
    }

    /** Shift+Enter toggles the active/containing Group, independently of keyboard focus. */
    void toggleParentGroup() {
        Row parent = activeGroup();
        if (parent == null) return;
        focusKey = null;
        toggleGroup(parent.key);
    }

    /** Shift+Left/Right counts non-excluded Groups and wraps at most once (06003275). */
    void moveGroup(int direction) {
        Row origin = activeGroup();
        if (origin == null || direction == 0) return;
        focusKey = null;
        int start = rows.indexOf(origin), index = start;
        do {
            index = Math.floorMod(index + Integer.signum(direction), rows.size());
            Row candidate = rows.get(index);
            if (candidate.group() && !candidate.excluded) { toggleGroup(candidate.key); return; }
        } while (index != start);
    }

    private Row activeGroup() {
        Row active = rowsByKey.get(groupTargetKey != null ? groupTargetKey : openGroupKey);
        if (active != null) return active;
        Row selected = rowsByKey.get(selectedKey());
        return selected == null ? null : selected.parent;
    }

    /** 06003276 walks ALL rows. A full circuit dispatches the origin even if count < distance. */
    private void moveRows(int distance, boolean visibleOnly, boolean commit) {
        if (distance == 0 || rows.isEmpty()) return;
        Row selected = rowsByKey.get(selectedKey());
        Row origin = rowsByKey.get(focusKey != null ? focusKey : selectedKey());
        Row active = activeGroup();
        if (origin != null && !origin.visible() && active != null && active.expanded) origin = active;
        if (origin == null) return;
        int start = rows.indexOf(origin);
        if (start < 0) return;
        int index = start, count = 0;
        Row candidate;
        do {
            index = Math.floorMod(index + Integer.signum(distance), rows.size());
            candidate = rows.get(index);
            boolean eligible = !candidate.excluded && (visibleOnly ? candidate.visible()
                    : !candidate.group() && !sameFamily(candidate, selected));
            if (eligible) count++;
        } while (index != start && count < Math.abs(distance));
        if (commit) activate(candidate);
        else {
            focusKey = null;
            if (sameFamily(candidate, selected)) selectRow(candidate);
            else focusKey = candidate.key;
        }
    }

    private static boolean sameFamily(Row a, Row b) {
        return a != null && b != null && a.representative != null && a.representative == b.representative;
    }
    private void activate(Row row) {
        if (row.excluded) return;
        if (row.group()) toggleGroup(row.key);
        // 0600326a also chooses the nearest preferred star rating. Until a trusted rating
        // preference exists, retain the local first-difficulty fallback for Set activation.
        else selectRow(row.representative != null ? row.representative : row);
    }
    private void selectRow(Row row) {
        select(row.set.id(), row.set.difficulties().indexOf(row.difficulty));
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
            iterator.remove(); selection = new Selection(set.id(), difficultyId(difficulty));
            if (!search.isBlank()) beforeSearch = selection;
            revealSelection = true; groupTargetKey = null; focusKey = null;
            expand(); return;
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
            revealSelection = true; groupTargetKey = null; focusKey = null;
            set = visible.getFirst(); selection = new Selection(set.id(), difficultyId(set.difficulties().getFirst()));
        } else if (set != null && set.difficulties().stream().noneMatch(d -> difficultyId(d).equals(selection.difficultyId()))) {
            selection = new Selection(set.id(), difficultyId(set.difficulties().getFirst()));
        }
        // Retain identity through zero results, but expose no playable selection.
        expand();
    }
    /** Retain hidden/excluded rows; entries is only the renderer's visible projection. */
    private void expand() {
        Map<String, Row> retained = new LinkedHashMap<>();
        Map<String, List<Row>> children = new LinkedHashMap<>();
        Map<String, Indexed> indexed = new HashMap<>();
        for (var item : library) indexed.put(item.set.id(), item);
        // Matching sets follow browser order. Excluded rows remain at the end, with state 0.
        List<BeatmapSet> ordered = new ArrayList<>(visible);
        for (var item : library) if (!visibleById.containsKey(item.set.id())) ordered.add(item.set);
        for (var set : ordered) {
            boolean excluded = !visibleById.containsKey(set.id());
            Row parent = null;
            if (group != Group.NONE) {
                Bucket bucket = bucket(indexed.get(set.id()));
                String key = "group:" + group + ":" + bucket.label();
                parent = retained.get(key);
                if (parent == null) {
                    parent = rowsByKey.computeIfAbsent(key, Row::new);
                    parent.label = bucket.label();
                    parent.matchingChildren = 0;
                    retained.put(key, parent);
                    children.put(key, new ArrayList<>());
                }
                if (!excluded) parent.matchingChildren += set.difficulties().size();
            }
            Row representative = null;
            for (var difficulty : set.difficulties()) {
                String key = rowKey(set.id(), difficultyId(difficulty));
                Row row = rowsByKey.computeIfAbsent(key, Row::new);
                row.set = set; row.difficulty = difficulty; row.parent = parent;
                row.excluded = excluded;
                if (representative == null) representative = row;
                row.representative = excluded ? null : representative;
                retained.put(key, row);
                if (parent != null) children.get(parent.key).add(row);
            }
        }
        Row selected = retained.get(selectedKey());
        if (revealSelection || openGroupKey != null && !retained.containsKey(openGroupKey)) {
            openGroupKey = selected == null || selected.parent == null ? null : selected.parent.key;
            revealSelection = false;
        }
        List<Row> orderedRows = new ArrayList<>();
        for (Row row : retained.values()) {
            if (row.group()) {
                row.excluded = row.matchingChildren == 0;
                row.expanded = row.key.equals(openGroupKey);
                row.state = row.excluded ? RowState.HIDDEN : row.expanded ? RowState.EXPANDED : RowState.COLLAPSED;
                orderedRows.add(row);
                orderedRows.addAll(children.get(row.key));
            } else if (row.parent == null) orderedRows.add(row);
        }
        List<Entry> result = new ArrayList<>();
        for (Row row : orderedRows) {
            if (!row.group()) {
                boolean sameSet = selected != null && row.set.id().equals(selected.set.id());
                row.state = row.excluded || row.parent != null && !row.parent.expanded ? RowState.HIDDEN
                        : row == selected ? RowState.SELECTED : sameSet ? RowState.EXPANDED
                        : row.set.difficulties().size() == 1 ? RowState.SINGLETON
                        : row == row.representative ? RowState.COLLAPSED : RowState.HIDDEN;
            }
            if (!row.visible()) continue;
            Kind kind = row.group() ? Kind.GROUP_HEADER
                    : row.state == RowState.COLLAPSED ? Kind.SET : Kind.DIFFICULTY;
            result.add(new Entry(kind, row.key, row.label, row.set,
                    kind == Kind.DIFFICULTY ? row.difficulty : null));
        }
        rowsByKey.keySet().retainAll(retained.keySet());
        rows = List.copyOf(orderedRows);
        // Moving between expanded siblings only changes selection; keep the carousel projection
        // intact. A library refresh must still publish the new metadata object references.
        if (!sameProjection(result)) entries = List.copyOf(result);
        Row target = rowsByKey.get(groupTargetKey);
        if (target == null || !target.visible()) groupTargetKey = null;
        Row focused = rowsByKey.get(focusKey);
        if (focused == null || !focused.visible()) focusKey = null;
    }
    private boolean sameProjection(List<Entry> next) {
        if (entries.size() != next.size()) return false;
        for (int i = 0; i < next.size(); i++) {
            Entry a = entries.get(i), b = next.get(i);
            if (a.kind() != b.kind() || !a.key().equals(b.key()) || !a.label().equals(b.label())
                    || a.set() != b.set() || a.difficulty() != b.difficulty()) return false;
        }
        return true;
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
