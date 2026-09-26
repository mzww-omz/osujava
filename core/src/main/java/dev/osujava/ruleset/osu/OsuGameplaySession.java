package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameInputAction;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.GameplayVisualTiming;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import dev.osujava.gameplay.JudgementWindows;
import dev.osujava.gameplay.OsuObjectGeometry;
import dev.osujava.gameplay.ScoreTracker;
import dev.osujava.gameplay.SliderVisual;
import dev.osujava.gameplay.SpinnerVisual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** osu!standard gameplay rules for HitCircles, Sliders, and Spinners. */
public final class OsuGameplaySession implements GameplaySession {
    private static final double SLIDER_FOLLOW_AREA = 2.4;
    private static final double SPINNER_ACTIVE_RADIUS = 160;

    private final GameClock clock;
    private final List<HitObject> circles;
    private final boolean[] judgedCircles;
    private final List<SliderRuntime> sliders;
    private final List<SpinnerRuntime> spinners;
    private final Map<HitObject, Integer> comboNumbers;
    private final OsuStacking stacking;
    private final List<JudgementVisual> judgementVisuals = new ArrayList<>();
    private final JudgementWindows windows;
    private final ScoreTracker score = new ScoreTracker();
    private final long preemptMs;
    private final double circleRadius;
    private double cursorX;
    private double cursorY;
    private final EnumSet<GameInputAction> pressedActions = EnumSet.noneOf(GameInputAction.class);
    private GameplayState state;

    public OsuGameplaySession(BeatmapDifficulty difficulty, GameClock clock, JudgementWindows windows) {
        this.clock = clock;
        this.windows = windows;
        this.preemptMs = ApproachTimeCalculator.preemptMs(difficulty.settings().approachRate());
        this.circleRadius = OsuObjectGeometry.radius(difficulty.settings().circleSize());
        this.stacking = new OsuStacking(difficulty);
        this.comboNumbers = comboNumbers(difficulty.hitObjects());
        this.circles = difficulty.hitObjects().stream()
                .filter(object -> object.type() == HitObject.Type.CIRCLE)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .toList();
        this.judgedCircles = new boolean[circles.size()];
        this.sliders = difficulty.hitObjects().stream()
                .filter(object -> object.type() == HitObject.Type.SLIDER && object.sliderData() != null)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .map(object -> new SliderRuntime(object, difficulty))
                .toList();
        this.spinners = difficulty.hitObjects().stream()
                .filter(object -> object.type() == HitObject.Type.SPINNER)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .map(object -> new SpinnerRuntime(object, difficulty.settings().overallDifficulty()))
                .toList();
        this.state = createState(clock.nowMs());
    }

    @Override
    public GameplayState update() {
        long now = clock.nowMs();
        updateSpinnerRotation(now);
        expireMisses(now);
        processSliderEvents(now);
        processSpinnerJudgements(now);
        state = createState(now);
        return state;
    }

    @Override
    public void click(double x, double y) {
        // Legacy one-shot API used by tests and Debug Auto: each call is a fresh press.
        release(GameInputAction.LEFT);
        press(GameInputAction.LEFT, x, y);
    }

    @Override
    public void press(GameInputAction action, double x, double y) {
        long now = clock.nowMs();
        cursorX = x;
        cursorY = y;
        if (!pressedActions.add(action)) {
            state = createState(now);
            return;
        }
        updateSpinnerRotation(now);
        expireMisses(now);
        processSpinnerJudgements(now);

        Candidate circleCandidate = bestCircleCandidate(x, y, now);
        Candidate sliderCandidate = bestSliderCandidate(x, y, now);
        HitObject candidateObject = circleCandidate == null ? null : circles.get(circleCandidate.index());
        if (sliderCandidate != null && (candidateObject == null
                || sliders.get(sliderCandidate.index()).object.timeMs() < candidateObject.timeMs())) {
            candidateObject = sliders.get(sliderCandidate.index()).object;
        }
        if (candidateObject != null && isBlockedByEarlierObject(candidateObject, now)) {
            state = createState(now);
            return;
        }
        if (candidateObject != null) missEarlierObjects(candidateObject, now);

        if (circleCandidate != null && candidateObject == circles.get(circleCandidate.index())) {
            judgedCircles[circleCandidate.index()] = true;
            Judgement judgement = windows.judge(circleCandidate.offsetMs());
            score.record(judgement);
            recordVisualJudgement(circles.get(circleCandidate.index()), judgement, now);
        } else if (sliderCandidate != null && candidateObject == sliders.get(sliderCandidate.index()).object) {
            SliderRuntime slider = sliders.get(sliderCandidate.index());
            slider.headJudged = true;
            slider.headHit = true;
            slider.headJudgementTimeMs = now;
            slider.headAction = action;
            slider.requiresHeadAction = pressedActions.contains(other(action));
            Judgement judgement = windows.judge(sliderCandidate.offsetMs());
            score.record(judgement);
            recordVisualJudgement(slider.object, judgement, now);
        }

        processSliderEvents(now);
        state = createState(now);
    }

