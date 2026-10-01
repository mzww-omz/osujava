package dev.osujava.beatmap;

import java.util.Objects;

/** Additional .osu metadata, retained per difficulty independently of the Set's display label. */
public record BeatmapMetadata(String titleUnicode, String artistUnicode, String source, String tags,
                              int beatmapId, int beatmapSetId) {
    public static final BeatmapMetadata EMPTY = new BeatmapMetadata("", "", "", "", -1, -1);

    public BeatmapMetadata {
        titleUnicode = Objects.requireNonNullElse(titleUnicode, "");
        artistUnicode = Objects.requireNonNullElse(artistUnicode, "");
        source = Objects.requireNonNullElse(source, "");
        tags = Objects.requireNonNullElse(tags, "");
    }
}
