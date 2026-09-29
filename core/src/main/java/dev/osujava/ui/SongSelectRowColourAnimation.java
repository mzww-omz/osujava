package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;

/** One resident background sprite's colour transform (stable 060040c1/40c2/2b50). */
final class SongSelectRowColourAnimation {
    private int state = -1, base, target, current, from, to;
    private long start, end;
    private boolean focused, hovered, active, flash;
    private final Color tint = new Color();

    int rgba() { return current; }

    /** Events precede sprite evaluation; interruptions therefore start at the last displayed colour. */
    void update(int nextState, int nextBase, boolean focus, boolean hover, long now, int frameMs) {
        if (state < 0) {
            current = target = base = nextBase;
        } else if (state != nextState || base != nextBase) {
            target = nextBase;
            transition(target, 300, now, frameMs);
        }
        base = nextBase;
        state = nextState;
        if (focus != focused) {
            Color.rgba8888ToColor(tint, base);
            if (focus) SongSelectRowColours.focusTint(tint, 1);
            target = Color.rgba8888(tint);
            transition(target, 50, now, frameMs);
        }
        if (hover && !hovered) {
            Color.rgba8888ToColor(tint, target);
            SongSelectRowColours.hoverTint(tint, 1);
            int highlight = Color.rgba8888(tint);
            // A running ordinary colour transform blocks the flash. Rehover keeps its original return colour.
            if (current != highlight && (!active || flash))
                begin(highlight, active ? to : current, 1000, true, now, frameMs);
        }
        focused = focus;
        hovered = hover;
        if (!active) return;
        if (now >= end) current = to;
        else {
            double progress = (double) (now - start) / (end - start);
            int result = 0;
            for (int shift = 24; shift >= 0; shift -= 8) {
                int a = from >>> shift & 255, b = to >>> shift & 255;
                // Native clamps and truncates each interpolated RGBA byte, including alpha.
                result |= (int) Math.max(0, Math.min(255, a + (b - a) * progress)) << shift;
            }
            current = result;
        }
        // 060040b0 removes expired transforms strictly after their end time.
        if (now > end) active = false;
    }

    private void transition(int colour, int duration, long now, int frameMs) {
        if (current == colour && !active) return;
        begin(current, colour, duration, false, now, frameMs);
    }

    private void begin(int first, int last, int duration, boolean isFlash, long now, int frameMs) {
        from = first; to = last;
        start = now - frameMs; end = now + duration;
        flash = isFlash; active = true;
    }
}
