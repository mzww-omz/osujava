package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.text.Normalizer;
import java.util.*;
import java.util.random.RandomGenerator;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** GL-free browser. Rebuilds only on state changes; identities never use display indices. */
final class SongBrowserModel {
    enum Sort {
        TITLE("Title"), ARTIST("Artist"), CREATOR("Creator"), BPM("BPM"), LENGTH("Length"), RECENT("Last Played"), ADDED("Date Added");
        final String label;
        Sort(String label) { this.label = label; }
    }
    enum Group {
        NONE("No Grouping"), ARTIST("Artist"), CREATOR("Creator"), BPM("BPM"), LENGTH("Length"), RECENT("Recently Played");
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
    private record Indexed(BeatmapSet set, List<SongBrowserQuery.Document> difficulties) { }
    private record Chart(Indexed item, BeatmapDifficulty difficulty, List<String> fields) {
        Chart(Indexed item, BeatmapDifficulty difficulty) {
            this(item,difficulty,List.of(normalize(difficulty.title()),normalize(difficulty.artist()),normalize(difficulty.creator())));
        }
    }
    private List<Chart> orderedCharts = List.of();
    private Map<String, Integer> familySizes = Map.of();
    private record Bucket(int order, String label) { }
    static final int HISTORY_LIMIT = 64;
    private List<Indexed> library = List.of();
    private List<BeatmapSet> visible = List.of();
    private Map<String, BeatmapSet> visibleById = Map.of();
    private Map<String, List<BeatmapDifficulty>> matchingDifficulties = Map.of();
    private List<BeatmapSet> snapshot = List.of();
    private List<Entry> entries = List.of();
    private Selection selection, beforeSearch;
    private String search = "";
    private Sort sort = Sort.TITLE;
    private Group group = Group.NONE;
    private final Deque<Selection> history = new ArrayDeque<>();
    private final RandomGenerator random;
    private Map<BeatmapDifficulty,SongBrowserActivity.Facts> activity=Map.of();
    private Clock wallClock=Clock.systemDefaultZone();
    private LocalDate today;
    void activity(Map<BeatmapDifficulty,SongBrowserActivity.Facts> next, Clock clock) {
        if(activity==next && wallClock==clock) return;
        activity=next; wallClock=clock; rebuild();
    }
    private SongBrowserActivity.Facts facts(BeatmapDifficulty diff) { return activity.getOrDefault(diff,SongBrowserActivity.Facts.UNKNOWN); }

