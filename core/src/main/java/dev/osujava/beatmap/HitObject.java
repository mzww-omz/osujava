package dev.osujava.beatmap;

public record HitObject(
        double x,
        double y,
        long timeMs,
        Type type,
        int rawType,
        int hitSound,
        SliderData sliderData,
        SpinnerData spinnerData) {

    public HitObject(double x, double y, long timeMs, Type type, int rawType, int hitSound, SliderData sliderData) {
        this(x, y, timeMs, type, rawType, hitSound, sliderData, null);
    }

    public HitObject(double x, double y, long timeMs, Type type, int rawType, int hitSound) {
        this(x, y, timeMs, type, rawType, hitSound, null, null);
    }

    public double endTimeMs() {
        return spinnerData == null ? timeMs : Math.max(timeMs, spinnerData.endTimeMs());
    }

    public double durationMs() {
        return endTimeMs() - timeMs;
    }

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
