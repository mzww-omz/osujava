package dev.osujava.library;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.DifficultySettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeatmapLibraryTest {
    @Test
    void registersSetsByIdAndReturnsAnImmutableSnapshot() {
        BeatmapLibrary library = new BeatmapLibrary();
        BeatmapSet first = set("first", "Song A");
        BeatmapSet second = set("second", "Song B");
        library.add(first);
        library.add(second);

        List<BeatmapSet> snapshot = library.all();
        assertEquals(List.of(first, second), snapshot);
        assertEquals(2, library.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(first));
    }

    private BeatmapSet set(String id, String title) {
        BeatmapDifficulty difficulty = new BeatmapDifficulty(title, "Artist", "Creator", "Normal", 0,
                "", "", DifficultySettings.defaults(), List.of(), List.of(), null, null);
        return new BeatmapSet(id, title, "Artist", "Creator", null, null, List.of(difficulty), List.of());
    }
}
