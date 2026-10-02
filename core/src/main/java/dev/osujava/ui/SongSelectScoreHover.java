package dev.osujava.ui;

import java.util.Arrays;
import java.util.List;

/** Bounded visible-slot animation; score storage and the renderer never advance it. */
final class SongSelectScoreHover {
    record Snapshot(float[] amounts) {
        float alpha(int slot) { return .3f + .3f * (slot < amounts.length ? amounts[slot] : 0); }
    }
    private List<ScoreBrowserModel.Row> rows;
    private int first;
    private float[] amounts = new float[0];

    void advance(float delta, List<ScoreBrowserModel.Row> next, int first, int capacity, int hoveredSlot) {
        int count = Math.max(0, Math.min(capacity, next.size() - first));
        if (rows != next || this.first != first || amounts.length != count) {
            amounts = new float[count]; rows = next; this.first = first;
        }
        // Stable 060013b4: linear .3 -> .6 over 200ms.
        float step = Float.isFinite(delta) ? Math.max(0, delta) / .2f : 0;
        for (int i = 0; i < count; i++) amounts[i] = i == hoveredSlot
                ? Math.min(1, amounts[i] + step) : Math.max(0, amounts[i] - step);
    }
    Snapshot snapshot() { return new Snapshot(Arrays.copyOf(amounts, amounts.length)); }
}
