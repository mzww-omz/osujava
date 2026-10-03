package dev.osujava.score;

import java.util.List;

/** Nullable fields are uncollected, not zero. Lists are immutable and empty only after known collection. */
public record ScoreDetails(String scoringVersion, String beatmapSha256, String beatmapMd5,
                           Integer geki, Integer katu, Integer possibleCombo, Boolean perfect,
                           Boolean passed, List<HealthPoint> health, List<Integer> hitErrors, List<Integer> spinnerRpm) {
    public static final String SCORE_V1 = "stable-score-v1-local-1";
    public static final int MAX_HEALTH_POINTS = 100_000;
    public static final ScoreDetails LEGACY = new ScoreDetails("osujava-legacy-1", "", "",
            null, null, null, null, null, null, null, null);

    public record HealthPoint(float timeMs, float value) {
        public HealthPoint {
            if (!Float.isFinite(timeMs) || !Float.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Invalid health point");
        }
    }

    public ScoreDetails {
        if (scoringVersion == null || scoringVersion.isBlank() || beatmapSha256 == null || beatmapMd5 == null
                || !beatmapSha256.matches("(?:[0-9a-f]{64})?") || !beatmapMd5.matches("(?:[0-9a-f]{32})?")
                || geki != null && geki < 0 || katu != null && katu < 0 || possibleCombo != null && possibleCombo < 0)
            throw new IllegalArgumentException("Invalid score details");
        if (health != null && health.size() > MAX_HEALTH_POINTS)
            throw new IllegalArgumentException("Too many health points");
        health = health == null ? null : List.copyOf(health);
        if (health != null) {
            float previous = Float.NEGATIVE_INFINITY;
            for (HealthPoint point : health) {
                if (point.timeMs() < previous) throw new IllegalArgumentException("Health times out of order");
                previous = point.timeMs();
            }
        }
        hitErrors = hitErrors == null ? null : List.copyOf(hitErrors);
        spinnerRpm = spinnerRpm == null ? null : List.copyOf(spinnerRpm);
    }

    /** Stable saved-score browsing cannot reconstruct hit-error / RPM samples from judgement totals. */
    public ScoreDetails forStorage() {
        if (hitErrors == null && spinnerRpm == null) return this;
        return new ScoreDetails(scoringVersion, beatmapSha256, beatmapMd5, geki, katu, possibleCombo,
                perfect, passed, health, null, null);
    }
}
