package dev.osujava.gameplay;

import java.util.List;

public record GameplayState(long currentTimeMs, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                            List<SpinnerVisual> spinners, ScoreState score, boolean completed,
                            List<JudgementVisual> judgementVisuals) {
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
    }
}
