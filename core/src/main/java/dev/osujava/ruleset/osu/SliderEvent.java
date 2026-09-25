package dev.osujava.ruleset.osu;

/** A judgement point nested inside an osu! Slider. */
public record SliderEvent(Type type, int spanIndex, double timeMs, double pathProgress) {
    public enum Type {
        TICK,
        REPEAT,
        TAIL
    }
}
