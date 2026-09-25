package dev.osujava.beatmap;

public record DifficultySettings(
        double hpDrainRate,
        double circleSize,
        double overallDifficulty,
        double approachRate,
        double sliderMultiplier,
        double sliderTickRate) {

    public static DifficultySettings defaults() {
        return new DifficultySettings(5, 5, 5, 5, 1.4, 1);
    }
}
