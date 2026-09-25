package dev.osujava.gameplay;

/** A rendered result emitted by gameplay at the same time and position as its existing judgement. */
public record JudgementVisual(double x, double y, double radius, Judgement judgement, long timeMs) {
}
