package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.OsuObjectGeometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The osu!standard v6+ reverse stack pass and pre-v6 forward pass. */
public final class OsuStacking {
    private static final double STACK_DISTANCE = 3;
    private final Map<HitObject, BeatmapPoint> offsets = new IdentityHashMap<>();
    private final Map<HitObject, Integer> heights = new IdentityHashMap<>();

    public OsuStacking(BeatmapDifficulty difficulty) {
        List<Entry> entries = new ArrayList<>();
        for (HitObject object : difficulty.hitObjects()) {
            if (object.type() != HitObject.Type.CIRCLE && object.type() != HitObject.Type.SLIDER
                    && object.type() != HitObject.Type.SPINNER) continue;
            BeatmapPoint start = new BeatmapPoint(object.x(), object.y());
            BeatmapPoint end = start;
            double endTime = object.endTimeMs();
            if (object.type() == HitObject.Type.SLIDER && object.sliderData() != null) {
                SliderPath path = new SliderPath(object.x(), object.y(), object.sliderData());
                end = path.positionAt(1);
                endTime = SliderTiming.calculate(difficulty, object, path).endTimeMs();
            }
            entries.add(new Entry(object, start, end, endTime));
        }
        entries.sort(Comparator.comparingLong(entry -> entry.object.timeMs()));
        double threshold = ApproachTimeCalculator.preemptMs(difficulty.settings().approachRate())
                * difficulty.settings().stackLeniency();
        if (difficulty.settings().formatVersion() >= 6) stackModern(entries, threshold);
        else stackLegacy(entries, threshold);

        double step = OsuObjectGeometry.stackOffsetPerHeight(difficulty.settings().circleSize());
        for (Entry entry : entries) {
            heights.put(entry.object, entry.height);
            double offset = entry.object.type() == HitObject.Type.SPINNER ? 0 : entry.height * step;
            offsets.put(entry.object, new BeatmapPoint(offset, offset));
        }
    }

    public int height(HitObject object) {
        return heights.getOrDefault(object, 0);
    }

    public BeatmapPoint offset(HitObject object) {
        return offsets.getOrDefault(object, new BeatmapPoint(0, 0));
    }

    public BeatmapPoint position(HitObject object) {
        BeatmapPoint offset = offset(object);
        return new BeatmapPoint(object.x() + offset.x(), object.y() + offset.y());
    }

    private static void stackModern(List<Entry> entries, double threshold) {
        for (int i = entries.size() - 1; i > 0; i--) {
            Entry current = entries.get(i);
            if (current.height != 0 || current.isSpinner()) continue;
            int n = i;
            if (current.isCircle()) {
                while (--n >= 0) {
                    Entry previous = entries.get(n);
                    if (previous.isSpinner()) continue;
                    if ((int) current.object.timeMs() - (int) previous.endTime > threshold) break;
                    if (previous.isSlider() && close(previous.end, current.start)) {
                        int offset = current.height - previous.height + 1;
                        for (int j = n + 1; j <= i; j++) {
                            Entry stacked = entries.get(j);
                            if (close(previous.end, stacked.start)) stacked.height -= offset;
                        }
                        break;
                    }
                    if (close(previous.start, current.start)) {
                        previous.height = current.height + 1;
                        current = previous;
                    }
                }
            } else if (current.isSlider()) {
                while (--n >= 0) {
                    Entry previous = entries.get(n);
                    if (previous.isSpinner()) continue;
                    if (current.object.timeMs() - previous.object.timeMs() > threshold) break;
                    if (close(previous.end, current.start)) {
                        previous.height = current.height + 1;
                        current = previous;
                    }
                }
            }
        }
    }

    private static void stackLegacy(List<Entry> entries, double threshold) {
        for (int i = 0; i < entries.size(); i++) {
            Entry current = entries.get(i);
            if (current.isSpinner() || (current.height != 0 && !current.isSlider())) continue;
            double startTime = current.endTime;
            int sliderStack = 0;
            for (int j = i + 1; j < entries.size(); j++) {
                Entry next = entries.get(j);
                if (next.object.timeMs() - threshold > startTime) break;
                if (next.isSpinner()) continue;
                if (close(next.start, current.start)) {
                    current.height++;
                    startTime = next.object.timeMs();
                } else if (close(next.start, current.end)) {
                    next.height -= ++sliderStack;
                    startTime = next.object.timeMs();
                }
            }
        }
    }

    private static boolean close(BeatmapPoint a, BeatmapPoint b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y()) < STACK_DISTANCE;
    }

    private static final class Entry {
        private final HitObject object;
        private final BeatmapPoint start;
        private final BeatmapPoint end;
        private final double endTime;
        private int height;

        private Entry(HitObject object, BeatmapPoint start, BeatmapPoint end, double endTime) {
            this.object = object;
            this.start = start;
            this.end = end;
            this.endTime = endTime;
        }

        private boolean isCircle() { return object.type() == HitObject.Type.CIRCLE; }
        private boolean isSlider() { return object.type() == HitObject.Type.SLIDER; }
        private boolean isSpinner() { return object.type() == HitObject.Type.SPINNER; }
    }
}
