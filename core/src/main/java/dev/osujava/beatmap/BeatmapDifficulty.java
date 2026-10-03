package dev.osujava.beatmap;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record BeatmapDifficulty(
        String title,
        String artist,
        String creator,
        String version,
        int mode,
        String audioFilename,
        String backgroundFilename,
        DifficultySettings settings,
        List<TimingPoint> timingPoints,
        List<HitObject> hitObjects,
        Path audioPath,
        Path backgroundPath,
        Path beatmapPath,
        int previewTimeMs,
        BeatmapMetadata metadata,
        BeatmapTimingStatistics timingStatistics,
        BeatmapPlayData playData,
        // Non-comment source rows omitted by the tolerant HitObject parser; retained across asset resolution.
        int skippedHitObjectCount,
        BeatmapParseIssues parseIssues) {

    /** Preserve the existing explicit HitObject omission constructor. */
    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath, int previewTimeMs,
                             BeatmapMetadata metadata, BeatmapTimingStatistics timingStatistics,
                             BeatmapPlayData playData, int skippedHitObjectCount) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, previewTimeMs,
                metadata, timingStatistics, playData, skippedHitObjectCount, BeatmapParseIssues.NONE);
    }

    /** Programmatically constructed charts have no parser omissions unless explicitly supplied. */
    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath, int previewTimeMs,
                             BeatmapMetadata metadata, BeatmapTimingStatistics timingStatistics,
                             BeatmapPlayData playData) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, previewTimeMs,
                metadata, timingStatistics, playData, 0);
    }

    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath, int previewTimeMs,
                             BeatmapPlayData playData) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, previewTimeMs,
                BeatmapMetadata.EMPTY, null, playData);
    }

    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath, int previewTimeMs,
                             BeatmapMetadata metadata, BeatmapTimingStatistics timingStatistics) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, previewTimeMs,
                metadata, timingStatistics, BeatmapPlayData.UNKNOWN);
    }

    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath, int previewTimeMs,
                             BeatmapMetadata metadata) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, previewTimeMs, metadata, null);
    }

    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath, int previewTimeMs) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, previewTimeMs, BeatmapMetadata.EMPTY);
    }

    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath, Path beatmapPath) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, beatmapPath, -1);
    }

    public BeatmapDifficulty(String title, String artist, String creator, String version, int mode,
                             String audioFilename, String backgroundFilename, DifficultySettings settings,
                             List<TimingPoint> timingPoints, List<HitObject> hitObjects,
                             Path audioPath, Path backgroundPath) {
        this(title, artist, creator, version, mode, audioFilename, backgroundFilename, settings,
                timingPoints, hitObjects, audioPath, backgroundPath, null);
    }

    public BeatmapDifficulty {
        if (skippedHitObjectCount < 0) throw new IllegalArgumentException("Skipped object count cannot be negative");
        title = Objects.requireNonNullElse(title, "Unknown title");
        artist = Objects.requireNonNullElse(artist, "Unknown artist");
        creator = Objects.requireNonNullElse(creator, "Unknown creator");
        version = Objects.requireNonNullElse(version, "Normal");
        audioFilename = Objects.requireNonNullElse(audioFilename, "");
        previewTimeMs = Math.max(-1, previewTimeMs);
        metadata = Objects.requireNonNullElse(metadata, BeatmapMetadata.EMPTY);
        backgroundFilename = Objects.requireNonNullElse(backgroundFilename, "");
        settings = Objects.requireNonNullElseGet(settings, DifficultySettings::defaults);
        timingPoints = List.copyOf(Objects.requireNonNullElse(timingPoints, List.of()));
        hitObjects = List.copyOf(Objects.requireNonNullElse(hitObjects, List.of()));
        playData = Objects.requireNonNullElse(playData, BeatmapPlayData.UNKNOWN);
        parseIssues = Objects.requireNonNullElse(parseIssues, BeatmapParseIssues.NONE);
        if (timingStatistics == null) timingStatistics = BeatmapTimingStatistics.fromObjects(timingPoints, hitObjects);
    }

    public BeatmapDifficulty withAssets(Path resolvedAudio, Path resolvedBackground) {
        return new BeatmapDifficulty(title, artist, creator, version, mode, audioFilename,
                backgroundFilename, settings, timingPoints, hitObjects, resolvedAudio, resolvedBackground, beatmapPath, previewTimeMs, metadata, timingStatistics, playData, skippedHitObjectCount, parseIssues);
    }

    public BeatmapDifficulty withAssets(Path resolvedAudio, Path resolvedBackground, Path resolvedBeatmap) {
        return new BeatmapDifficulty(title, artist, creator, version, mode, audioFilename,
                backgroundFilename, settings, timingPoints, hitObjects, resolvedAudio, resolvedBackground, resolvedBeatmap, previewTimeMs, metadata, timingStatistics, playData, skippedHitObjectCount, parseIssues);
    }
}
