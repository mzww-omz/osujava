package dev.osujava.gameplay;

import java.util.List;

public record GameplayState(long currentTimeMs, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                            List<SpinnerVisual> spinners, ScoreState score, boolean completed,
                            List<JudgementVisual> judgementVisuals, List<HitObjectVisual> drawOrder,
                            LegacyHudVisual hud, LegacySongProgress songProgress) {
    public GameplayState(long time, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                         List<SpinnerVisual> spinners, ScoreState score, boolean completed,
                         List<JudgementVisual> judgements, List<HitObjectVisual> order) {
        this(time, circles, sliders, spinners, score, completed, judgements, order,
                LegacyHudVisual.immediate(score), null);
    }
    public GameplayState(long currentTimeMs, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                         List<SpinnerVisual> spinners, ScoreState score, boolean completed,
                         List<JudgementVisual> judgementVisuals) {
        this(currentTimeMs, circles, sliders, spinners, score, completed, judgementVisuals,
                defaultDrawOrder(circles, sliders, spinners));
    }

    // Compatibility for hand-built snapshots. Live sessions supply the precomputed beatmap order.
    private static List<HitObjectVisual> defaultDrawOrder(List<HitCircleVisual> circles,
            List<SliderVisual> sliders, List<SpinnerVisual> spinners) {
        var objects = new java.util.ArrayList<HitObjectVisual>();
        objects.addAll(circles);
        objects.addAll(sliders);
        objects.addAll(spinners);
        objects.sort(java.util.Comparator.comparingLong(HitObjectVisual::startTimeMs).reversed()
                .thenComparing(java.util.Comparator.comparingInt(HitObjectVisual::beatmapIndex).reversed()));
        return objects;
    }

    public GameplayState(long currentTimeMs, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                         List<SpinnerVisual> spinners, ScoreState score, boolean completed) {
        this(currentTimeMs, circles, sliders, spinners, score, completed, List.of());
    }

    public GameplayState(long currentTimeMs, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                         ScoreState score, boolean completed) {
        this(currentTimeMs, circles, sliders, List.of(), score, completed, List.of());
    }

    public GameplayState(long currentTimeMs, List<HitCircleVisual> circles, ScoreState score, boolean completed) {
        this(currentTimeMs, circles, List.of(), List.of(), score, completed, List.of());
    }

    public GameplayState {
        circles = List.copyOf(circles);
        sliders = List.copyOf(sliders);
        spinners = List.copyOf(spinners);
        judgementVisuals = List.copyOf(judgementVisuals);
        drawOrder = List.copyOf(drawOrder);
    }
}
