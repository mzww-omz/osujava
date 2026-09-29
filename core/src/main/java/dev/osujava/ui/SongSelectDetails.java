package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.HitObject;
import java.util.Locale;

/** Immutable selected metadata, built only when the selected Library objects change. */
record SongSelectDetails(String title, String mapper, String summary, String stats, String status) {
    static SongSelectDetails of(BeatmapSet set, BeatmapDifficulty diff, SongSelectRowPresentation.Stars rating) {
        String title = set.artist() + " - " + set.title() + " [" + diff.version() + "]";
        String mapper = "Mapped by " + diff.creator();
        long circles = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.CIRCLE).count();
        long sliders = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SLIDER).count();
        long spinners = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SPINNER).count();
        String bpm = bpmText(diff);
        String summary = "Length " + formatTime(Math.max(0, diff.timingStatistics().lengthMs())) + "    BPM " + bpm + "    Objects " + diff.hitObjects().size();
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
        double min = diff.timingStatistics().minimumBpm(), max = diff.timingStatistics().maximumBpm();
        if (max == 0) return "—";
        return min == max ? "" + (int) max : (int) min + "–" + (int) max + " (" + (int) diff.timingStatistics().commonBpm() + ")";
    }
}