    @Override
    public void pointerMoved(double x, double y) {
        cursorX = x;
        cursorY = y;
        updateSpinnerRotation(clock.nowMs());
    }

    @Override
    public void pointerReleased() {
        release(GameInputAction.LEFT);
    }

    @Override
    public void release(GameInputAction action) {
        pressedActions.remove(action);
        for (SliderRuntime slider : sliders) {
            if (slider.headAction != null && action == other(slider.headAction)) {
                slider.requiresHeadAction = false;
            }
        }
    }

    @Override
    public void finish() {
        long now = clock.nowMs();
        updateSpinnerRotation(now);
        for (int index = 0; index < circles.size(); index++) {
            if (!judgedCircles[index]) {
                judgedCircles[index] = true;
                score.record(Judgement.MISS);
                recordVisualJudgement(circles.get(index), Judgement.MISS, now);
            }
        }
        for (SliderRuntime slider : sliders) {
            if (!slider.headJudged) {
                slider.headJudged = true;
                slider.headJudgementTimeMs = now;
                score.record(Judgement.MISS);
                recordVisualJudgement(slider.object, Judgement.MISS, now);
            }
            for (int index = 0; index < slider.events.size(); index++) {
                if (!slider.eventJudged[index]) {
                    slider.eventJudged[index] = true;
                    OsuScoreEvent type = OsuScoreEvent.fromSliderEvent(slider.events.get(index).type());
                    score.recordNestedHit(type.baseScore(), false, type.affectsCombo());
                }
            }
        }
        processSpinnerJudgements(now);
        state = createState(now);
    }

    @Override
    public GameplayState state() {
        return state;
    }

    private Candidate bestCircleCandidate(double x, double y, long now) {
        Candidate best = null;
        for (int index = 0; index < circles.size(); index++) {
            if (judgedCircles[index]) continue;
            HitObject circle = circles.get(index);
            double offset = Math.abs((double) now - circle.timeMs());
            BeatmapPoint position = stacking.position(circle);
            if (offset > windows.hit50Ms() || !withinRadius(x, y, position.x(), position.y(), circleRadius)) continue;
            if (best == null || circle.timeMs() < circles.get(best.index()).timeMs()) best = new Candidate(index, offset);
        }
        return best;
    }

    private Candidate bestSliderCandidate(double x, double y, long now) {
        Candidate best = null;
        for (int index = 0; index < sliders.size(); index++) {
            SliderRuntime slider = sliders.get(index);
            if (slider.headJudged) continue;
            double offset = Math.abs((double) now - slider.object.timeMs());
            if (offset > windows.hit50Ms()
                    || !withinRadius(x, y, stacking.position(slider.object).x(),
                    stacking.position(slider.object).y(), circleRadius)) continue;
            if (best == null || slider.object.timeMs() < sliders.get(best.index()).object.timeMs()) best = new Candidate(index, offset);
        }
        return best;
    }

