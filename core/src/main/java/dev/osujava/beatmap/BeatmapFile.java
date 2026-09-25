package dev.osujava.beatmap;

import java.util.Objects;

public record BeatmapFile(
        int formatVersion,
        String title,
        String titleUnicode,
        String artist,
        String artistUnicode,
        String creator,
        BeatmapDifficulty difficulty) {

    public BeatmapFile {
        title = Objects.requireNonNullElse(title, "Unknown title");
        artist = Objects.requireNonNullElse(artist, "Unknown artist");
        creator = Objects.requireNonNullElse(creator, "Unknown creator");
        Objects.requireNonNull(difficulty, "difficulty");
    }

    public String displayTitle() {
        return titleUnicode == null || titleUnicode.isBlank() ? title : titleUnicode;
    }

    public String displayArtist() {
        return artistUnicode == null || artistUnicode.isBlank() ? artist : artistUnicode;
    }
}
