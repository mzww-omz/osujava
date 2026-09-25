package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementWindows;
import dev.osujava.gameplay.ScoreState;
import dev.osujava.gameplay.ScoreTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OsuGameplaySession implements GameplaySession {
    private final GameClock clock;
    private final List<HitObject> circles;
    private final boolean[] judged;
    private final JudgementWindows windows;
    private final ScoreTracker score = new ScoreTracker();
    private final long preemptMs;
    private final double circleRadius;
    private GameplayState state;

    public OsuGameplaySession(BeatmapDifficulty difficulty, GameClock clock, JudgementWindows windows) {
        this.clock = clock;
        this.windows = windows;
        this.preemptMs = ApproachTimeCalculator.preemptMs(difficulty.settings().approachRate());
        this.circleRadius = Math.max(10, 54.4 - 4.48 * difficulty.settings().circleSize());
        this.circles = difficulty.hitObjects().stream()
                .filter(object -> object.type() == HitObject.Type.CIRCLE)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .toList();
        this.judged = new boolean[circles.size()];
        this.state = createState(clock.nowMs());
    }

    @Override
    public GameplayState update() {
        long now = clock.nowMs();
        expireMisses(now);
        state = createState(now);
        return state;
    }

    @Override
    public void click(double x, double y) {
        long now = clock.nowMs();
        expireMisses(now);
        int candidate = -1;
        long bestOffset = Long.MAX_VALUE;
        for (int index = 0; index < circles.size(); index++) {
            if (judged[index]) continue;
            HitObject circle = circles.get(index);
            long offset = Math.abs(now - circle.timeMs());
            if (offset > windows.hit50Ms()) continue;
            double dx = x - circle.x();
            double dy = y - circle.y();
            if (dx * dx + dy * dy > circleRadius * circleRadius) continue;
            if (offset < bestOffset) {
                candidate = index;
                bestOffset = offset;
            }
        }
        if (candidate >= 0) {
            score.record(windows.judge(bestOffset));
            judged[candidate] = true;
        }
        state = createState(now);
    }

    @Override
    public void finish() {
        for (int index = 0; index < circles.size(); index++) {
            if (!judged[index]) {
                judged[index] = true;
                score.record(Judgement.MISS);
            }
        }
        state = createState(clock.nowMs());
    }

    @Override
    public GameplayState state() {
        return state;
    }

    private void expireMisses(long now) {
        for (int index = 0; index < circles.size(); index++) {
            if (!judged[index] && now > circles.get(index).timeMs() + windows.hit50Ms()) {
                judged[index] = true;
                score.record(Judgement.MISS);
            }
        }
    }

    private GameplayState createState(long now) {
        List<HitCircleVisual> visible = new ArrayList<>();
        for (int index = 0; index < circles.size(); index++) {
            if (judged[index]) continue;
            HitObject circle = circles.get(index);
            long spawnAt = circle.timeMs() - preemptMs;
            if (now < spawnAt || now > circle.timeMs() + windows.hit50Ms()) continue;
            double progress = Math.max(0, Math.min(1, (double) (now - spawnAt) / preemptMs));
            visible.add(new HitCircleVisual(circle.x(), circle.y(), circleRadius,
                    circleRadius * (2.5 - 1.5 * progress), circle.timeMs()));
        }
        boolean complete = allJudged();
        return new GameplayState(now, visible, score.snapshot(), complete);
    }

    private boolean allJudged() {
        for (boolean value : judged) if (!value) return false;
        return true;
    }
}
