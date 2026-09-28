package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;
import java.util.Locale;

/** Immutable selected metadata, built only when the selected Library objects change. */
record SongSelectDetails(String title, String mapper, String summary, String stats, String status) {
    static SongSelectDetails of(BeatmapSet set, BeatmapDifficulty diff, SongSelectRowPresentation.Stars rating) {
        String title = set.artist() + " - " + set.title() + " [" + diff.version() + "]";
        String mapper = "Mapped by " + diff.creator();
        long circles = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.CIRCLE).count();
        long sliders = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SLIDER).count();
        long spinners = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SPINNER).count();
        long firstMs = diff.hitObjects().stream().mapToLong(o -> (long) o.timeMs()).min().orElse(0);
        long lastMs = diff.hitObjects().stream().mapToLong(o -> (long) o.endTimeMs()).max().orElse(0);
        String bpm = bpmText(diff);
        String summary = "Length " + formatTime(Math.max(0, lastMs - firstMs)) + "    BPM " + bpm + "    Objects " + diff.hitObjects().size();
        String status = "Local beatmap" + (rating.present() ? "    Stars " + rating.label() : "");
        String stats = "Circles " + circles + "   Sliders " + sliders + "   Spinners " + spinners
                + "    OD " + oneDecimal(diff.settings().overallDifficulty())
                + "   AR " + oneDecimal(diff.settings().approachRate())
                + "   CS " + oneDecimal(diff.settings().circleSize())
                + "   HP " + oneDecimal(diff.settings().hpDrainRate());
        return new SongSelectDetails(title, mapper, summary, stats, status);
    }

    private static String oneDecimal(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String formatTime(long ms) { return (ms / 60000) + ":" + String.format(Locale.ROOT, "%02d", (ms / 1000) % 60); }
    private static String bpmText(BeatmapDifficulty diff) {
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (TimingPoint point : diff.timingPoints()) if (point.uninherited() && point.beatLength() > 0) {
            double bpm = 60000 / point.beatLength();
            min = Math.min(min, bpm); max = Math.max(max, bpm);
        }
        if (max == 0) return "—";
        return Math.round(min) == Math.round(max) ? "" + Math.round(max) : Math.round(min) + "–" + Math.round(max);
    }
}
