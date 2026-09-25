package dev.osujava.library;

import dev.osujava.beatmap.BeatmapSet;

import java.util.List;

public record ImportResult(BeatmapSet beatmapSet, List<String> warnings) {
    public ImportResult {
        warnings = List.copyOf(warnings);
    }
}
