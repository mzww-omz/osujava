package dev.osujava.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UI-only content coordinates (downwards), viewport and transient visual offsets. */
final class SongSelectCarousel {
    record Entry(String key, int setIndex, int difficultyIndex, String familyKey, boolean expanded, boolean visible) {
        Entry(String key, int setIndex, int difficultyIndex, String familyKey, boolean expanded) {
            this(key, setIndex, difficultyIndex, familyKey, expanded, true);
        }
        Entry(String key, int setIndex, int difficultyIndex) {
            this(key, setIndex, difficultyIndex, setIndex < 0 ? key : Integer.toString(setIndex), difficultyIndex >= 0);
        }
        boolean header() { return setIndex < 0; }
    }
    static final class Row {
        Entry entry;
        float logicalY;
        int logicalIndex;
        float hoverAmount, separationY, selectedAmount, groupAmount, focusAmount;
        float focusStart, focusElapsed;
        boolean focused, resident, instantSprites;
        float motionY, motionX, revealAmount = 1;
        Row(Entry entry, float logicalY) { this.entry = entry; this.logicalY = logicalY; }
    }

    private List<Row> rows = List.of(), allRows = List.of();
    private List<Entry> contentEntries = List.of();
    private final Map<String, Row> byKey = new HashMap<>();
    private String selectedKey, hoverKey, focusKey, emphasisKey, selectionTrackingKey;
    private float hoverAbsence, viewportHeight, rowHeight = 76, maxScroll;
    private final SongSelectScroll scroll = new SongSelectScroll();
    private boolean keyboardTracking, pointerTracking;
    private boolean initialized, snapNextFrame;
    private int activeStart, activeEnd;
    private float selectionAnchor, screenHeight, viewportTop, referenceScale;
    private float rowStep = 72;

    /** Diagnostic velocity in logical UI units per second. */
    float scrollVelocity() { return (float) (scroll.velocity() * referenceScale * 1000); }
    float predictedTravel() { return (float) (scroll.remaining() * referenceScale); }

    List<Row> rows() { return rows; }
    List<Row> allRows() { return allRows; }
    String hoverKey() { return hoverKey; }
    boolean presents(Row row) {
        return row.entry.visible() && row.resident && row.logicalIndex >= activeStart && row.logicalIndex < activeEnd;
    }
    float scrollOffset() { return (float) (scroll.position() * referenceScale); }
    float scrollTarget() { return (float) (scroll.destination() * referenceScale); }
    float maxScroll() { return maxScroll; }
    float rowHeight() { return rowHeight; }

    /** Equal full-row projections preserve visible list identity and motion through selection changes. */
    void content(List<Entry> entries, float height, float size, float step, String selection) {
        content(entries, height, size, step, selection, height, height);
    }

