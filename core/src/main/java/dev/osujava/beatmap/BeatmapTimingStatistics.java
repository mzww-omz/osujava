package dev.osujava.beatmap;

import java.util.LinkedHashMap;
import java.util.List;

/** Library metadata from the file timeline, not ruleset-resolved gameplay durations. */
public record BeatmapTimingStatistics(int lengthMs, int drainSeconds,
                                     double minimumBpm, double maximumBpm, double commonBpm) {
    public int lengthSeconds() { return lengthMs / 1000; }

    /** Used by in-memory charts without file events; imported charts supply their break total. */
    public static BeatmapTimingStatistics fromObjects(List<TimingPoint> points, List<HitObject> objects) {
        if (objects.isEmpty()) return calculate(points, -1, -1, -1, 0);
        HitObject first = objects.getFirst(), last = objects.getLast();
        return calculate(points, milliseconds(first.timeMs()), milliseconds(last.timeMs()),
                milliseconds(last.endTimeMs()), 0);
    }

    /**
     * Stable's library scan (06003c77): sliders end at their start for these statistics.
     * Spinner end and hold end are supplied by the importer. Breaks are summed, not merged.
     */
    public static BeatmapTimingStatistics calculate(List<TimingPoint> points, int firstStartMs,
                                                    int lastStartMs, int lastEndMs, long breakMs) {
        long drain = Math.max(((long) lastStartMs - firstStartMs - breakMs) / 1000,
                ((long) lastEndMs - firstStartMs - breakMs) / 1000);
        double minBeat = Double.POSITIVE_INFINITY, maxBeat = 0, beat = 0;
        double segmentEnd = lastEndMs;
        var durations = new LinkedHashMap<Double, Long>();
        // 06003c96: aggregate durations by exact beat length, scanning from the last red line.
        // The first point extends to time zero. Inherited points do not split tempo segments.
        for (int i = points.size() - 1; i >= 0; i--) {
            TimingPoint point = points.get(i);
            if (point.uninherited()) beat = point.beatLength();
            if (!Double.isFinite(beat) || beat <= 0 || !Double.isFinite(point.timeMs())
                    || point.timeMs() > segmentEnd || !point.uninherited() && i > 0) continue;
            minBeat = Math.min(minBeat, beat);
            maxBeat = Math.max(maxBeat, beat);
            durations.merge(beat, (long) (segmentEnd - (i == 0 ? 0 : point.timeMs())), Long::sum);
            segmentEnd = point.timeMs();
        }
        long longest = 0;
        double commonBeat = 0;
        for (var duration : durations.entrySet()) {
            if (duration.getValue() > longest) {
                longest = duration.getValue();
                commonBeat = duration.getKey();
            }
        }
        return new BeatmapTimingStatistics(lastEndMs, (int) Math.clamp(drain, Integer.MIN_VALUE, Integer.MAX_VALUE),
                bpm(maxBeat), bpm(minBeat), bpm(commonBeat));
    }

    /** Native library times are int32 milliseconds, with fractional times truncated. */
    public static int milliseconds(double value) {
        return (int) value;
    }

    private static double bpm(double beatLength) {
        return beatLength > 0 && Double.isFinite(beatLength) ? Math.rint(60000 / beatLength) : 0;
    }
}
