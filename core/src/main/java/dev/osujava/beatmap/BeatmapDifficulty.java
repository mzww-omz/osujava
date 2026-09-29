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
        BeatmapTimingStatistics timingStatistics) {

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
        if (timingStatistics == null) timingStatistics = BeatmapTimingStatistics.fromObjects(timingPoints, hitObjects);
    }

    public BeatmapDifficulty withAssets(Path resolvedAudio, Path resolvedBackground) {
        return new BeatmapDifficulty(title, artist, creator, version, mode, audioFilename,
                backgroundFilename, settings, timingPoints, hitObjects, resolvedAudio, resolvedBackground, beatmapPath, previewTimeMs, metadata, timingStatistics);
    }

    public BeatmapDifficulty withAssets(Path resolvedAudio, Path resolvedBackground, Path resolvedBeatmap) {
        return new BeatmapDifficulty(title, artist, creator, version, mode, audioFilename,
                backgroundFilename, settings, timingPoints, hitObjects, resolvedAudio, resolvedBackground, resolvedBeatmap, previewTimeMs, metadata, timingStatistics);
    }
}