    SongBrowserModel(List<BeatmapSet> sets) { this(sets, RandomGenerator.getDefault()); }
    SongBrowserModel(List<BeatmapSet> sets, RandomGenerator random) { this.random = random; library(sets); }
    void library(List<BeatmapSet> sets) {
        library = sets.stream().map(set -> new Indexed(set,
                set.difficulties().stream().map(SongBrowserQuery.Document::of).toList())).toList();
        snapshot = sets.stream().sorted(Comparator.comparing((BeatmapSet s) -> normalize(s.title()))
                .thenComparing(s -> normalize(s.artist())).thenComparing(s -> normalize(s.creator())).thenComparing(BeatmapSet::id)).toList();
        rebuild();
    }
    List<BeatmapSet> librarySets() { return snapshot; }
    /** 06003c95: raw maximum BPM, independently of the rounded common BPM search field. */
    private static double maximumBpm(BeatmapDifficulty difficulty) {
        if (difficulty.timingPoints().isEmpty()) return 0;
        double minimumBeat = 5000;
        for (var point : difficulty.timingPoints())
            if (point.uninherited() && Double.isFinite(point.beatLength()) && point.beatLength() < minimumBeat)
                minimumBeat = point.beatLength();
        return minimumBeat == 0 ? 0 : 60000 / minimumBeat;
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
    private static boolean emptySearch(String value) { return value.chars().allMatch(c -> c == ' '); }
    void search(String next) {
        next = Objects.requireNonNullElse(next, "");
        if (search.equals(next)) return;
        if (emptySearch(search) && !emptySearch(next)) beforeSearch = selection;
        boolean clearing = !emptySearch(search) && emptySearch(next);
        search = next;
        revealSelection = true; groupTargetKey = null; focusKey = null;
        if (clearing && beforeSearch != null) selection = beforeSearch;
        rebuild();
        if (clearing) beforeSearch = null;
    }
    void select(String setId, int difficultyIndex) {
        BeatmapSet set = visibleById.get(setId);
        if (set == null) return;
        var difficulty = set.difficulties().get(Math.max(0, Math.min(difficultyIndex, set.difficulties().size() - 1)));
        if (!matches(set, difficulty)) return;
        Selection next = new Selection(set.id(), difficultyId(difficulty));
        if (next.equals(selection) && groupTargetKey == null && focusKey == null) return;
        selection = next;
        if (!emptySearch(search)) beforeSearch = next;
        revealSelection = true; groupTargetKey = null; focusKey = null;
        expand();
    }

    void selectSet(String setId) {
        var set = visibleById.get(setId);
        if (set == null) return;
        select(setId, set.difficulties().indexOf(matchingDifficulties.get(setId).getFirst()));
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
    void activateRow(String key) {
        Row row = rowsByKey.get(key);
        if (row != null) activate(row);
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
        selectSet(candidates.get(random.nextInt(candidates.size())).id());
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
            if (!matches(set, difficulty)) continue;
            iterator.remove(); selection = new Selection(set.id(), difficultyId(difficulty));
            if (!emptySearch(search)) beforeSearch = selection;
            revealSelection = true; groupTargetKey = null; focusKey = null;
            expand(); return;
        }
    }
    int historySize() { return history.size(); }
    private boolean matches(BeatmapSet set, BeatmapDifficulty difficulty) {
        return matchingDifficulties.getOrDefault(set.id(), List.of()).contains(difficulty);
    }
    private boolean classified(BeatmapDifficulty difficulty) {
        if (group == Group.LENGTH) return difficulty.timingStatistics().lengthMs() >= 0;
        if (group != Group.BPM) return true;
        double bpm = maximumBpm(difficulty);
        // 060032b2 uses [lower,upper), while 0600329d uses strictly >300.
        // Exactly 300 has no native Group; preserve that boundary instead of inventing one.
        return bpm >= 0 && bpm != 300;
    }
    private BeatmapSet find(Selection selected) {
        return selected == null ? null : visibleById.get(selected.setId());
    }
    private void rebuild() {
        today=LocalDate.now(wallClock);
        var query = new SongBrowserQuery(search,this::facts,wallClock);
        Map<String, List<BeatmapDifficulty>> matching = new HashMap<>();
        for (var item : library) {
            var matches = item.difficulties().stream().filter(query::matches).map(SongBrowserQuery.Document::difficulty)
                    .filter(this::classified).toList();
            if (!matches.isEmpty()) matching.put(item.set().id(), matches);
        }
        matchingDifficulties = Map.copyOf(matching);
        Comparator<Chart> secondary = Comparator.comparing((Chart c) -> c.fields.get(0))
                .thenComparing(c -> c.fields.get(1)).thenComparing(c -> c.fields.get(2)).thenComparing(c -> c.item.set.id());
        Comparator<Chart> primary = switch (sort) {
            case TITLE -> secondary;
            case ARTIST -> Comparator.comparing(c -> c.fields.get(1));
            case CREATOR -> Comparator.comparing(c -> c.fields.get(2));
            case BPM -> Comparator.comparingDouble(c -> maximumBpm(c.difficulty));
            case LENGTH -> Comparator.comparingInt(c -> c.difficulty.timingStatistics().lengthSeconds());
            case RECENT -> Comparator.comparing((Chart c) -> facts(c.difficulty).lastPlayedAt(),Comparator.nullsLast(Comparator.reverseOrder()));
            case ADDED -> Comparator.comparing((Chart c) -> facts(c.difficulty).addedAt(),Comparator.nullsLast(Comparator.reverseOrder()));
        };
        Comparator<Chart> ordering = primary.thenComparing(secondary);
        if (group != Group.NONE) ordering = Comparator.comparing((Chart c) -> bucket(c).order())
                .thenComparing(c -> bucket(c).label()).thenComparing(ordering);
        // Sort difficulty records before taking the unique Set projection. Unmatched charts
        // retain identity after the matching records, but cannot determine their order/family.
        Comparator<Chart> finalOrdering = ordering;
        orderedCharts = library.stream().flatMap(i -> i.set.difficulties().stream().map(d -> new Chart(i,d)))
                .filter(c -> classified(c.difficulty))
                .sorted(Comparator.comparing((Chart c) -> !matches(c.item.set,c.difficulty)).thenComparing(finalOrdering)).toList();
        visible = orderedCharts.stream().filter(c -> matches(c.item.set,c.difficulty)).map(c -> c.item.set).distinct().toList();
        Map<String, BeatmapSet> nextById = new HashMap<>();
        for (var item : visible) nextById.put(item.id(), item);
        visibleById = Map.copyOf(nextById);
        var set = find(selection);
        if (set == null && !visible.isEmpty()) {
            revealSelection = true; groupTargetKey = null; focusKey = null;
            set = visible.getFirst(); selection = new Selection(set.id(), difficultyId(matchingDifficulties.get(set.id()).getFirst()));
        } else if (set != null && matchingDifficulties.get(set.id()).stream().noneMatch(d -> difficultyId(d).equals(selection.difficultyId()))) {
            selection = new Selection(set.id(), difficultyId(matchingDifficulties.get(set.id()).getFirst()));
        }
        // Retain identity through zero results, but expose no playable selection.
        rebuildRows();
        expand();
    }
    /** Retain hidden/excluded rows; entries is only the renderer's visible projection. */
    private void rebuildRows() {
        Map<String, Row> retained = new LinkedHashMap<>();
        Map<String, List<Row>> children = new LinkedHashMap<>();
        Row representative = null;
        Map<String, Integer> familySizes = new HashMap<>();
        for (var chart : orderedCharts) {
            var set = chart.item.set;
            var difficulty = chart.difficulty;
            Row parent = null;
            if (group != Group.NONE) {
                Bucket bucket = bucket(chart);
                String key = "group:" + group + ":" + bucket.label();
                parent = retained.get(key);
                if (parent == null) {
                    parent = rowsByKey.computeIfAbsent(key, Row::new);
                    parent.label = bucket.label(); parent.matchingChildren = 0;
                    retained.put(key, parent); children.put(key, new ArrayList<>());
                }
                if (matches(set,difficulty)) parent.matchingChildren++;
            }
            String key = rowKey(set.id(),difficultyId(difficulty));
            Row row = rowsByKey.computeIfAbsent(key, Row::new);
            row.set = set; row.difficulty = difficulty; row.parent = parent;
            row.excluded = !matches(set,difficulty);
            if (!row.excluded) {
                // 06003257: a Group or a change of the preceding non-excluded family
                // starts a new representative; non-adjacent records must not merge.
                if (representative == null || representative.parent != parent || !representative.set.id().equals(set.id()))
                    representative = row;
                familySizes.merge(representative.key,1,Integer::sum);
            }
            row.representative = row.excluded ? null : representative;
            retained.put(key,row);
            if (parent != null) children.get(parent.key).add(row);
        }
        List<Row> orderedRows = new ArrayList<>();
        for (Row row : retained.values()) {
            if (row.group()) {
                row.excluded = row.matchingChildren == 0;
                orderedRows.add(row);
                orderedRows.addAll(children.get(row.key));
            } else if (row.parent == null) orderedRows.add(row);
        }
        rowsByKey.keySet().retainAll(retained.keySet());
        rows = List.copyOf(orderedRows);
        this.familySizes = Map.copyOf(familySizes);
    }

    /** Selection and group activation change states, never row identity, metadata or order. */
    private void expand() {
        Row selected = rowsByKey.get(selectedKey());
        if (revealSelection || openGroupKey != null && !rowsByKey.containsKey(openGroupKey)) {
            openGroupKey = selected == null || selected.parent == null ? null : selected.parent.key;
            revealSelection = false;
        }
        for (Row row : rows) if (row.group()) {
            row.expanded = row.key.equals(openGroupKey);
            row.state = row.excluded ? RowState.HIDDEN : row.expanded ? RowState.EXPANDED : RowState.COLLAPSED;
        }
        List<Entry> result = new ArrayList<>();
        for (Row row : rows) {
            if (!row.group()) {
                boolean sameSet = selected != null && row.set.id().equals(selected.set.id());
                row.state = row.excluded || row.parent != null && !row.parent.expanded ? RowState.HIDDEN
                        : row == selected ? RowState.SELECTED : sameSet ? RowState.EXPANDED
                        : row.representative != null && familySizes.getOrDefault(row.representative.key, 0) == 1 ? RowState.SINGLETON
                        : row == row.representative ? RowState.COLLAPSED : RowState.HIDDEN;
            }
            if (!row.visible()) continue;
            Kind kind = row.group() ? Kind.GROUP_HEADER
                    : row.state == RowState.COLLAPSED ? Kind.SET : Kind.DIFFICULTY;
            result.add(new Entry(kind, row.key, row.label, row.set,
                    kind == Kind.DIFFICULTY ? row.difficulty : null));
        }
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
    private Bucket bucket(Chart chart) {
        return switch (group) {
            case NONE -> new Bucket(0, "");
            case ARTIST -> initial(chart.difficulty.artist());
            case CREATOR -> initial(chart.difficulty.creator());
            case RECENT -> {
                var time=facts(chart.difficulty).lastPlayedAt();
                long age=time==null ? Long.MAX_VALUE : Math.max(0,ChronoUnit.DAYS.between(Instant.ofEpochMilli(time).atZone(wallClock.getZone()).toLocalDate(),today));
                int band=age==0 ? 0 : age==1 ? 1 : age<7 ? 2 : time!=null ? 3 : 4;
                yield new Bucket(band,new String[]{"Today","Yesterday","Last 7 Days","Older","Never Played"}[band]);
            }
            case BPM -> {
                double bpm = maximumBpm(chart.difficulty);
                int band = (int)Math.min(5,bpm/60);
                yield new Bucket(band,band == 5 ? ">300 BPM" : band*60 + "–<" + (band+1)*60 + " BPM");
            }
            case LENGTH -> {
                int length = chart.difficulty.timingStatistics().lengthMs();
                int band = length < 60000 ? 0 : length < 120000 ? 1 : length < 180000 ? 2
                        : length < 240000 ? 3 : length < 300000 ? 4 : length < 600000 ? 5 : 6;
                yield new Bucket(band,new String[]{"Under 1 minute","1–<2 minutes","2–<3 minutes",
                        "3–<4 minutes","4–<5 minutes","5–<10 minutes","10+ minutes"}[band]);
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
