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
                    float textX, float textWidth, float titleY, float bylineY, float detailY, float starsY) { }

    static Content content(BeatmapSet set, BeatmapDifficulty difficulty, OptionalDouble rating) {
        boolean child = difficulty != null;
        Path background = child && difficulty.backgroundPath() != null ? difficulty.backgroundPath() : set.backgroundPath();
        return new Content(set.title(), set.artist() + " // "
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
        return geometry(width, height, visibleWidth, thumbnails, 0);
    }

    /** The mode/grade column sits between thumbnail and text. */
    static Geometry geometry(float width, float height, float visibleWidth, boolean thumbnails, float gradeWidth) {
        // Stable 0fbf sets thumbnail scale to 1.425 (half for the larger cache).
        // 0fdc exposes the 114x85.5 envelope. Row pitch is independently 48/480.
        float canvasScale = height / (48 * 1.6f);
        float inset = 5.2f * height / 48;
        float thumbHeight = thumbnails ? 85.5f * canvasScale : 0;
        float thumbWidth = thumbnails ? 114 * canvasScale : 0;
        float textX = thumbnails ? inset + thumbWidth + height * .13f : height * .20f;
        textX += Math.max(0, gradeWidth);
        return new Geometry(inset, (height - thumbHeight) / 2 - .25f * height / 48, thumbWidth, thumbHeight,
                textX, Math.max(0, Math.min(width, visibleWidth) - textX - 16),
                height - 16, height - 31, height - 48, height - 66);
    }

    /** A bounded nine-slot star band; extreme ratings retain saturated icons and an honest label. */
    record Stars(int count, float lastFill, String label) {
        static Stars of(OptionalDouble value) {
            if (value == null || value.isEmpty() || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0)
                return new Stars(0, 0, "");
            double rating = value.getAsDouble();
            int count = Math.max(1, (int) Math.ceil(Math.min(9, rating)));
            float last = rating >= 9 ? 1 : (float) (rating - (count - 1));
            String label = rating >= 100 ? String.format(Locale.ROOT, "%.2g", rating)
                    : String.format(Locale.ROOT, "%.2f", rating);
            return new Stars(count, last, label);
        }
        boolean present() { return count > 0; }
        float fill(int index) { return index >= count ? 0 : index == count - 1 ? lastFill : 1; }
        int slots() { return count; }
        float numericWidth() { return label.length() > 5 ? 64 : 42; }
        float width() { return present() ? slots() * 18 + numericWidth() : 0; }
        StarLayout layout(float available) {
            float width = Float.isFinite(available) ? Math.max(0, available) : 0;
            float numberWidth = Math.min(width, numericWidth());
            float step = count == 0 ? 0 : Math.min(18, (width - numberWidth) / count);
            // Preserve the numerical rating when there is no room for readable icons.
            int icons = step >= 6 ? count : 0;
            return new StarLayout(icons, icons == 0 ? 0 : step, numberWidth);
        }
    }
    record StarLayout(int icons, float step, float numberWidth) {
        float numberX() { return icons * step; }
        float size() { return step * 15 / 18; }
    }
}
