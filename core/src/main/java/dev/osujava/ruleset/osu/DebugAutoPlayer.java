package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.JudgementWindows;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Generates ordinary gameplay input for development checks; it never edits judgement state. */
public final class DebugAutoPlayer {
    private final GameClock clock;
    private final GameplaySession session;
    private final List<HitObject> circles;
    private final long preemptMs;
    private final double hit50Ms;
    private int targetIndex;
    private double cursorX = 256;
    private double cursorY = 192;

    public DebugAutoPlayer(BeatmapDifficulty difficulty, GameClock clock, GameplaySession session) {
        Objects.requireNonNull(difficulty, "difficulty");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.session = Objects.requireNonNull(session, "session");
        this.circles = difficulty.hitObjects().stream()
                .filter(object -> object.type() == HitObject.Type.CIRCLE)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .toList();
        this.preemptMs = ApproachTimeCalculator.preemptMs(difficulty.settings().approachRate());
        this.hit50Ms = JudgementWindows.fromOverallDifficulty(difficulty.settings().overallDifficulty()).hit50Ms();
    }

    /** Moves to the next Circle and clicks through GameplaySession's standard input API. */
    public void update() {
        long now = clock.nowMs();
        while (targetIndex < circles.size()
                && now > circles.get(targetIndex).timeMs() + hit50Ms) targetIndex++;
        if (targetIndex >= circles.size()) return;

        HitObject target = circles.get(targetIndex);
        if (now < target.timeMs() - preemptMs) return;
        moveCursor(target.x(), target.y());
        if (now < target.timeMs()) return;

        session.click(cursorX, cursorY);
        session.pointerReleased();
        targetIndex++;
    }

    public double cursorX() {
        return cursorX;
    }

    public double cursorY() {
        return cursorY;
    }

    public boolean finished() {
        return targetIndex >= circles.size();
    }

    private void moveCursor(double x, double y) {
        cursorX = x;
        cursorY = y;
        session.pointerMoved(x, y);
    }
}
