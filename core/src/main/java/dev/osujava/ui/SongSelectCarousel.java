package dev.osujava.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UI-only content coordinates (downwards), viewport and transient visual offsets. */
final class SongSelectCarousel {
    record Entry(String key, int setIndex, int difficultyIndex) { }
    static final class Row {
        final Entry entry;
        final float logicalY;
        float hoverAmount, separationY, selectedAmount, groupAmount;
        float selectionSeparationY, expansionY, expansionVelocityY, expansionX, revealAmount = 1;
        Row(Entry entry, float logicalY) { this.entry = entry; this.logicalY = logicalY; }
    }

    private List<Row> rows = List.of();
    private final Map<String, Row> byKey = new HashMap<>();
    private String selectedKey, hoverKey;
    private float hoverAbsence, viewportHeight, rowHeight = 76, scrollOffset, scrollTarget, maxScroll;
    private boolean initialized;
    private float scrollVelocity, velocityInfluence, viewportVelocity, rowStep = 72;
    private static final float SPRING_RATE = 18;
    private static final float VELOCITY_DECAY = 5;
    private static final float VELOCITY_LIMIT_ROWS = 36;

    float scrollVelocity() { return scrollVelocity; }
    float velocityInfluence() { return velocityInfluence; }

    List<Row> rows() { return rows; }
    float scrollOffset() { return scrollOffset; }
    float scrollTarget() { return scrollTarget; }
    float maxScroll() { return maxScroll; }
    float rowHeight() { return rowHeight; }

    /** Only content/filter/size changes call this; selection within an expanded set does not. */
    void content(List<Entry> entries, float height, float size, float step, String selection) {
        content(entries, height, size, step, step, selection);
    }