    private void expireMisses(long now) {
        for (int index = 0; index < circles.size(); index++) {
            if (!judgedCircles[index] && now > circles.get(index).timeMs() + windows.hit50Ms()) {
                judgedCircles[index] = true;
                score.record(Judgement.MISS);
                recordVisualJudgement(circles.get(index), Judgement.MISS,
                        circles.get(index).timeMs() + windows.hit50Ms());
            }
        }
        for (SliderRuntime slider : sliders) {
            if (!slider.headJudged && now > slider.object.timeMs() + windows.hit50Ms()) {
                slider.headJudged = true;
                slider.headHit = false;
                slider.headJudgementTimeMs = Math.round(slider.object.timeMs() + windows.hit50Ms());
                score.record(Judgement.MISS);
                recordVisualJudgement(slider.object, Judgement.MISS, slider.headJudgementTimeMs);
            }
        }
    }

    private void processSliderEvents(long now) {
        for (SliderRuntime slider : sliders) {
            slider.tracking = updateTrackingAt(slider, now);
            if (!slider.headJudged && now <= slider.object.timeMs() + windows.hit50Ms()) continue;
            for (int index = 0; index < slider.events.size(); index++) {
                if (slider.eventJudged[index]) continue;
                SliderEvent event = slider.events.get(index);
                double dueAt = event.type() == SliderEvent.Type.TAIL
                        ? SliderEventGenerator.tailJudgementStartTime(slider.timing) : event.timeMs();
                if (now < dueAt) continue;
                if (event.type() == SliderEvent.Type.TAIL && hasPendingEarlierEvent(slider, index)) continue;
                boolean hit = slider.tracking;
                slider.eventJudged[index] = true;
                slider.eventHit[index] = hit;
                Judgement judgement = hit ? Judgement.HIT300 : Judgement.MISS;
                OsuScoreEvent type = OsuScoreEvent.fromSliderEvent(event.type());
                score.recordNestedHit(type.baseScore(), hit, type.affectsCombo());
                if (event.type() == SliderEvent.Type.TAIL) {
                    BeatmapPoint tail = slider.path.positionAt(event.pathProgress());
                    recordVisualJudgement(tail.x(), tail.y(), circleRadius, judgement, now);
                }
            }
        }
    }

    private void updateSpinnerRotation(long now) {
        for (SpinnerRuntime spinner : spinners) {
            spinner.tracking = !pressedActions.isEmpty() && now >= spinner.object.timeMs() && now < spinner.object.endTimeMs();
            spinner.rotation.moveCursor(cursorX, cursorY, now, spinner.tracking);
            int completedSpins = spinner.rotation.completedSpins();
            while (spinner.scoredSpins < completedSpins) {
                spinner.scoredSpins++;
                if (spinner.scoredSpins <= spinner.requirements.spinsRequiredForBonus()) {
                    score.recordBonusScore(OsuScoreEvent.SPINNER_SPIN.baseScore());
                } else if (spinner.scoredSpins <= spinner.requirements.spinsRequiredForBonus()
                        + spinner.requirements.maximumBonusSpins()) {
                    score.recordBonusScore(OsuScoreEvent.SPINNER_BONUS.baseScore());
                }
            }
        }
    }

    private void processSpinnerJudgements(long now) {
        for (SpinnerRuntime spinner : spinners) {
            if (spinner.judgement != null || now < spinner.object.endTimeMs()) continue;
            double progress = spinner.progress();
            spinner.judgement = progress >= 1 ? Judgement.HIT300
                    : progress > 0.9 ? Judgement.HIT100
                    : progress > 0.75 ? Judgement.HIT50
                    : Judgement.MISS;
            score.record(spinner.judgement);
            recordVisualJudgement(spinner.object, spinner.judgement, spinner.object.endTimeMs());
            spinner.tracking = false;
        }
    }

    private boolean hasPendingEarlierEvent(SliderRuntime slider, int eventIndex) {
        for (int index = 0; index < eventIndex; index++) {
            if (!slider.eventJudged[index]) return true;
        }
        return false;
    }

    private boolean updateTrackingAt(SliderRuntime slider, long now) {
        if (pressedActions.isEmpty() || now < slider.object.timeMs()
                || now > slider.timing.endTimeMs() + windows.hit50Ms()) return false;
        if (slider.requiresHeadAction && !pressedActions.contains(slider.headAction)) return false;
        BeatmapPoint ball = slider.path.positionAt(slider.timing.progressAt(now));
        double radius = slider.tracking ? circleRadius * SLIDER_FOLLOW_AREA : circleRadius;
        return withinRadius(cursorX, cursorY, ball.x(), ball.y(), radius);
    }

