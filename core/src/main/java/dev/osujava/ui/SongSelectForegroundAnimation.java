package dev.osujava.ui;

/** Row-owned opacity/thumbnail state from stable 06000fd2/1be8/0fdc. No texture or clock ownership. */
final class SongSelectForegroundAnimation {
    record Snapshot(float baseOpacity, float detailOpacity, float thumbnailOpacity, int thumbnailBrightness) {
        static final Snapshot VISIBLE = new Snapshot(1, 1, 1, 255);
    }

    private final Linear base = new Linear(0), detail = new Linear(0), thumbnail = new Linear(0);
    private final Linear brightness = new Linear(50);
    private int state;
    private long lastRequest, requestAge;
    private boolean requested, loaded;

    void update(int next, boolean instant, long now, int frameMs) {
        if (next != state) {
            // The base prefix contains the background, title, byline and thumbnail.
            if (next == 0 && base.current != 0) {
                base.move(0, instant ? 0 : 200, now, frameMs);
                detail.move(0, instant ? 0 : 200, now, frameMs);
                thumbnail.move(0, instant ? 0 : 200, now, frameMs);
            } else if (state == 0 && next > 0) {
                base.reset(instant ? 0 : 200, now, frameMs);
                thumbnail.reset(instant ? 0 : 200, now, frameMs);
                if (next >= 2) detail.reset(instant ? 0 : 200, now, frameMs);
            }
            if ((state >= 2) != (next >= 2)) detail.move(next >= 2 ? 1 : 0, instant ? 0 : 300, now, frameMs);
            if ((state >= 3) != (next >= 3)) {
                if (next >= 3) thumbnail.reset(instant ? 0 : 1000, now, frameMs);
                brightness.move(next >= 3 ? 255 : 50, instant ? 0 : 300, now, frameMs);
            }
            state = next;
        }
        base.advance(now); detail.advance(now); thumbnail.advance(now); brightness.advance(now);
        // The sprite colour is stored as bytes after evaluating a colour transform.
        brightness.current = (int) brightness.current;
    }

    /** 06001be8 counts consecutive draw requests; a gap of 200ms or more resets the delay. */
    boolean requestThumbnail(long now) {
        if (requested) return true;
        requestAge = lastRequest > 0 && now - lastRequest < 200 ? requestAge + now - lastRequest : 0;
        lastRequest = now;
        requested = requestAge >= (state >= 3 ? 100 : 500);
        return requested;
    }

    /** The row's one-shot load callback replaces an outstanding 1000ms fade with 400ms from zero. */
    void thumbnailLoaded(long now, int frameMs) {
        if (loaded) return;
        loaded = true;
        thumbnail.reset(400, now, frameMs);
        // Loading occurs after transform evaluation in the native draw pass; this frame remains at zero.
    }

    Snapshot snapshot() { return new Snapshot(base.current, detail.current, thumbnail.current, (int) brightness.current); }

    private static final class Linear {
        float current, from, to;
        long start, end;
        Linear(float value) { current = from = to = value; }
        void reset(int duration, long now, int frameMs) {
            current = 0;
            set(1, duration, now, frameMs);
        }
        void move(float target, int duration, long now, int frameMs) {
            set(target, duration, now, frameMs);
            if (duration == 0) current = from = to;
        }
        private void set(float target, int duration, long now, int frameMs) {
            from = current; to = target; start = now - frameMs; end = now + duration;
        }
        void advance(long now) {
            if (now > end) current = to;
            else if (now <= start) current = from;
            else current = from + (to - from) * (float) (now - start) / (end - start);
        }
    }
}
