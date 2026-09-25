package dev.osujava.beatmap;

public record TimingPoint(
        double timeMs,
        double beatLength,
        int meter,
        int sampleSet,
        int sampleIndex,
        int volume,
        boolean uninherited,
        int effects) {
}
