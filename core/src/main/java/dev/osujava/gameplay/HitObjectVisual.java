package dev.osujava.gameplay;

/** Immutable identity used for visual ordering only. */
public sealed interface HitObjectVisual permits HitCircleVisual, SliderVisual, SpinnerVisual {
    int beatmapIndex();
    long startTimeMs();
}
