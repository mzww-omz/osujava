package dev.osujava.ui;

import dev.osujava.beatmap.TimingPoint;
import java.util.List;

/** Reads imported control points; neither parses beatmaps nor changes gameplay timing. */
public final class MenuBeatTiming {
    private MenuBeatTiming() { }
    public record Beat(double positionMs, double originMs, double lengthMs, long index, double phase, boolean kiai) { }
    public static Beat at(List<TimingPoint> points, double positionMs, boolean audioAvailable) {
        double origin = 0, length = audioAvailable ? 500 : 1000;
        boolean kiai = false;
        if (audioAvailable) {
            TimingPoint first = null, current = null, effect = null;
            for (TimingPoint p : points) {
                if (!Double.isFinite(p.timeMs())) continue;
                if (p.uninherited() && p.beatLength() > 0 && Double.isFinite(p.beatLength())) {
                    if (first == null || p.timeMs() < first.timeMs()) first = p;
                    if (p.timeMs() <= positionMs && (current == null || p.timeMs() >= current.timeMs())) current = p;
                }
                if (p.timeMs() <= positionMs && (effect == null || p.timeMs() >= effect.timeMs())) effect = p;
            }
            if (current == null) current = first;
            if (current != null) { origin = current.timeMs(); length = current.beatLength(); }
            kiai = effect != null && (effect.effects() & 1) != 0;
        }
        double beats = (positionMs - origin) / length;
        long index = (long) Math.floor(beats);
        return new Beat(positionMs, origin, length, index, beats - index, kiai);
    }
}
