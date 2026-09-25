package dev.osujava.beatmap;

/** Spinner-specific timing parsed from the .osu hit object line. */
public record SpinnerData(double endTimeMs) {
    public SpinnerData {
        if (!Double.isFinite(endTimeMs)) {
            throw new IllegalArgumentException("Spinner end time must be finite");
        }
    }
}
