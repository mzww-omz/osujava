package dev.osujava.gameplay;

import java.util.Comparator;
import java.util.List;

/** LegacyFollowCircle transforms evaluated from immutable gameplay events, independent of render cadence. */
public final class FollowCircleAnimation {
    public static final double FOLLOW_SCALE = 2;
    public enum Kind { PRESS, RELEASE, TICK, BREAK, END }
    public record Event(Kind kind, double timeMs) { }
    public record Frame(double scale, double alpha) { }

    private FollowCircleAnimation() { }

    public static Frame at(List<Event> events, double now, double endTimeMs) {
        Transform scale = new Transform(1, 1, 0, 0, 0);
        Transform alpha = new Transform(0, 0, 0, 0, 0);
        // A successful tail schedules END at the actual end, even when judged early.
        for (Event event : events.stream().sorted(Comparator.comparingDouble(Event::timeMs)).toList()) {
            double t = event.timeMs();
            if (t > now) break;
            switch (event.kind()) {
                case PRESS -> {
                    double remaining = Math.max(0, endTimeMs - t);
                    scale = new Transform(1, 2, t, Math.min(180, remaining), 1);
                    alpha = new Transform(0, 1, t, Math.min(60, remaining), 0);
                }
                case RELEASE -> { /* LegacyFollowCircle.OnSliderRelease deliberately does nothing. */ }
                case TICK -> {
                    if (scale.at(t) >= 2) scale = new Transform(2.2, 2, t, 200, 0);
                }
                case BREAK -> {
                    scale = new Transform(scale.at(t), 4, t, 100, 0);
                    alpha = new Transform(alpha.at(t), 0, t, 100, 0);
                }
                case END -> {
                    scale = new Transform(scale.at(t), 1.6, t, 200, 1);
                    alpha = new Transform(alpha.at(t), 0, t, 200, 2);
                }
            }
        }
        return new Frame(scale.at(now), alpha.at(now));
    }

    private record Transform(double from, double to, double start, double duration, int easing) {
        double at(double now) {
            double p = GameplayVisualTiming.progress(now, start, duration);
            p = easing == 1 ? GameplayVisualTiming.easeOut(p) : easing == 2 ? p * p : p;
            return from + (to - from) * p;
        }
    }
}
