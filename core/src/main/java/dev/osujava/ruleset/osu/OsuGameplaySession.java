package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.FollowCircleAnimation;
import dev.osujava.gameplay.GameplayAudioCue;
import dev.osujava.gameplay.GameInputAction;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.HitObjectVisual;
import dev.osujava.gameplay.GameplayVisualTiming;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import dev.osujava.gameplay.JudgementWindows;
import dev.osujava.gameplay.OsuObjectGeometry;
import dev.osujava.gameplay.ScoreTracker;
import dev.osujava.gameplay.LegacyHudAnimation;
import dev.osujava.gameplay.LegacySongProgress;
import dev.osujava.gameplay.SliderNestedVisualTiming;
import dev.osujava.gameplay.SliderVisual;
import dev.osujava.gameplay.SpinnerVisual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.stream.IntStream;
import java.util.Map;

/** osu!standard gameplay rules for HitCircles, Sliders, and Spinners. */
public final class OsuGameplaySession implements GameplaySession {
    private static final double SLIDER_FOLLOW_AREA = 2.4;
    private static final double SPINNER_ACTIVE_RADIUS = 160;

    private final GameClock clock;
    private final List<HitObject> circles;
    private final boolean[] judgedCircles;
    private final Map<HitObject, CircleResult> circleResults = new IdentityHashMap<>();
    private final List<SliderRuntime> sliders;
    private final List<SpinnerRuntime> spinners;
    private final Map<HitObject, ComboInfo> comboInfo;
    private final OsuStacking stacking;
    private final List<JudgementVisual> judgementVisuals = new ArrayList<>();
    private final List<GameplayAudioCue> audioCues = new ArrayList<>();
    private final List<TimingPoint> timingPoints;
    private final JudgementWindows windows;
    private final LegacyHudAnimation hud = new LegacyHudAnimation();
    private final LegacySongProgress songProgress;
    private final ScoreTracker score = new ScoreTracker();
    private final long preemptMs;
    private final double circleRadius;
    private double cursorX;
    private double cursorY;
    private final EnumSet<GameInputAction> pressedActions = EnumSet.noneOf(GameInputAction.class);
    private GameplayState state;
    private final Map<HitObject, Integer> beatmapIndices = new IdentityHashMap<>();
    private final List<Integer> visualOrder;