    private GameplayState createState(long now) {
        List<HitCircleVisual> visibleCircles = new ArrayList<>();
        for (int index = 0; index < circles.size(); index++) {
            if (judgedCircles[index]) continue;
            HitObject circle = circles.get(index);
            long spawnAt = circle.timeMs() - preemptMs;
            if (now < spawnAt || now > circle.timeMs() + windows.hit50Ms()) continue;
            double progress = GameplayVisualTiming.approachProgress(now, circle.timeMs(), preemptMs);
            BeatmapPoint position = stacking.position(circle);
            visibleCircles.add(new HitCircleVisual(position.x(), position.y(), circleRadius,
                    GameplayVisualTiming.approachRadius(circleRadius, progress), circle.timeMs(),
                    preemptMs, comboNumbers.getOrDefault(circle, 1)));
        }

        List<SliderVisual> visibleSliders = new ArrayList<>();
        for (SliderRuntime slider : sliders) {
            double spawnAt = slider.object.timeMs() - preemptMs;
            if (now < spawnAt || now > slider.timing.endTimeMs() + 150) continue;
            double progress = slider.timing.progressAt(now);
            BeatmapPoint ball = slider.path.positionAt(progress);
            double approachProgress = GameplayVisualTiming.approachProgress(
                    now, slider.object.timeMs(), preemptMs);
            List<SliderVisual.RepeatMarker> repeats = new ArrayList<>();
            for (int index = 0; index < slider.events.size(); index++) {
                SliderEvent event = slider.events.get(index);
                if (event.type() == SliderEvent.Type.REPEAT) {
                    repeats.add(new SliderVisual.RepeatMarker(slider.path.positionAt(event.pathProgress()),
                            event.spanIndex(), slider.eventJudged[index], event.timeMs()));
                }
            }
            visibleSliders.add(new SliderVisual(slider.path.sampledPoints(),
                    stacking.position(slider.object), slider.path.positionAt(slider.timing.endProgress()),
                    ball, repeats, circleRadius, GameplayVisualTiming.approachRadius(circleRadius, approachProgress), progress,
                    slider.object.timeMs(), slider.timing.endTimeMs(), slider.headJudged, slider.headHit,
                    slider.tracking, preemptMs, comboNumbers.getOrDefault(slider.object, 1),
                    slider.headJudgementTimeMs));
        }

        List<SpinnerVisual> visibleSpinners = new ArrayList<>();
        for (SpinnerRuntime spinner : spinners) {
            double spawnAt = spinner.object.timeMs() - preemptMs;
            if (now < spawnAt || now > spinner.object.endTimeMs() + 150) continue;
            visibleSpinners.add(new SpinnerVisual(spinner.object.x(), spinner.object.y(), SPINNER_ACTIVE_RADIUS,
                    spinner.progress(), spinner.rotation.visualRotationDegrees(),
                    spinner.rotation.totalRotationDegrees(), spinner.rotation.completedSpins(),
                    spinner.requirements.spinsRequired(), spinner.object.timeMs(), spinner.object.endTimeMs(),
                    spinner.tracking, spinner.judgement, preemptMs));
        }
        judgementVisuals.removeIf(visual -> now - visual.timeMs() > 900);
        return new GameplayState(now, visibleCircles, visibleSliders, visibleSpinners, score.snapshot(),
                allJudged(), judgementVisuals);
    }

    private void recordVisualJudgement(HitObject object, Judgement judgement, double timeMs) {
        BeatmapPoint position = stacking.position(object);
        recordVisualJudgement(position.x(), position.y(), circleRadius, judgement, timeMs);
    }

    private void recordVisualJudgement(double x, double y, double radius, Judgement judgement, double timeMs) {
        judgementVisuals.add(new JudgementVisual(x, y, radius, judgement, (long) Math.round(timeMs)));
    }

