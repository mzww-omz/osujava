package dev.osujava.gameplay;

import java.util.List;

public record GameplayState(long currentTimeMs, List<HitCircleVisual> circles, List<SliderVisual> sliders,
                            ScoreState score, boolean completed) {
    public GameplayState(long currentTimeMs, List<HitCircleVisual> circles, ScoreState score, boolean completed) {
        this(currentTimeMs, circles, List.of(), score, completed);
    }

    public GameplayState {
        circles = List.copyOf(circles);
        sliders = List.copyOf(sliders);
    }
}