    public OsuGameplaySession(BeatmapDifficulty difficulty, GameClock clock, JudgementWindows windows) {
        for (int i = 0; i < difficulty.hitObjects().size(); i++)
            beatmapIndices.put(difficulty.hitObjects().get(i), i);
        // HitObjectContainer.Compare: descending StartTime, then reverse child insertion ID.
        this.visualOrder = IntStream.range(0, difficulty.hitObjects().size()).boxed()
                .sorted(Comparator.<Integer>comparingLong(i -> difficulty.hitObjects().get(i).timeMs()).reversed()
                        .thenComparing(Comparator.reverseOrder())).toList();
        this.clock = clock;
        this.timingPoints = difficulty.timingPoints();
        this.windows = windows;
        this.preemptMs = ApproachTimeCalculator.preemptMs(difficulty.settings().approachRate());
        this.circleRadius = OsuObjectGeometry.radius(difficulty.settings().circleSize());
        this.stacking = new OsuStacking(difficulty);
        this.comboInfo = comboInformation(difficulty.hitObjects());
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
        double first = difficulty.hitObjects().stream().filter(o -> o.type() != HitObject.Type.UNKNOWN)
                .mapToDouble(HitObject::timeMs).min().orElse(Double.NaN);
        double last = Math.max(circles.stream().mapToDouble(HitObject::timeMs).max().orElse(first),
                Math.max(sliders.stream().mapToDouble(s -> s.timing.endTimeMs()).max().orElse(first),
                        spinners.stream().mapToDouble(s -> s.object.endTimeMs()).max().orElse(first)));
        this.songProgress = Double.isFinite(first) ? new LegacySongProgress(clock.nowMs(), first, last) : null;
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
            hud.changed(score.snapshot(), now);
            if (judgement != Judgement.MISS) emitHitSound(circles.get(circleCandidate.index()), now);
            recordVisualJudgement(circles.get(circleCandidate.index()), judgement, now);
        } else if (sliderCandidate != null && candidateObject == sliders.get(sliderCandidate.index()).object) {
            SliderRuntime slider = sliders.get(sliderCandidate.index());
            slider.headJudged = true;
            slider.headJudgementTimeMs = now;
            Judgement judgement = windows.judge(sliderCandidate.offsetMs());
            slider.headHit = judgement != Judgement.MISS;
            if (slider.headHit) {
                slider.headAction = action;
                slider.requiresHeadAction = pressedActions.contains(other(action));
            }
            score.record(judgement);
            hud.changed(score.snapshot(), now);
            if (slider.headHit) emitHitSound(slider.object, now);
            recordVisualJudgement(slider.object, judgement, now);
            if (slider.headHit) postProcessLateSliderHead(slider, now);
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
                hud.changed(score.snapshot(), now);
                recordVisualJudgement(circles.get(index), Judgement.MISS, now);
            }
        }
        for (SliderRuntime slider : sliders) {
            if (!slider.headJudged) {
                slider.headJudged = true;
                slider.headJudgementTimeMs = now;
                score.record(Judgement.MISS);
                hud.changed(score.snapshot(), now);
                recordVisualJudgement(slider.object, Judgement.MISS, now);
            }
            for (int index = 0; index < slider.events.size(); index++) {
                if (!slider.eventJudged[index]) {
                    slider.eventJudged[index] = true;
                    OsuScoreEvent type = OsuScoreEvent.fromSliderEvent(slider.events.get(index).type());
                    score.recordNestedHit(type.baseScore(), false, type.affectsCombo());
                    hud.changed(score.snapshot(), now);
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

    @Override
    public List<GameplayAudioCue> drainAudioCues() {
        List<GameplayAudioCue> pending = List.copyOf(audioCues);
        audioCues.clear();
        return pending;
    }

    private Candidate bestCircleCandidate(double x, double y, long now) {
        Candidate best = null;
        for (int index = 0; index < circles.size(); index++) {
            if (judgedCircles[index]) continue;
            HitObject circle = circles.get(index);
            double offset = Math.abs((double) now - circle.timeMs());
            BeatmapPoint position = stacking.position(circle);
            if (now < circle.timeMs() - JudgementWindows.MISS_WINDOW_MS
                    || now > circle.timeMs() + windows.hit50Ms()
                    || !withinRadius(x, y, position.x(), position.y(), circleRadius)) continue;
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
            if (now < slider.object.timeMs() - JudgementWindows.MISS_WINDOW_MS
                    || now > slider.object.timeMs() + windows.hit50Ms()
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
                hud.changed(score.snapshot(), now);
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
                hud.changed(score.snapshot(), now);
                recordVisualJudgement(slider.object, Judgement.MISS, slider.headJudgementTimeMs);
            }
        }
    }

    private void processSliderEvents(long now) {
        for (SliderRuntime slider : sliders) {
            setVisualTracking(slider, updateTrackingAt(slider, now), now);
            if (!slider.headJudged && now <= slider.object.timeMs() + windows.hit50Ms()) continue;
            for (int index = 0; index < slider.events.size(); index++) {
                if (slider.eventJudged[index]) continue;
                SliderEvent event = slider.events.get(index);
                double dueAt = event.type() == SliderEvent.Type.TAIL
                        ? SliderEventGenerator.tailJudgementStartTime(slider.timing) : event.timeMs();
                if (now < dueAt) continue;
                if (event.type() == SliderEvent.Type.TAIL && hasPendingEarlierEvent(slider, index)) continue;
                judgeSliderEvent(slider, index, slider.tracking, now);
            }
        }
    }

    private void postProcessLateSliderHead(SliderRuntime slider, long now) {
        if (now <= slider.object.timeMs()) return;
        BeatmapPoint ball = slider.path.positionAt(slider.timing.progressAt(now));
        double expandedRadius = circleRadius * SLIDER_FOLLOW_AREA;
        if (!withinRadius(cursorX, cursorY, ball.x(), ball.y(), expandedRadius)) return;

        boolean allTicksInRange = true;
        for (int index = 0; index < slider.events.size(); index++) {
            SliderEvent event = slider.events.get(index);
            if (event.timeMs() > now) break;
            BeatmapPoint position = slider.path.positionAt(event.pathProgress());
            if (!withinRadius(cursorX, cursorY, position.x(), position.y(), expandedRadius)) {
                allTicksInRange = false;
                break;
            }
        }
        for (int index = 0; index < slider.events.size(); index++) {
            SliderEvent event = slider.events.get(index);
            if (event.timeMs() > now) break;
            if (!slider.eventJudged[index]) judgeSliderEvent(slider, index, allTicksInRange, now);
        }
        setVisualTracking(slider, allTicksInRange
                || withinRadius(cursorX, cursorY, ball.x(), ball.y(), circleRadius), now);
    }

    private void setVisualTracking(SliderRuntime slider, boolean tracking, long now) {
        if (tracking != slider.tracking && now < slider.timing.endTimeMs()) {
            slider.followEvents.add(new FollowCircleAnimation.Event(tracking
                    ? FollowCircleAnimation.Kind.PRESS : FollowCircleAnimation.Kind.RELEASE, now));
        }
        slider.tracking = tracking;
    }

    private void judgeSliderEvent(SliderRuntime slider, int index, boolean hit, long now) {
        SliderEvent event = slider.events.get(index);
        slider.eventJudged[index] = true;
        slider.eventHit[index] = hit;
        slider.eventJudgementTimes[index] = now;
        slider.followEvents.add(new FollowCircleAnimation.Event(!hit ? FollowCircleAnimation.Kind.BREAK
                : event.type() == SliderEvent.Type.TAIL ? FollowCircleAnimation.Kind.END
                : FollowCircleAnimation.Kind.TICK, hit && event.type() == SliderEvent.Type.TAIL
                ? slider.timing.endTimeMs() : now));
        Judgement judgement = hit ? Judgement.HIT300 : Judgement.MISS;
        OsuScoreEvent type = OsuScoreEvent.fromSliderEvent(event.type());
        score.recordNestedHit(type.baseScore(), hit, type.affectsCombo());
        hud.changed(score.snapshot(), now);
        if (hit) {
            if (event.type() == SliderEvent.Type.TICK) emitAudioCue(now,
                    OsuHitsoundSamples.sliderTick(timingAt(event.timeMs())), timingAt(event.timeMs()));
            else emitHitSound(slider.object, now);
        }
        if (event.type() == SliderEvent.Type.TAIL) {
            BeatmapPoint tail = slider.path.positionAt(event.pathProgress());
            recordVisualJudgement(tail.x(), tail.y(), circleRadius, judgement, now,
                    JudgementVisual.Kind.SLIDER_TAIL, comboInfo.getOrDefault(slider.object,
                            new ComboInfo(1, 0)).colorIndex());
        }
    }

    private void updateSpinnerRotation(long now) {
        for (SpinnerRuntime spinner : spinners) {
            spinner.tracking = !pressedActions.isEmpty() && now >= spinner.object.timeMs() && now < spinner.object.endTimeMs();
            spinner.rotation.moveCursor(cursorX, cursorY, now, spinner.tracking);
            int completedSpins = spinner.rotation.completedSpins();
            if (spinner.completedAtMs == Long.MIN_VALUE && now >= spinner.object.timeMs()
                    && spinner.progress() >= 1) spinner.completedAtMs = now;
            while (spinner.scoredSpins < completedSpins) {
                spinner.scoredSpins++;
                if (spinner.scoredSpins <= spinner.requirements.spinsRequiredForBonus()) {
                    score.recordBonusScore(OsuScoreEvent.SPINNER_SPIN.baseScore());
                    hud.changed(score.snapshot(), now);
                    emitAudioCue(now, OsuHitsoundSamples.spinnerSpin(timingAt(now), false), timingAt(now));
                } else if (spinner.scoredSpins <= spinner.requirements.spinsRequiredForBonus()
                        + spinner.requirements.maximumBonusSpins()) {
                    score.recordBonusScore(OsuScoreEvent.SPINNER_BONUS.baseScore());
                    hud.changed(score.snapshot(), now);
                    emitAudioCue(now, OsuHitsoundSamples.spinnerSpin(timingAt(now), true), timingAt(now));
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
            hud.changed(score.snapshot(), now);
            if (spinner.judgement != Judgement.MISS) emitHitSound(spinner.object, now);
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
            HitObject circle = circles.get(index);
            CircleResult result = circleResults.get(circle);
            if (judgedCircles[index] && (result == null || now > result.timeMs()
                    + (result.judgement() == Judgement.MISS ? 100 : 240))) continue;
            long spawnAt = circle.timeMs() - preemptMs;
            if (now < spawnAt || (result == null && now > circle.timeMs() + windows.hit50Ms())) continue;
            double progress = GameplayVisualTiming.approachProgress(now, circle.timeMs(), preemptMs);
            BeatmapPoint position = stacking.position(circle);
            ComboInfo combo = comboInfo.getOrDefault(circle, new ComboInfo(1, 0));
            visibleCircles.add(new HitCircleVisual(position.x(), position.y(), circleRadius,
                    GameplayVisualTiming.approachRadius(circleRadius, progress), circle.timeMs(),
                    preemptMs, combo.number(), combo.colorIndex(), beatmapIndices.get(circle),
                    result == null ? null : result.judgement(), result == null ? Double.NaN : result.timeMs()));
        }

        List<SliderVisual> visibleSliders = new ArrayList<>();
        for (SliderRuntime slider : sliders) {
            double spawnAt = slider.object.timeMs() - preemptMs;
            if (now < spawnAt || now > Math.max(slider.timing.endTimeMs() + 240, slider.headJudgementTimeMs + 240)) continue;
            double progress = slider.timing.progressAt(now);
            BeatmapPoint ball = slider.path.positionAt(progress);
            slider.ballRotationDegrees = SliderBallRotation.at(slider.path, slider.timing, now, slider.ballRotationDegrees);
            double approachProgress = GameplayVisualTiming.approachProgress(
                    now, slider.object.timeMs(), preemptMs);
            List<SliderVisual.RepeatMarker> repeats = new ArrayList<>();
            List<SliderVisual.TickMarker> ticks = new ArrayList<>();
            for (int index = 0; index < slider.events.size(); index++) {
                SliderEvent event = slider.events.get(index);
                if (event.type() == SliderEvent.Type.REPEAT) {
                    int repeatIndex = event.spanIndex();
                    SliderNestedVisualTiming visualTiming = SliderNestedVisualTiming.endCircle(
                            slider.object.timeMs(), preemptMs, slider.timing.spanDurationMs(), event.timeMs(),
                            repeatIndex, ApproachTimeCalculator.fadeInMs(preemptMs), true);
                    SliderNestedVisualTiming reverseArrowTiming = SliderNestedVisualTiming.reverseArrow(
                            slider.object.timeMs(), preemptMs, slider.timing.spanDurationMs(), event.timeMs(),
                            repeatIndex, true);
                    double snake = GameplayVisualTiming.sliderSnakeProgress(now, slider.object.timeMs(), preemptMs);
                    double arrowProgress = event.pathProgress() == 1 ? snake : 0;
                    BeatmapPoint arrowPosition = slider.path.positionAt(arrowProgress);
                    double arrowAngle = ReverseArrowDirection.at(slider.path, arrowProgress, repeatIndex % 2 == 0);
                    repeats.add(new SliderVisual.RepeatMarker(arrowPosition,
                            repeatIndex, slider.eventJudged[index], event.timeMs(), visualTiming,
                            reverseArrowTiming, slider.eventHit[index], slider.eventJudgementTimes[index], arrowAngle));
                } else if (event.type() == SliderEvent.Type.TICK) {
                    SliderNestedVisualTiming visualTiming = SliderNestedVisualTiming.tick(
                            slider.object.timeMs(), preemptMs, slider.timing.spanDurationMs(),
                            event.spanIndex(), event.timeMs());
                    ticks.add(new SliderVisual.TickMarker(slider.path.positionAt(event.pathProgress()),
                            event.timeMs(), slider.eventJudged[index], slider.eventHit[index], visualTiming, slider.eventJudgementTimes[index]));
                }
            }
            ComboInfo combo = comboInfo.getOrDefault(slider.object, new ComboInfo(1, 0));
            visibleSliders.add(new SliderVisual(slider.path.sampledPoints(),
                    stacking.position(slider.object), slider.path.positionAt(slider.timing.endProgress()),
                    ball, repeats, circleRadius, GameplayVisualTiming.approachRadius(circleRadius, approachProgress), progress,
                    slider.object.timeMs(), slider.timing.endTimeMs(), slider.headJudged, slider.headHit,
                    slider.tracking, preemptMs, combo.number(),
                    slider.headJudgementTimeMs, combo.colorIndex(), ticks, slider.timing.velocity(), beatmapIndices.get(slider.object),
                    slider.ballRotationDegrees, slider.followEvents));
        }

        List<SpinnerVisual> visibleSpinners = new ArrayList<>();
        for (SpinnerRuntime spinner : spinners) {
            double spawnAt = spinner.object.timeMs() - preemptMs;
            if (now < spawnAt || now > spinner.object.endTimeMs() + 320) continue;
            visibleSpinners.add(new SpinnerVisual(spinner.object.x(), spinner.object.y(), SPINNER_ACTIVE_RADIUS,
                    spinner.progress(), spinner.rotation.visualRotationDegrees(),
                    spinner.rotation.totalRotationDegrees(), spinner.rotation.completedSpins(),
                    spinner.requirements.spinsRequired(), spinner.object.timeMs(), spinner.object.endTimeMs(),
                    spinner.tracking, spinner.judgement, preemptMs, spinner.rotation.spinsPerMinute(now),
                    spinner.completedAtMs, spinner.bonusScore(), beatmapIndices.get(spinner.object)));
        }
        judgementVisuals.removeIf(visual -> now - visual.timeMs() > 900);
        Map<Integer, HitObjectVisual> visibleObjects = new HashMap<>();
        visibleCircles.forEach(v -> visibleObjects.put(v.beatmapIndex(), v));
        visibleSliders.forEach(v -> visibleObjects.put(v.beatmapIndex(), v));
        visibleSpinners.forEach(v -> visibleObjects.put(v.beatmapIndex(), v));
        List<HitObjectVisual> drawOrder = new ArrayList<>();
        for (int index : visualOrder) {
            HitObjectVisual visual = visibleObjects.get(index);
            if (visual != null) drawOrder.add(visual);
        }
        return new GameplayState(now, visibleCircles, visibleSliders, visibleSpinners, score.snapshot(),
                allJudged(), judgementVisuals, drawOrder, hud.at(now), songProgress);
    }

    private void recordVisualJudgement(HitObject object, Judgement judgement, double timeMs) {
        if (object.type() == HitObject.Type.CIRCLE) circleResults.put(object, new CircleResult(judgement, timeMs));
        BeatmapPoint position = stacking.position(object);
        JudgementVisual.Kind kind = switch (object.type()) {
            case CIRCLE -> JudgementVisual.Kind.CIRCLE;
            case SLIDER -> JudgementVisual.Kind.SLIDER_HEAD;
            case SPINNER -> JudgementVisual.Kind.SPINNER;
            default -> JudgementVisual.Kind.CIRCLE;
        };
        recordVisualJudgement(position.x(), position.y(), circleRadius, judgement, timeMs,
                kind, comboInfo.getOrDefault(object, new ComboInfo(1, 0)).colorIndex());
    }

    private void emitHitSound(HitObject object, double timeMs) {
        TimingPoint timing = timingAt(object.timeMs());
        emitAudioCue(timeMs, OsuHitsoundSamples.hit(object.hitSound(), timing), timing);
    }

    private void emitAudioCue(double timeMs, List<String> samples, TimingPoint timing) {
        audioCues.add(new GameplayAudioCue(Math.round(timeMs), samples, OsuHitsoundSamples.volume(timing)));
    }

    private TimingPoint timingAt(double timeMs) {
        TimingPoint active = null;
        for (TimingPoint point : timingPoints) {
            if (point.timeMs() > timeMs) break;
            active = point;
        }
        return active;
    }

    private void recordVisualJudgement(double x, double y, double radius, Judgement judgement, double timeMs,
                                       JudgementVisual.Kind kind, int comboColorIndex) {
        judgementVisuals.add(new JudgementVisual(x, y, radius, judgement, (long) Math.round(timeMs),
                kind, comboColorIndex));
    }

    private Map<HitObject, ComboInfo> comboInformation(List<HitObject> hitObjects) {
        Map<HitObject, ComboInfo> information = new IdentityHashMap<>();
        List<HitObject> ordered = hitObjects.stream()
                .filter(object -> object.type() == HitObject.Type.CIRCLE
                        || object.type() == HitObject.Type.SLIDER || object.type() == HitObject.Type.SPINNER)
                .sorted(Comparator.comparingLong(HitObject::timeMs))
                .toList();
        int comboNumber = 0;
        int colorIndex = -1;
        boolean afterSpinner = false;
        for (HitObject object : ordered) {
            if (object.type() == HitObject.Type.SPINNER) {
                afterSpinner = true;
                continue;
            }
            if (comboNumber == 0 || afterSpinner || (object.rawType() & 4) != 0) {
                comboNumber = 1;
                colorIndex += 1 + ((object.rawType() >> 4) & 7);
            } else comboNumber++;
            information.put(object, new ComboInfo(comboNumber, colorIndex));
            afterSpinner = false;
        }
        return information;
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
                hud.changed(score.snapshot(), now);
                recordVisualJudgement(circle, Judgement.MISS, now);
            }
        }
        for (SliderRuntime slider : sliders) {
            if (slider.object.timeMs() >= target.timeMs()) break;
            if (!slider.headJudged) {
                slider.headJudged = true;
                slider.headJudgementTimeMs = now;
                score.record(Judgement.MISS);
                hud.changed(score.snapshot(), now);
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
        private final double[] eventJudgementTimes;
        private final List<FollowCircleAnimation.Event> followEvents = new ArrayList<>();
        private double ballRotationDegrees;
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
            this.eventJudgementTimes = new double[events.size()];
        }
    }

    private final class SpinnerRuntime {
        private final HitObject object;
        private final SpinnerRequirements requirements;
        private final SpinnerRotationTracker rotation;
        private int scoredSpins;
        private boolean tracking;
        private Judgement judgement;
        private long completedAtMs = Long.MIN_VALUE;

        private SpinnerRuntime(HitObject object, double overallDifficulty) {
            this.object = object;
            this.requirements = SpinnerRequirements.calculate(object.durationMs(), overallDifficulty);
            this.rotation = new SpinnerRotationTracker(object.x(), object.y());
        }

        private long bonusScore() {
            int small = Math.min(scoredSpins, requirements.spinsRequiredForBonus());
            int large = Math.min(Math.max(0, scoredSpins - small), requirements.maximumBonusSpins());
            return (long) small * OsuScoreEvent.SPINNER_SPIN.baseScore()
                    + (long) large * OsuScoreEvent.SPINNER_BONUS.baseScore();
        }

        private double progress() {
            if (requirements.spinsRequired() == 0) return 1;
            return rotation.totalRotationDegrees() / (requirements.spinsRequired() * 360.0);
        }
    }

    private record CircleResult(Judgement judgement, double timeMs) { }

    private record Candidate(int index, double offsetMs) {
    }

    private record ComboInfo(int number, int colorIndex) {
    }
}
