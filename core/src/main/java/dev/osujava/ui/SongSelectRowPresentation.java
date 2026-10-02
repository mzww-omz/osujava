package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import java.nio.file.Path;
import java.util.Locale;
import java.util.OptionalDouble;

/** Cached content and pure row geometry. Does not own selection, motion or rating calculation. */
final class SongSelectRowPresentation {
    enum Tone { SELECTED, SIBLING, PLAYED, UNPLAYED }
    static Tone tone(boolean selected, boolean sibling, boolean played) {
        return selected ? Tone.SELECTED : sibling ? Tone.SIBLING : played ? Tone.PLAYED : Tone.UNPLAYED;
    }
    record Content(String title, String byline, String detail, Path thumbnail, Stars stars, int mode) { }
    record Geometry(float thumbnailX, float thumbnailY, float thumbnailWidth, float thumbnailHeight,
                    float textX, float textWidth, float titleY, float bylineY, float detailY, float starsY,
                    float secondaryX, float modeX, float modeY, float gradeX, float gradeY) { }

    static Content content(BeatmapSet set, BeatmapDifficulty difficulty, OptionalDouble rating) {
        boolean child = difficulty != null;
        Path background = child && difficulty.backgroundPath() != null ? difficulty.backgroundPath() : set.backgroundPath();
        return new Content(child ? difficulty.title() : set.title(), (child ? difficulty.artist() : set.artist()) + " // "
                + (child ? difficulty.creator() : set.creator()),
                child ? difficulty.version() : "",
                background, Stars.of(child ? rating : OptionalDouble.empty()),
                child ? difficulty.mode() : commonMode(set));
    }

    private static int commonMode(BeatmapSet set) {
        if (set.difficulties().isEmpty()) return -1;
        int mode = set.difficulties().getFirst().mode();
        return set.difficulties().stream().allMatch(d -> d.mode() == mode) ? mode : -1;
    }

    static Geometry geometry(float width, float height, float visibleWidth, boolean thumbnails) {
        return geometry(width, height, visibleWidth, thumbnails, true, false, false, false);
    }

    /** 06000fbf/0fc2/0fe4: text shifts with state; thumbnail and badge origins stay fixed. */
    static Geometry geometry(float width, float height, float visibleWidth, boolean thumbnails,
                             boolean expanded, boolean cropped, boolean mode, boolean grade) {
        // Stable 0fbf sets thumbnail scale to 1.425 (half for the larger cache).
        // 0fdc exposes the 114x85.5 envelope. Row pitch is independently 48/480.
        float canvasScale = height / (48 * 1.6f);
        float inset = 5.2f * height / 48;
        float thumbHeight = thumbnails ? 85.5f * canvasScale : 0;
        float thumbWidth = thumbnails ? 114 * canvasScale : 0;
        float referenceScale = height / 48;
        float styleInset = cropped ? 15 : 5;
        float column = thumbnails ? 75 : 5;
        float textX = (column + styleInset + (expanded && (mode || grade) ? 20 : 3)) * referenceScale;
        float centreY = height / 2 - (cropped ? -3 : 0) * referenceScale;
        return new Geometry(inset, (height - thumbHeight) / 2 - .25f * height / 48, thumbWidth, thumbHeight,
                textX, Math.max(0, Math.min(width, visibleWidth) - textX - 16),
                centreY + 16 * referenceScale, centreY + 4 * referenceScale,
                centreY - 7 * referenceScale, centreY - 18 * referenceScale,
                textX + referenceScale, (column + styleInset + 1) * referenceScale, centreY + 13 * referenceScale,
                (column + styleInset - 1) * referenceScale, centreY - (mode ? 14 : 0) * referenceScale);
    }

    /** Stable 06000fd2 creates ten background/foreground pairs; 06000fbd caps fill at ten. */
    record Stars(int count, float lastFill, String label, double rating) {
        private static final int SLOT_COUNT = 10;
        static Stars of(OptionalDouble value) {
            if (value == null || value.isEmpty() || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0)
                return new Stars(0, 0, "", -1);
            double rating = value.getAsDouble();
            int count = Math.max(1, (int) Math.ceil(Math.min(SLOT_COUNT, rating)));
            float last = rating >= SLOT_COUNT ? 1 : (float) (rating - (count - 1));
            String label = rating >= 100 ? String.format(Locale.ROOT, "%.2g", rating)
                    : String.format(Locale.ROOT, "%.2f", rating);
            return new Stars(count, last, label, rating);
        }
        boolean present() { return count > 0; }
        float fill(int index) { return index < 0 || index >= count ? 0 : index == count - 1 ? lastFill : 1; }
        int slots() { return present() ? SLOT_COUNT : 0; }
    }
}
