package dev.osujava.beatmap;

public record DifficultySettings(
        double hpDrainRate,
        double circleSize,
        double overallDifficulty,
        double approachRate,
        double sliderMultiplier,
        double sliderTickRate,
        double stackLeniency,
        int formatVersion) {

    public DifficultySettings(double hpDrainRate, double circleSize, double overallDifficulty,
                              double approachRate, double sliderMultiplier, double sliderTickRate) {
        this(hpDrainRate, circleSize, overallDifficulty, approachRate, sliderMultiplier, sliderTickRate, 0.7, 14);
    }

    public static DifficultySettings defaults() {
        return new DifficultySettings(5, 5, 5, 5, 1.4, 1, 0.7, 14);
    }
}
