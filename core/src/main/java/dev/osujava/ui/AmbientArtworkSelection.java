package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapSet;
import java.nio.file.Files;
import java.util.List;
import java.util.random.RandomGenerator;

/** No shared selection/history exists yet. Choose once per screen, preferring usable local artwork. */
final class AmbientArtworkSelection {
    private AmbientArtworkSelection() { }
    static BeatmapSet choose(List<BeatmapSet> sets, RandomGenerator random) {
        List<BeatmapSet> artwork = sets.stream().filter(set ->
                set.backgroundPath() != null && Files.isRegularFile(set.backgroundPath())
                || set.difficulties().stream().anyMatch(d -> d.backgroundPath() != null && Files.isRegularFile(d.backgroundPath())))
                .toList();
        List<BeatmapSet> candidates = artwork.isEmpty() ? sets : artwork;
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }
}
