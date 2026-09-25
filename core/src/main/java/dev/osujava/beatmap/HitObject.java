package dev.osujava.beatmap;

public record HitObject(
        double x,
        double y,
        long timeMs,
        Type type,
        int rawType,
        int hitSound) {

    public enum Type {
        CIRCLE,
        SLIDER,
        SPINNER,
        HOLD,
        UNKNOWN
    }

    public static Type typeFromBits(int rawType) {
        if ((rawType & 1) != 0) return Type.CIRCLE;
        if ((rawType & 2) != 0) return Type.SLIDER;
        if ((rawType & 8) != 0) return Type.SPINNER;
        if ((rawType & 128) != 0) return Type.HOLD;
        return Type.UNKNOWN;
    }
}
