package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import java.nio.file.Path;
import java.util.Locale;
import java.util.OptionalDouble;

/** Cached content and pure row geometry. Does not own selection, motion or rating calculation. */
final class SongSelectRowPresentation {
    record Content(String title, String byline, String detail, Path thumbnail, Stars stars) { }
    record Geometry(float thumbnailX, float thumbnailY, float thumbnailWidth, float thumbnailHeight,
                    float textX, float textWidth, float titleY, float bylineY, float detailY) { }

    static Content content(BeatmapSet set, BeatmapDifficulty difficulty, OptionalDouble rating) {
        boolean child = difficulty != null;
        Path background = child && difficulty.backgroundPath() != null ? difficulty.backgroundPath() : set.backgroundPath();
        return new Content(set.title(), set.artist() + "  /  "
                + (child ? difficulty.creator() : set.creator()),
                child ? difficulty.version() : set.difficulties().size() + (set.difficulties().size() == 1 ? " difficulty" : " difficulties"),
                background, Stars.of(child ? rating : OptionalDouble.empty()));
    }

    static Geometry geometry(float width, float height, float visibleWidth, boolean modernRatio) {
        float thumbHeight = Math.min(70, height - 18);
        float thumbWidth = thumbHeight * (modernRatio ? 115f / 85 : 96f / 70);
        float textX = 12 + thumbWidth + 16;
        return new Geometry(12, (height - thumbHeight) / 2, thumbWidth, thumbHeight,
                textX, Math.max(0, Math.min(width, visibleWidth) - textX - 16),
                height / 2 + 17, height / 2 - 1, height / 2 - 23);
    }

    /** Up to seven literal full/partial stars; above that, a single icon with a numeric label. */
    record Stars(int count, float lastFill, String label) {
        static Stars of(OptionalDouble value) {
            if (value == null || value.isEmpty() || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0)
                return new Stars(0, 0, "");
            double rating = value.getAsDouble();
            int count = rating > 7 ? 1 : Math.max(1, (int) Math.ceil(rating));
            float last = rating > 7 ? 1 : (float) (rating - (count - 1));
            String label = rating >= 100 ? String.format(Locale.ROOT, "%.2g", rating)
                    : String.format(Locale.ROOT, "%.2f", rating);
            return new Stars(count, last, label);
        }
        boolean present() { return count > 0; }
        float fill(int index) { return index == count - 1 ? lastFill : 1; }
        float numericWidth() { return label.length() > 5 ? 64 : 42; }
        float width() { return present() ? count * 12 + numericWidth() : 0; }
    }
}
