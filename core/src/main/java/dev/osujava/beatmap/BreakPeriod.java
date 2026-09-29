package dev.osujava.beatmap;

/** Raw .osu break interval in audio milliseconds; rulesets decide its drain semantics. */
public record BreakPeriod(int startTimeMs, int endTimeMs) {
    public BreakPeriod {
        if (endTimeMs < startTimeMs) throw new IllegalArgumentException("Break ends before it starts");
    }
}