    void content(List<Entry> entries, float height, float size, float setPitch, float difficultyPitch, String selection) {
        Map<String, Row> previous = new HashMap<>(byKey);
        // Representatives let both expansion and collapse inherit the actual on-screen position.
        Map<String, Row> representatives = new HashMap<>();
        for (Row row : rows) {
            representatives.putIfAbsent(setKey(row.entry), row);
            if (row.entry.key().equals(selectedKey)) representatives.put(setKey(row.entry), row);
        }
        Row anchor = previous.get(selection);
        if (anchor == null) for (Entry entry : entries) if (entry.key().equals(selection)) {
            anchor = representatives.get(setKey(entry));
            break;
        }
        float oldOffset = scrollOffset, oldTarget = scrollTarget, oldStep = rowStep, oldHeight = viewportHeight;
        float anchorY = anchor == null ? 0 : anchor.logicalY - oldOffset;
        viewportHeight = validSize(height, 620);
        rowHeight = validSize(size, 76);
        rowStep = validSize(setPitch, 72);
        float childStep = validSize(difficultyPitch, rowStep);
        scrollVelocity = Math.max(-rowHeight * VELOCITY_LIMIT_ROWS, Math.min(rowHeight * VELOCITY_LIMIT_ROWS, scrollVelocity));
        byKey.clear();
        List<Row> next = new ArrayList<>(entries.size());
        float logicalY = viewportHeight / 2;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i > 0) logicalY += (entries.get(i - 1).difficultyIndex() >= 0 && entry.difficultyIndex() >= 0
                    && entries.get(i - 1).setIndex() == entry.setIndex()) ? childStep : rowStep;
            Row row = new Row(entry, logicalY);
            row.groupAmount = !initialized && entry.difficultyIndex() >= 0 ? 1 : 0;
            Row old = previous.get(entry.key());
            if (old != null) copyEmphasis(old, row);
            next.add(row);
            byKey.put(entry.key(), row);
        }
        rows = List.copyOf(next);
        maxScroll = Math.max(0, logicalY - viewportHeight / 2);
        Row newAnchor = byKey.get(selection);
        if (initialized && anchor != null && newAnchor != null)
            scrollOffset = newAnchor.logicalY - anchorY;
        scrollOffset = clamp(scrollOffset);
        for (Row row : rows) {
            Row old = previous.get(row.entry.key());
            boolean replaced = old == null;
            if (replaced) old = representatives.get(setKey(row.entry));
            if (!initialized || old == null) continue;
            row.expansionVelocityY = old.expansionVelocityY;
            row.groupAmount = old.groupAmount;
            if (replaced) {
                if (row.entry.key().equals(selection) || row.entry.difficultyIndex() < 0) copyEmphasis(old, row);
                else { row.revealAmount = 0; row.expansionX = 6; }
            }
            // Include offsets from an interrupted transition; repeated navigation never resets motion.
            float oldDown = old.logicalY - oldOffset + old.expansionY - old.separationY - old.selectionSeparationY;
            row.expansionY = oldDown - (row.logicalY - scrollOffset) + row.separationY + row.selectionSeparationY;
        }
        if (!java.util.Objects.equals(selection, selectedKey) || !initialized) select(selection);
        else if (newAnchor != null) {
            float browsingOffset = anchor == null ? 0 : oldTarget - anchor.logicalY + oldHeight / 2;
            scrollTarget = clamp(newAnchor.logicalY - viewportHeight / 2 + browsingOffset * rowStep / oldStep);
        } else scrollTarget = clamp(oldTarget * rowStep / oldStep);
        if (!initialized) {
            scrollOffset = scrollTarget;
            if (newAnchor != null) newAnchor.selectedAmount = 1;
            initialized = true;
        }
        if (rows.isEmpty()) { scrollVelocity = 0; velocityInfluence = 0; viewportVelocity = 0; }
        if (!byKey.containsKey(hoverKey)) { hoverKey = null; hoverAbsence = 0; }
    }

    private static String setKey(Entry entry) {
        int separator = entry.key().lastIndexOf('#');
        return separator < 0 ? entry.key() : entry.key().substring(0, separator);
    }

    private static void copyEmphasis(Row old, Row row) {
        row.groupAmount = old.groupAmount;
        row.hoverAmount = old.hoverAmount;
        row.separationY = old.separationY;
        row.selectedAmount = old.selectedAmount;
        row.selectionSeparationY = old.selectionSeparationY;
        row.revealAmount = old.revealAmount;
        row.expansionX = old.expansionX;
    }

    void select(String key) {
        if (initialized && java.util.Objects.equals(selectedKey, key)) return;
        selectedKey = key;
        Row selected = byKey.get(key);
        scrollTarget = clamp(selected == null ? scrollOffset : selected.logicalY - viewportHeight / 2);
    }

    /** Wheel browsing moves the viewport independently of selection and Set expansion. */
    void scrollBy(float distance) {
        if (!Float.isFinite(distance) || distance == 0) return;
        float target = clamp(scrollTarget + distance);
        float travel = target - scrollTarget;
        scrollTarget = target;
        if (travel == 0) return;
        // Input impulses are distinct from viewport error. Reversal releases the previous impulse.
        if (Math.signum(travel) != Math.signum(scrollVelocity)) scrollVelocity = 0;
        float limit = rowHeight * VELOCITY_LIMIT_ROWS;
        scrollVelocity = Math.max(-limit, Math.min(limit, scrollVelocity + travel * 9));
    }

    /** Briefly retain hover across gaps; speed affects its strength, never selection. */
    void advance(float delta, String hitKey) {
        float dt = Float.isFinite(delta) ? Math.max(0, Math.min(2, delta)) : 0;
        if (hitKey != null && byKey.containsKey(hitKey)) { hoverKey = hitKey; hoverAbsence = 0; }
        else if ((hoverAbsence += dt) >= .075f) hoverKey = null;
        // Small bounded integration steps keep visual response consistent for 30/60/120 Hz.
        float remaining = dt;
        while (remaining > 0) {
            float step = Math.min(remaining, 1f / 120);
            scrollVelocity *= 1 - ease(step, VELOCITY_DECAY);
            float speed = Math.abs(scrollVelocity) / (rowHeight * VELOCITY_LIMIT_ROWS);
            float influence = Math.max(0, (speed - .055f) / .945f);
            velocityInfluence += (influence - velocityInfluence) * ease(step, 16);
            remaining -= step;
        }
        float hoverEase = ease(dt, 19), releaseEase = ease(dt, 13);
        float springDecay = (float) Math.exp(-SPRING_RATE * dt);
        if (dt > 0) {
            // Exact critically damped spring: soft starts, preserved momentum, no frame-rate tuning.
            float error = scrollOffset - scrollTarget;
            float momentum = viewportVelocity + SPRING_RATE * error;
            float nextOffset = scrollTarget + (error + momentum * dt) * springDecay;
            viewportVelocity = (viewportVelocity - SPRING_RATE * momentum * dt) * springDecay;
            scrollOffset = clamp(nextOffset);
            if (scrollOffset != nextOffset) viewportVelocity = 0;
        }
        Row hovered = byKey.get(hoverKey), selected = byKey.get(selectedKey);
        float hoverStrength = 1 - .9f * velocityInfluence;
        float selectionEase = ease(dt, 10), expansionEase = ease(dt, 9);
        for (Row row : rows) {
            float selectionSpace = selected == null ? 0 : Math.signum(selected.logicalY - row.logicalY) * rowHeight * .025f;
            float separation = hovered == null ? 0 : Math.signum(hovered.logicalY - row.logicalY) * rowHeight * .04f * hoverStrength;
            float down = visualDown(row);
            if ((down < -rowHeight * 2 || down > viewportHeight + rowHeight * 2)
                    && Math.abs(row.expansionY) < .01f && Math.abs(row.expansionVelocityY) < .01f && row.hoverAmount < .01f && row.selectedAmount < .01f
                    && row != selected && row != hovered) {
                // Settled off-screen rows need no easing work; keep their eventual spacing ready.
                row.hoverAmount = 0; row.selectedAmount = 0;
                row.groupAmount = row.entry.difficultyIndex() >= 0 ? 1 : 0;
                row.separationY = separation; row.selectionSeparationY = selectionSpace;
                row.expansionY = 0; row.expansionVelocityY = 0; row.expansionX = 0; row.revealAmount = 1;
                continue;
            }
            row.groupAmount += ((row.entry.difficultyIndex() >= 0 ? 1 : 0) - row.groupAmount) * expansionEase;
            boolean hover = row == hovered;
            row.hoverAmount += ((hover ? hoverStrength : 0) - row.hoverAmount) * (hover ? hoverEase : releaseEase);
            row.separationY += (separation - row.separationY) * releaseEase;
            row.selectedAmount += ((row == selected ? 1 : 0) - row.selectedAmount) * selectionEase;
            row.selectionSeparationY += (selectionSpace - row.selectionSeparationY) * selectionEase;
            // Match the viewport spring so expansion and centering cannot pull the selected row in opposite directions.
            float expansionMomentum = row.expansionVelocityY + SPRING_RATE * row.expansionY;
            row.expansionY = (row.expansionY + expansionMomentum * dt) * springDecay;
            row.expansionVelocityY = (row.expansionVelocityY - SPRING_RATE * expansionMomentum * dt) * springDecay;
            row.expansionX *= 1 - expansionEase;
            row.revealAmount += (1 - row.revealAmount) * expansionEase;
        }
    }

    private float visualDown(Row row) {
        return row.logicalY - scrollOffset + row.expansionY - row.separationY - row.selectionSeparationY;
    }
    float renderY(Row row, float top) { return top - visualDown(row) - rowHeight / 2; }
    float renderX(Row row, float width) {
        float normalized = (visualDown(row) - viewportHeight / 2) / (viewportHeight / 2);
        float distance = Math.min(1, Math.abs(normalized));
        // A gentle push at the center and pinch toward it near the ends, bounded to 5 UI units.
        float velocityOffset = (3 - 8 * distance) * velocityInfluence;
        return Math.max(width * .50f, Math.min(width * .70f,
                curveX(normalized, width) - width * .052f * row.groupAmount
                        - 3 * row.selectedAmount - 7 * row.hoverAmount
                        + velocityOffset + row.expansionX));
    }

    /** A smooth bounded arch, designed for osujava rather than sampled from another client. */
    static float curveX(float distance, float width) {
        float bounded = Float.isFinite(distance) ? Math.min(100, Math.abs(distance)) : 100;
        float squared = bounded * bounded;
        return width * (.61f + .065f * squared / (1 + squared));
    }

    static float skinRowHeight(float logicalWidth, float logicalHeight, float carouselWidth) {
        float aspect = logicalWidth / logicalHeight;
        if (!Float.isFinite(aspect) || aspect < 2.5f || aspect > 12) aspect = 6;
        return Math.max(68, Math.min(110, carouselWidth / aspect));
    }
    private static float validSize(float value, float fallback) {
        return Float.isFinite(value) && value > 0 ? Math.min(value, 10000) : fallback;
    }
    private float clamp(float value) { return Math.max(0, Math.min(maxScroll, value)); }
    private static float ease(float dt, float rate) { return (float) -Math.expm1(-dt * rate); }
}
