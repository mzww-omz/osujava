package dev.osujava.gameplay;

/** A rendered result emitted by gameplay at the same time and position as its existing judgement. */
public record JudgementVisual(double x, double y, double radius, Judgement judgement, long timeMs,
                              Kind kind, int comboColorIndex, long effectSeed) {
    public JudgementVisual(double x, double y, double radius, Judgement judgement, long timeMs,
                           Kind kind, int comboColorIndex) {
        this(x, y, radius, judgement, timeMs, kind, comboColorIndex,
                Double.doubleToLongBits(x) ^ Long.rotateLeft(Double.doubleToLongBits(y), 23)
                        ^ timeMs * 0x9e3779b97f4a7c15L ^ kind.ordinal() * 31L ^ judgement.ordinal());
    }
    public enum Kind { CIRCLE, SLIDER_HEAD, SLIDER_TAIL, SPINNER }
}
