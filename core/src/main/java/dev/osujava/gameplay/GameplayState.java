package dev.osujava.gameplay;

import java.util.List;

public record GameplayState(long currentTimeMs, List<HitCircleVisual> circles, ScoreState score, boolean completed) {
    public GameplayState {
        circles = List.copyOf(circles);
    }
}
