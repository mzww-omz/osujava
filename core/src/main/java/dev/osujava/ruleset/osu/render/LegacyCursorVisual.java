// Cursor algorithms adapted from osu!lazer and osu-framework, copyright ppy Pty Ltd.
// MIT licence: docs/licenses/ppy-MIT.txt.
package dev.osujava.ruleset.osu.render;

import dev.osujava.gameplay.GameplaySession.PointerState;
import dev.osujava.skin.SkinConfiguration.Cursor;

import java.util.ArrayDeque;
import java.util.List;

/** Visual-only consumer of timestamped Session input. Coordinates are the same osu! space as input.
 * Mirrors LegacyCursor and LegacyCursorTrail; no gameplay writes or wall-clock reads.
 */
public final class LegacyCursorVisual {
    public static final int MAX_PARTS = 2048;
    public static final double DISJOINT_INTERVAL_MS = 1000.0 / 60;
    public static final float MAGIC_SCALE = 1.6f;
    private final Cursor config;
    private final boolean skinCursor, middle, trail;
    private final float trailWidth;
    private final ArrayDeque<Part> parts = new ArrayDeque<>();
    private double time = Double.NaN, x, y, lastX, lastY, emissionEpoch;
    private long emittedSlots;
    private double transitionTime = Double.NEGATIVE_INFINITY;
    private float fromScale = 1, toScale = 1;
    private boolean positioned, trailPositioned, pressed;
    private final Resampler resampler = new Resampler();

    public LegacyCursorVisual(Cursor config, boolean skinCursor, boolean middle, float trailLogicalWidth) {
        this.config = config;
        this.skinCursor = skinCursor;
        this.middle = skinCursor && middle;
        this.trail = trailLogicalWidth > 0 && Float.isFinite(trailLogicalWidth);
        this.trailWidth = trailLogicalWidth / MAGIC_SCALE;
    }

    /** Observe after forwarding an input API call. pressed is read from Session, never inferred here. */
    public void input(double now, PointerState pointer, boolean pressEvent) {
        if (pointer == null) return;
        move(now, pointer.x(), pointer.y());
        if (pressEvent && pointer.pressed() && config.expand()) {
            // LegacyCursor.Expand explicitly restarts from 1 on each press.
            transitionTime = now; fromScale = 1; toScale = 1.3f;
        } else if (pressed && !pointer.pressed()) {
            fromScale = expandedScale(now); toScale = 1; transitionTime = now;
        }
        pressed = pointer.pressed();
    }

    public void move(double now, double newX, double newY) {
        advance(now);
        boolean changed = !positioned || newX != x || newY != y;
        x = newX; y = newY;
        if (!positioned) { emissionEpoch = now; emittedSlots = 0; }
        positioned = true;
        if (!trail || disjoint() || !changed) return;
        if (!trailPositioned) {
            lastX = x; lastY = y; trailPositioned = true;
            resampler.accept(x, y);
            return;
        }
        if (!resampler.accept(x, y)) return;
        double dx = x - lastX, dy = y - lastY, distance = Math.hypot(dx, dy);
        double interval = interval();
        // CursorTrail leaves one full interval clear near the cursor, with a strict < endpoint.
        long count = Math.max(0, (long) Math.ceil((distance - interval) / interval) - 1);
        double startX = lastX, startY = lastY;
        // Retain only the newest 2048 even for a huge input jump; work is bounded too.
        for (long i = Math.max(1, count - MAX_PARTS + 1); i <= count; i++) {
            lastX = startX + dx / distance * interval * i;
            lastY = startY + dy / distance * interval * i;
            add(now, lastX, lastY);
        }
    }

    public void advance(double now) {
        if (!Double.isFinite(now)) throw new IllegalArgumentException("Cursor time must be finite");
        if (!Double.isNaN(time) && now < time) {
            // A seek invalidates mouse history. A fixed-clock replay supplies that history again.
            parts.clear(); positioned = trailPositioned = pressed = false;
            resampler.reset(); fromScale = toScale = 1; transitionTime = Double.NEGATIVE_INFINITY;
        }
        time = now;
        prune(now);
        if (trail && disjoint() && positioned) {
            // Integer slots give the exact same timestamps at every render/event frequency.
            long due = (long) Math.floor((now - emissionEpoch) / DISJOINT_INTERVAL_MS + 1e-9);
            int visibleSlots = (int) Math.ceil(fadeDuration() / DISJOINT_INTERVAL_MS);
            long first = Math.max(emittedSlots + 1, due - visibleSlots + 1);
            for (long slot = first; slot <= due; slot++) add(emissionEpoch + slot * DISJOINT_INTERVAL_MS, x, y);
            emittedSlots = due;
            prune(now);
        }
    }

    private void add(double now, double px, double py) {
        if (parts.size() == MAX_PARTS) parts.removeFirst();
        parts.addLast(new Part(px, py, now, expandedScale(now)));
    }
    private void prune(double now) {
        while (!parts.isEmpty() && now - parts.getFirst().createdMs() >= fadeDuration()) parts.removeFirst();
    }
    public boolean disjoint() { return !middle; }
    public double fadeDuration() { return disjoint() ? 150 : 500; }
    public double interval() { return trailWidth / 2.5; } // User cursor scale=1; multiplier=1/max(1,1).
    public boolean trailCentered() { return !disjoint() || config.centre(); }
    public float rotation(double now) {
        return skinCursor && config.rotate() ? (float) (((now % 10000 + 10000) % 10000) * 360 / 10000) : 0;
    }
    public float trailRotation(double now) { return config.trailRotate() ? rotation(now) : 0; }
    public float expandedScale(double now) {
        if (!config.expand()) return 1;
        if (!Double.isFinite(transitionTime)) return toScale;
        double p = Math.max(0, Math.min(1, (now - transitionTime) / 100));
        return (float) (fromScale + (toScale - fromScale) * p * (2 - p));
    }
    public Piece cursor(double now) {
        return new Piece(x, y, skinCursor ? config.centre() : true, expandedScale(now), rotation(now));
    }
    public Piece middle() { return new Piece(x, y, config.centre(), 1, 0); }
    public boolean positioned() { return positioned; }
    public List<Part> parts() { return List.copyOf(parts); }
    public record Piece(double x, double y, boolean centred, float scale, float rotation) { }
    public record Part(double x, double y, double createdMs, float scale) {
        public float alpha(double now, double fadeDuration) {
            return (float) Math.max(0, Math.min(1, 1 - (now - createdMs) / fadeDuration));
        }
    }

    /** osu-framework InputResampler: preserve HD/raw input; reduce integer nodes to corners. */
    private static final class Resampler {
        private boolean raw, hasPosition;
        private double relevantX, relevantY, actualX, actualY;
        boolean accept(double x, double y) {
            if (raw) { store(x, y); return true; }
            if (x != Math.floor(x)) raw = true;
            if (!hasPosition) { store(x, y); return true; }
            double dx = x - relevantX, dy = y - relevantY, distance = Math.hypot(dx, dy);
            double rx = x - actualX, ry = y - actualY, movement = Math.hypot(rx, ry);
            if (movement < 1) return false;
            actualX = x; actualY = y;
            if (distance < 2 || distance < 10 && (dx * rx + dy * ry) / (distance * movement) > .7) return false;
            relevantX = x; relevantY = y; return true;
        }
        private void store(double x, double y) {
            relevantX = actualX = x; relevantY = actualY = y; hasPosition = true;
        }
        void reset() { raw = hasPosition = false; }
    }
}