    void content(List<Entry> entries, float height, float size, float pitch,
                 String selection, float fullHeight, float top) {
        if (initialized && contentEntries.equals(entries) && viewportHeight == height && rowHeight == size
                && screenHeight == fullHeight && viewportTop == top && rowStep == pitch) {
            select(selection);
            return;
        }
        boolean sameOrder = allRows.size() == entries.size();
        for (int i = 0; sameOrder && i < entries.size(); i++)
            sameOrder = allRows.get(i).entry.key().equals(entries.get(i).key());
        List<String> openingGroups = new ArrayList<>();
        Map<String, Row> previous = new HashMap<>(byKey);
        Map<String, Row> representatives = new HashMap<>();
        for (Row row : allRows) representatives.putIfAbsent(row.entry.familyKey(), row);
        float oldScale = referenceScale, oldOrigin = viewportTop - screenHeight;
        viewportHeight = validSize(height, 620);
        screenHeight = validSize(fullHeight, viewportHeight);
        viewportTop = Float.isFinite(top) ? top : screenHeight;
        referenceScale = SongSelectMetrics.carouselScale(screenHeight);
        selectionAnchor = viewportTop - screenHeight + SongSelectMetrics.SELECTION_Y * referenceScale;
        rowHeight = validSize(size, 76);
        rowStep = validSize(pitch, 72);
        float ratio = oldScale > 0 ? referenceScale / oldScale : 1;
        // Reference-space scroll and velocity survive scale changes without an anchor correction.
        for (Row row : allRows) {
            row.motionY = viewportTop - screenHeight + (row.motionY - oldOrigin) * ratio;
            row.motionX *= ratio;
        }
        byKey.clear();
        List<Row> next = new ArrayList<>(), visible = new ArrayList<>();
        Entry previousVisible = null;
        float logicalY = viewportTop - screenHeight + SongSelectMetrics.FIRST_ROW_Y * referenceScale - rowStep;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.visible()) {
                boolean gap = previousVisible != null && (entry.header() ? !previousVisible.header()
                        : previousVisible.expanded() || entry.expanded());
                logicalY += rowStep + (gap ? SongSelectMetrics.OPEN_SPACING * referenceScale : 0);
            }
            // Hidden rows have an index/coordinate too, but do not advance the visible pitch.
            Row row = previous.get(entry.key());
            if (initialized && entry.header() && entry.expanded()
                    && (row == null || !row.entry.expanded())) openingGroups.add(entry.key());
            if (row == null) {
                row = new Row(entry, logicalY);
                Row representative = representatives.get(entry.familyKey());
                row.motionY = representative == null ? logicalY : representative.motionY;
                row.motionX = representative == null ? horizontalTarget(entry, row.motionY, false) : representative.motionX;
            } else if (!row.entry.visible() && entry.visible()) {
                // 06000fd3: a returning non-representative inherits the representative's position.
                Row representative = representatives.get(entry.familyKey());
                if (representative != null && representative != row) {
                    row.motionY = representative.motionY;
                    row.motionX = representative.motionX;
                }
            }
            row.entry = entry; row.logicalY = logicalY; row.logicalIndex = i;
            if (!entry.visible()) row.resident = false;
            next.add(row); byKey.put(entry.key(), row);
            if (entry.visible()) { visible.add(row); previousVisible = entry; }
        }
        allRows = List.copyOf(next); rows = List.copyOf(visible); contentEntries = List.copyOf(entries);
        if (!sameOrder) { activeStart = 0; activeEnd = allRows.size(); }
        maxScroll = rows.isEmpty() ? 0 : rows.getLast().logicalY - rows.getFirst().logicalY;
        scroll.range(maxScroll / (double) referenceScale);
        if (!java.util.Objects.equals(selection, selectedKey) || !initialized) select(selection);
        if (!initialized) {
            scroll.jump(scroll.destination());
            for (Row row : allRows) {
                row.motionX = horizontalTarget(row.entry, row.motionY, false);
                row.groupAmount = row.entry.header() || row.entry.expanded() ? 1 : 0;
                row.selectedAmount = row.entry.key().equals(selection) ? 1 : 0;
            }
            initialized = true;
        }
        for (String key : openingGroups) seedGroup(byKey.get(key));
        Row hover = byKey.get(hoverKey);
        if (hover == null || !hover.entry.visible()) { hoverKey = null; hoverAbsence = 0; }
    }

    /** 06003273: seed every child; only visible family representatives advance the starting Y. */
    private void seedGroup(Row group) {
        float y = group.motionY;
        var families = new java.util.HashSet<String>();
        for (int i = group.logicalIndex + 1; i < allRows.size(); i++) {
            Row row = allRows.get(i);
            if (row.entry.header()) break;
            row.motionX = group.motionX; row.motionY = y;
            if (insideBuffer(y) && !row.resident) { row.resident = true; row.instantSprites = false; }
            if (families.add(row.entry.familyKey()) && row.entry.visible())
                y += SongSelectMetrics.ROW_PITCH * referenceScale;
        }
    }

    /** Native one-frame snap flag, used when entering the screen after selection is restored. */
    void snapOnNextFrame() { snapNextFrame = true; }

    /** Visual keyboard focus is separate from both scrolling and the playable selection. */
    void focus(String key) { focusKey = key; }
    void emphasize(String key) { emphasisKey = key; }
    void selectionTrackingTarget(String key) { selectionTrackingKey = key; }
    void select(String key) {
        emphasisKey = key;
        if (initialized && java.util.Objects.equals(selectedKey, key)) return;
        selectedKey = key;
        selectionTrackingKey = key;
        Row selected = byKey.get(key);
        scroll.seek(selected == null ? scroll.position() : (selected.logicalY - selectionAnchor) / referenceScale,
                SongSelectScroll.SELECT_DECAY);
    }

    /** Reordering has no spatial correspondence: discard row travel and center selection. */
    void reordered() {
        activeStart = 0; activeEnd = allRows.size();
        for (Row row : allRows) row.motionY = row.logicalY;
        Row selected = byKey.get(selectedKey);
        scroll.seek(selected == null ? 0 : (selected.logicalY - selectionAnchor) / referenceScale,
                SongSelectScroll.SELECT_DECAY);
    }

    /** Keyboard row traversal continues tracking until a pointer press or wheel event. */
    void keyboardNavigation() { keyboardTracking = true; }
    void pointerPressed() { keyboardTracking = false; }
    void pointerTracking(float referenceX, boolean held, boolean enabled) {
        pointerTracking = enabled && !held && referenceX < 200;
        if (held || !enabled) keyboardTracking = false;
    }
    void wheel(float amount) {
        if (!Float.isFinite(amount) || amount == 0) return;
        keyboardTracking = false;
        scroll.wheel(amount);
    }
    /** Local preview/debug distance command; physical wheel events use wheel(). */
    void scrollBy(float distance) {
        if (!Float.isFinite(distance) || distance == 0 || referenceScale <= 0) return;
        keyboardTracking = false;
        scroll.seek(scroll.destination() + distance / referenceScale, SongSelectScroll.SELECT_DECAY);
    }
    /** Instant local positioning for diagnostics; pointer gestures use the timed drag methods. */
    void dragBy(float distance) {
        if (!Float.isFinite(distance) || referenceScale <= 0) return;
        keyboardTracking = false;
        scroll.jump(scroll.position() + distance / referenceScale);
    }

    void beginDrag(String key) {
        keyboardTracking = false;
        hoverKey = key; hoverAbsence = 0;
        scroll.beginDrag();
    }
    void drag(float distance, float delta) {
        if (referenceScale <= 0) return;
        float dt = Float.isFinite(delta) ? Math.max(0, Math.min(2, delta)) : 0;
        scroll.drag(distance / (double) referenceScale, dt * 1000);
    }
    void releaseDrag() { scroll.releaseDrag(); }
    void cancelDrag() { scroll.cancelDrag(); }
    boolean pointerCancellationEnabled(float referenceX) {
        return !keyboardTracking && (referenceX >= 200 || scroll.dragging());
    }

    /** 06003253/324f: right-button position in the 70..400 reference-Y interval. */
    void rightScroll(float referenceX, float referenceY) {
        if (!Float.isFinite(referenceX) || !Float.isFinite(referenceY)
                || referenceX < 200 || keyboardTracking || scroll.dragging()) return;
        float fraction = Math.max(0, Math.min(1, (referenceY - 70) / 330));
        scroll.seek(fraction * maxScroll / referenceScale, SongSelectScroll.TRACK_DECAY);
    }

    /** Retain the existing hover gap policy; row displacement is independent of scroll speed. */
    void advance(float delta, String hitKey) {
        advance(delta, hitKey, false);
    }
    void advance(float delta, String hitKey, boolean rightScrolling) {
        float dt = Float.isFinite(delta) ? Math.max(0, Math.min(2, delta)) : 0;
        // 06003256/3267 preserve the hover identity throughout either scroll gesture.
        if (!scroll.dragging() && !rightScrolling) {
            if (hitKey != null && byKey.containsKey(hitKey) && byKey.get(hitKey).entry.visible()) { hoverKey = hitKey; hoverAbsence = 0; }
            else if ((hoverAbsence += dt) >= .075f) hoverKey = null;
        }
        if (dt > 0) {
            Row tracking = byKey.get(keyboardTracking && focusKey != null ? focusKey : selectionTrackingKey);
            if (!scroll.dragging() && (keyboardTracking || pointerTracking) && tracking != null && tracking.entry.visible())
                scroll.seek((tracking.logicalY - selectionAnchor) / referenceScale, SongSelectScroll.TRACK_DECAY);
            scroll.advance(dt * 1000);
        }
        float hoverEase = ease(dt, 19), releaseEase = ease(dt, 13);
        Row hovered = byKey.get(hoverKey), selected = byKey.get(emphasisKey);
        advanceRows(dt, hovered);
        float selectionEase = ease(dt, 10);
        for (Row row : rows) {
            boolean focused = row.entry.key().equals(focusKey);
            if (row.focused != focused) {
                row.focusStart = row.focusAmount; row.focusElapsed = 0; row.focused = focused;
            }
            row.focusElapsed = Math.min(.05f, row.focusElapsed + dt);
            row.focusAmount = row.focusStart + ((focused ? 1 : 0) - row.focusStart) * (row.focusElapsed / .05f);
            row.groupAmount += (((row.entry.header() || row.entry.expanded()) ? 1 : 0) - row.groupAmount) * (1 - (float)Math.pow(.95, dt * 60));
            row.hoverAmount += ((row == hovered ? 1 : 0) - row.hoverAmount) * (row == hovered ? hoverEase : releaseEase);
            row.selectedAmount += ((row == selected ? 1 : 0) - row.selectedAmount) * selectionEase;
        }
    }

    private float targetY(Row row, Row hovered) {
        return row.logicalY + (hovered == null ? 0
                : Math.signum(row.logicalIndex - hovered.logicalIndex) * SongSelectMetrics.HOVER_SPACING * referenceScale);
    }
    private float screenY(float y) { return screenHeight - viewportTop + y - scrollOffset(); }
    private boolean aboveBuffer(float screenY) { return screenY < -20 * referenceScale; }
    private boolean belowBuffer(float screenY) { return screenY > 640 * referenceScale; }
    private boolean insideBuffer(float y) { return !aboveBuffer(screenY(y)) && !belowBuffer(screenY(y)); }
    private float stateIndent(Row row, Row hovered) {
        return -((row.entry.header() || row.entry.expanded()) ? SongSelectMetrics.OPEN_INDENT * referenceScale : 0)
                - (row == hovered ? SongSelectMetrics.HOVER_INDENT * referenceScale : 0);
    }

    /** Active indices include hidden rows, as do native layout and range scans (06003263/3267). */
    private void advanceRows(float dt, Row hovered) {
        activeEnd = Math.min(activeEnd, allRows.size()); activeStart = Math.min(activeStart, activeEnd);
        Row anchor = null;
        for (int i = activeStart; i < activeEnd; i++)
            if (allRows.get(i).entry.visible()) { anchor = allRows.get(i); break; }
        while (activeStart > 0) {
            Row row = allRows.get(activeStart - 1);
            if (aboveBuffer(screenY(targetY(row, hovered)) - predictedTravel())) break;
            activeStart--;
            reenter(row, anchor, hovered);
            if (row.entry.visible()) anchor = row;
        }
        anchor = null;
        for (int i = activeEnd - 1; i >= activeStart; i--)
            if (allRows.get(i).entry.visible()) { anchor = allRows.get(i); break; }
        while (activeEnd < allRows.size()) {
            Row row = allRows.get(activeEnd);
            if (belowBuffer(screenY(targetY(row, hovered)) - predictedTravel())) break;
            activeEnd++;
            reenter(row, anchor, hovered);
            if (row.entry.visible()) anchor = row;
        }
        for (int i = activeStart; i < activeEnd; i++) {
            Row row = allRows.get(i);
            if (!row.entry.visible()) { row.resident = false; continue; }
            float y = targetY(row, hovered), x = horizontalTarget(row.entry, row.motionY, row == hovered);
            row.motionX = snapNextFrame ? x : interpolate(row.motionX, x, .95, dt);
            row.motionY = snapNextFrame ? y : interpolate(row.motionY, y, .875, dt);
            row.separationY = row.logicalY - row.motionY;
            if (insideBuffer(row.motionY) && !row.resident) { row.resident = true; row.instantSprites = true; }
            else if (belowBuffer(screenY(y)) && belowBuffer(screenY(row.motionY))) {
                // Both current and destination are below the buffer: retire and snap the whole suffix.
                for (int j = activeEnd - 1; j >= i; j--) retire(allRows.get(j), hovered);
                activeEnd = i;
            } else if (aboveBuffer(screenY(y)) && aboveBuffer(screenY(row.motionY))) {
                for (int j = activeStart; j <= i; j++) retire(allRows.get(j), hovered);
                activeStart = i + 1;
            }
        }
        snapNextFrame = false;
    }
    private void retire(Row row, Row hovered) {
        row.motionX = horizontalTarget(row.entry, row.motionY, row == hovered);
        row.motionY = targetY(row, hovered);
        row.separationY = row.logicalY - row.motionY;
        row.resident = false;
    }
    /** Recover relative displacement from a visible neighbour, with the native 200-unit X cap. */
    private void reenter(Row row, Row anchor, Row hovered) {
        if (!row.entry.visible()) {
            row.motionX = anchor == null ? 160 * referenceScale : anchor.motionX;
            row.motionY = anchor == null ? viewportTop - screenHeight / 2 : anchor.motionY;
        } else if (anchor != null) {
            row.motionY = anchor.motionY + row.logicalY - anchor.logicalY;
            row.motionX = Math.min(anchor.motionX - stateIndent(anchor, hovered) + stateIndent(row, hovered),
                    (-SongSelectMetrics.ROW_RIGHT_OFFSET + 200) * referenceScale + stateIndent(row, hovered));
        } else {
            row.motionX = horizontalTarget(row.entry, row.motionY, row == hovered) + 200 * referenceScale;
            row.motionY = viewportTop - screenHeight + scrollOffset() + predictedTravel();
        }
    }

    static float interpolate(float current, float target, double decay, float seconds) {
        return (float) (target - (target - current) * Math.pow(decay, seconds * 60));
    }
    private float horizontalTarget(Entry entry, float y, boolean hovered) {
        float screenDown = screenHeight - viewportTop + y - scrollOffset() + predictedTravel();
        return SongSelectMetrics.curveX(screenDown, 0, screenHeight)
                - ((entry.header() || entry.expanded()) ? SongSelectMetrics.OPEN_INDENT * referenceScale : 0)
                - (hovered ? SongSelectMetrics.HOVER_INDENT * referenceScale : 0);
    }
    private float visualDown(Row row) { return row.motionY - scrollOffset(); }
    float renderY(Row row, float top) { return top - visualDown(row) - rowHeight / 2; }
    float renderX(Row row, float width) { return width + row.motionX; }

    /** Normalized screen-center distance on the usual 720-high logical viewport. */
    static float curveX(float distance, float width) {
        float bounded = Float.isFinite(distance) ? Math.min(100, Math.abs(distance)) : 100;
        return SongSelectMetrics.curveX(360 + bounded * 360, width, 720);
    }

    static float skinRowHeight(float logicalWidth, float logicalHeight, float carouselWidth) {
        float aspect = logicalWidth / logicalHeight;
        if (!Float.isFinite(aspect) || aspect < 2.5f || aspect > 12) aspect = 6;
        return Math.max(68, Math.min(110, carouselWidth / aspect));
    }
    /** Settled, unhovered destination; transient hover and velocity offsets are excluded. */
    float[] targetPosition(int index, float width, float top) {
        if (index < 0) return new float[]{0, 0};
        var row = allRows.get(index);
        float down = row.logicalY - scrollTarget();
        float x = SongSelectMetrics.curveX(screenHeight - viewportTop + down, width, screenHeight)
                - ((row.entry.header() || row.entry.expanded()) ? SongSelectMetrics.OPEN_INDENT * referenceScale : 0);
        return new float[]{x, top - down - rowHeight / 2};
    }

    private static float validSize(float value, float fallback) {
        return Float.isFinite(value) && value > 0 ? Math.min(value, 10000) : fallback;
    }
    private static float ease(float dt, float rate) { return (float) -Math.expm1(-dt * rate); }
}
