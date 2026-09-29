package dev.osujava.ui;

import java.util.ArrayList;
import java.util.List;

/** The ten sprite pairs from stable 06000fd2/0fbd; no texture ownership or rating calculation. */
final class SongSelectStarAnimation {
    record Glyph(float scale, float crop) { }
    record Snapshot(boolean cropped, float backgroundOpacity, float foregroundOpacity, List<Glyph> glyphs) {
        static final Snapshot EMPTY = new Snapshot(false, 0, 0, List.of());
        Snapshot { glyphs = List.copyOf(glyphs); }
    }

    private final boolean cropped;
    private final int width;
    private final Scalar[] stars = new Scalar[10];
    private final Scalar background = new Scalar(0), foreground = new Scalar(1);
    private double rating = -1;
    private boolean retired;
    private long retirementEnd;

    SongSelectStarAnimation(boolean cropped, int logicalWidth) {
        this.cropped = cropped;
        width = Math.max(1, logicalWidth);
        for (int i = 0; i < stars.length; i++) stars[i] = new Scalar(cropped ? -i * width : 0);
    }

    /** 06000fbf tests the row BACKGROUND provider, independently of the star texture's provider. */
    static boolean cropped(double skinVersion, boolean builtInBackground) { return skinVersion < 2.2 && !builtInBackground; }

    void update(SongSelectRowPresentation.Stars value, boolean instant, long now, int frameMs) {
        double next = value.present() ? Math.min(10, value.rating()) : -1;
        if (!retired && next != rating) {
            double previous = rating;
            rating = next;
            background.move(next < 0 ? 0 : 1, now - frameMs, now + (instant ? 0 : cropped ? 1000 : 600), 0, instant);
            double amount = Math.max(0, next);
            for (int i = 0; i < stars.length; i++) {
                if (cropped) {
                    stars[i].move((int) (width * (amount - i)), now - frameMs, now + (instant ? 0 : 500), 7, false);
                } else {
                    float target = amount > i ? .6f * (float) Math.max(.5, Math.min(1, amount - i) + i * .04f) : 0;
                    int relative = (int) Math.floor(i - Math.min(amount, previous));
                    int order = previous > amount ? (int) Math.floor(previous - amount) - relative - 1 : relative;
                    long start = now + order * 80L + 50;
                    stars[i].move(target, instant ? now - frameMs : start, instant ? now : start + 500, instant ? 0 : 30, false);
                }
            }
        }
        advance(now);
    }

    /** Removed pairs keep their existing scale/crop transform while their opacity fades out. */
    void retire(long now, int frameMs) {
        retired = true;
        retirementEnd = now + 300;
        background.move(0, now - frameMs, retirementEnd, 0, false);
        foreground.move(0, now - frameMs, retirementEnd, 0, false);
    }

    boolean finished(long now) { return retired && now > retirementEnd; }

    void advance(long now) {
        background.advance(now); foreground.advance(now);
        for (var star : stars) {
            star.advance(now);
            // Sprite width is an integer; scale remains float (060040b0).
            if (cropped) star.current = (int) star.current;
        }
    }

    Snapshot snapshot() {
        var glyphs = new ArrayList<Glyph>(10);
        for (var star : stars) glyphs.add(cropped
                ? new Glyph(1, Math.max(0, Math.min(1, star.current / width))) : new Glyph(star.current, 1));
        return new Snapshot(cropped, background.current, foreground.current, glyphs);
    }

    /** Native easing IDs 0=linear, 7=out cubic, 30=out back (06002b54). */
    private static final class Scalar {
        float current, from, to;
        long start, end;
        int easing;
        Scalar(float value) { current = from = to = value; }
        void move(float target, long begin, long finish, int curve, boolean instant) {
            from = current; to = target; start = begin; end = finish; easing = curve;
            if (instant) { current = from = to; end = start; }
        }
        void advance(long now) {
            if (now > end) { current = to; return; }
            if (now <= start) { current = from; return; }
            double t = (double) (now - start) / (end - start), p = t - 1;
            double amount = switch (easing) {
                case 7 -> 1 + p * p * p;
                case 30 -> 1 + p * p * (2.70158 * p + 1.70158);
                default -> t;
            };
            current = (float) (from + (to - from) * amount);
        }
    }
}
