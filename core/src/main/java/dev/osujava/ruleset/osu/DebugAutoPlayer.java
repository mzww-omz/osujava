package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.JudgementWindows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Generates ordinary gameplay input for development checks; it never edits judgement state. */
public final class DebugAutoPlayer {
    /** 300 RPM clears the highest current requirement with margin and moves 30 degrees per 60 Hz frame. */
    public static final double SPINNER_RPM = 300;
    private static final double SPINNER_CURSOR_RADIUS = 80;

    private final GameClock clock;
    private final GameplaySession session;
    private final List<Target> targets;
    private final OsuStacking stacking;
    private final long preemptMs;
    private final double hit50Ms;
    private int targetIndex;
    private boolean targetStarted;
    private boolean primaryPressed;
    private boolean releaseAfterUpdate;
    private double cursorX = 256;
    private double cursorY = 192;

    public DebugAutoPlayer(BeatmapDifficulty difficulty, GameClock clock, GameplaySession session) {
        Objects.requireNonNull(difficulty, "difficulty");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.session = Objects.requireNonNull(session, "session");
        this.stacking = new OsuStacking(difficulty);
        List<Target> ordered = new ArrayList<>();
        for (HitObject object : difficulty.hitObjects()) {
            if (object.type() == HitObject.Type.CIRCLE) ordered.add(new Target(object, null, null));
            else if (object.type() == HitObject.Type.SLIDER && object.sliderData() != null) {
                BeatmapPoint offset = stacking.offset(object);
                SliderPath path = new SliderPath(object.x(), object.y(), object.sliderData())
                        .translated(offset.x(), offset.y());
                ordered.add(new Target(object, path, SliderTiming.calculate(difficulty, object, path)));
            } else if (object.type() == HitObject.Type.SPINNER) ordered.add(new Target(object, null, null));
        }
        ordered.sort(Comparator.comparingLong(target -> target.object().timeMs()));
        this.targets = List.copyOf(ordered);
        this.preemptMs = ApproachTimeCalculator.preemptMs(difficulty.settings().approachRate());
        this.hit50Ms = JudgementWindows.fromOverallDifficulty(difficulty.settings().overallDifficulty()).hit50Ms();
    }

    /** Moves the current time-ordered target through GameplaySession's standard input API. */
    public void update() {
        long now = clock.nowMs();
        if (releaseAfterUpdate || targetIndex >= targets.size()) return;
        while (!targetStarted && targetIndex < targets.size()
                && targets.get(targetIndex).object().type() != HitObject.Type.SPINNER
                && now > targets.get(targetIndex).object().timeMs() + hit50Ms) targetIndex++;
        if (targetIndex >= targets.size()) return;

        Target target = targets.get(targetIndex);
        HitObject object = target.object();
        if (object.type() == HitObject.Type.CIRCLE) updateCircle(target, now);
        else if (object.type() == HitObject.Type.SLIDER) updateSlider(target, now);
        else if (object.type() == HitObject.Type.SPINNER) updateSpinner(target, now);
    }

    /** Called after GameplaySession.update() so terminal Slider/Spinner input lasts through its final judgement. */
    public void afterSessionUpdate() {
        if (!releaseAfterUpdate) return;
        session.pointerReleased();
        primaryPressed = false;
        releaseAfterUpdate = false;
        targetStarted = false;
        targetIndex++;
    }

    public double cursorX() {
        return cursorX;
    }

    public double cursorY() {
        return cursorY;
    }

    public boolean finished() {
        return targetIndex >= targets.size();
    }

    public boolean primaryPressed() {
        return primaryPressed;
    }

    private void updateCircle(Target target, long now) {
        HitObject object = target.object();
        if (now < object.timeMs() - preemptMs) return;
        BeatmapPoint position = stacking.position(object);
        moveCursor(position.x(), position.y());
        if (now < object.timeMs()) return;

        session.click(cursorX, cursorY);
        session.pointerReleased();
        targetIndex++;
    }

    private void updateSlider(Target target, long now) {
        HitObject object = target.object();
        if (!targetStarted) {
            if (now < object.timeMs() - preemptMs) return;
            BeatmapPoint position = stacking.position(object);
            moveCursor(position.x(), position.y());
            if (now < object.timeMs()) return;
            session.click(cursorX, cursorY);
            targetStarted = true;
            primaryPressed = true;
            return;
        }

        SliderTiming timing = target.sliderTiming();
        BeatmapPoint ball = target.sliderPath().positionAt(timing.progressAt(now));
        moveCursor(ball.x(), ball.y());
        if (now >= timing.endTimeMs()) releaseAfterUpdate = true;
    }

    private void updateSpinner(Target target, long now) {
        HitObject object = target.object();
        if (now < object.timeMs() - preemptMs) return;
        moveCursor(spinnerX(object, now), spinnerY(object, now));
        if (!targetStarted && now >= object.timeMs()) {
            session.click(cursorX, cursorY);
            targetStarted = true;
            primaryPressed = true;
        }
        if (targetStarted && now >= object.endTimeMs()) releaseAfterUpdate = true;
    }

    private double spinnerX(HitObject spinner, long now) {
        return spinner.x() + SPINNER_CURSOR_RADIUS * Math.cos(spinnerAngle(spinner, now));
    }

    private double spinnerY(HitObject spinner, long now) {
        return spinner.y() + SPINNER_CURSOR_RADIUS * Math.sin(spinnerAngle(spinner, now));
    }

    private double spinnerAngle(HitObject spinner, long now) {
        double elapsed = Math.max(0, Math.min(now, (long) spinner.endTimeMs()) - spinner.timeMs());
        return elapsed * SPINNER_RPM * Math.PI * 2 / 60_000;
    }

    private void moveCursor(double x, double y) {
        cursorX = x;
        cursorY = y;
        session.pointerMoved(x, y);
    }

    private record Target(HitObject object, SliderPath sliderPath, SliderTiming sliderTiming) {
    }
}
