package dev.osujava.ruleset.osu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Generates tick, repeat, and tail times for one Slider using its beatmap-clock timing. */
public final class SliderEventGenerator {
    private static final double TAIL_LENIENCY_MS = 36;
    private static final int MAX_EVENTS = 100_000;

    private SliderEventGenerator() {
    }

    public static List<SliderEvent> generate(SliderTiming timing, SliderPath path) {
        List<SliderEvent> events = new ArrayList<>();
        double pathLength = Math.min(100_000, path.distance());
        double tickDistance = timing.tickDistance();
        for (int span = 0; span < timing.spanCount() && events.size() < MAX_EVENTS; span++) {
            double spanStart = timing.startTimeMs() + span * timing.spanDurationMs();
            boolean reversed = span % 2 == 1;
            if (timing.ticksEnabled() && pathLength > 0 && tickDistance > 0) {
                double minDistanceFromEnd = timing.velocity() * 10;
                for (double distance = tickDistance; distance < pathLength - minDistanceFromEnd; distance += tickDistance) {
                    double progress = distance / pathLength;
                    double timeProgress = reversed ? 1 - progress : progress;
                    events.add(new SliderEvent(SliderEvent.Type.TICK, span,
                            spanStart + timeProgress * timing.spanDurationMs(), progress));
                    if (events.size() >= MAX_EVENTS) break;
                }
            }
            if (span < timing.spanCount() - 1 && events.size() < MAX_EVENTS) {
                events.add(new SliderEvent(SliderEvent.Type.REPEAT, span,
                        spanStart + timing.spanDurationMs(), (span + 1) % 2));
            }
        }
        double tailTime = timing.endTimeMs();
        events.add(new SliderEvent(SliderEvent.Type.TAIL, timing.spanCount() - 1, tailTime,
                timing.endProgress()));
        events.sort(Comparator.comparingDouble(SliderEvent::timeMs)
                .thenComparingInt(event -> event.type() == SliderEvent.Type.REPEAT ? 0
                        : event.type() == SliderEvent.Type.TICK ? 1 : 2));
        return List.copyOf(events);
    }

    /** Earliest time at which lazer accepts tracking judgement for the Slider tail. */
    public static double tailJudgementStartTime(SliderTiming timing) {
        return Math.max(timing.startTimeMs() + timing.durationMs() / 2,
                timing.endTimeMs() - TAIL_LENIENCY_MS);
    }
}