    private Map<HitObject, Integer> comboNumbers(List<HitObject> hitObjects) {
        Map<HitObject, Integer> numbers = new IdentityHashMap<>();
        List<HitObject> ordered = hitObjects.stream()
                .filter(object -> object.type() == HitObject.Type.CIRCLE
                        || object.type() == HitObject.Type.SLIDER || object.type() == HitObject.Type.SPINNER)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .toList();
        int comboNumber = 0;
        for (HitObject object : ordered) {
            if (comboNumber == 0 || (object.rawType() & 4) != 0) comboNumber = 1;
            else comboNumber++;
            numbers.put(object, comboNumber);
        }
        return numbers;
    }

    private boolean allJudged() {
        for (boolean judged : judgedCircles) if (!judged) return false;
        for (SliderRuntime slider : sliders) {
            if (!slider.headJudged) return false;
            for (boolean judged : slider.eventJudged) if (!judged) return false;
        }
        for (SpinnerRuntime spinner : spinners) if (spinner.judgement == null) return false;
        return true;
    }

    private boolean withinRadius(double x, double y, double targetX, double targetY, double radius) {
        double dx = x - targetX;
        double dy = y - targetY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private GameInputAction other(GameInputAction action) {
        return action == GameInputAction.LEFT ? GameInputAction.RIGHT : GameInputAction.LEFT;
    }

    private boolean isBlockedByEarlierObject(HitObject target, long now) {
        for (int index = 0; index < circles.size(); index++) {
            HitObject circle = circles.get(index);
            if (circle.timeMs() >= target.timeMs()) break;
            if (!judgedCircles[index] && now < circle.timeMs()) return true;
        }
        for (SliderRuntime slider : sliders) {
            if (slider.object.timeMs() >= target.timeMs()) break;
            if (!slider.headJudged && now < slider.object.timeMs()) return true;
        }
        return false;
    }

    private void missEarlierObjects(HitObject target, long now) {
        for (int index = 0; index < circles.size(); index++) {
            HitObject circle = circles.get(index);
            if (circle.timeMs() >= target.timeMs()) break;
            if (!judgedCircles[index]) {
                judgedCircles[index] = true;
                score.record(Judgement.MISS);
                recordVisualJudgement(circle, Judgement.MISS, now);
            }
        }
        for (SliderRuntime slider : sliders) {
            if (slider.object.timeMs() >= target.timeMs()) break;
            if (!slider.headJudged) {
                slider.headJudged = true;
                slider.headJudgementTimeMs = now;
                score.record(Judgement.MISS);
                recordVisualJudgement(slider.object, Judgement.MISS, now);
            }
        }
    }

    private final class SliderRuntime {
        private final HitObject object;
        private final SliderPath path;
        private final SliderTiming timing;
        private final List<SliderEvent> events;
        private final boolean[] eventJudged;
        private final boolean[] eventHit;
        private boolean headJudged;
        private boolean headHit;
        private boolean tracking;
        private GameInputAction headAction;
        private boolean requiresHeadAction;
        private long headJudgementTimeMs = Long.MIN_VALUE;

        private SliderRuntime(HitObject object, BeatmapDifficulty difficulty) {
            this.object = object;
            BeatmapPoint offset = stacking.offset(object);
            this.path = new SliderPath(object.x(), object.y(), object.sliderData())
                    .translated(offset.x(), offset.y());
            this.timing = SliderTiming.calculate(difficulty, object, path);
            this.events = SliderEventGenerator.generate(timing, path);
            this.eventJudged = new boolean[events.size()];
            this.eventHit = new boolean[events.size()];
        }
    }

    private final class SpinnerRuntime {
        private final HitObject object;
        private final SpinnerRequirements requirements;
        private final SpinnerRotationTracker rotation;
        private int scoredSpins;
        private boolean tracking;
        private Judgement judgement;

        private SpinnerRuntime(HitObject object, double overallDifficulty) {
            this.object = object;
            this.requirements = SpinnerRequirements.calculate(object.durationMs(), overallDifficulty);
            this.rotation = new SpinnerRotationTracker(object.x(), object.y());
        }

        private double progress() {
            if (requirements.spinsRequired() == 0) return 1;
            return rotation.totalRotationDegrees() / (requirements.spinsRequired() * 360.0);
        }
    }

    private record Candidate(int index, double offsetMs) {
    }
}
