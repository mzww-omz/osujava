package dev.osujava.library;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.DifficultySettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeatmapLibraryTest {
    @Test void snapshotAndRevisionChangeOnlyAfterSuccessfulStorage() {
        boolean[] fail = {false};
        var library = new BeatmapLibrary(new BeatmapLibraryStorage() {
            public List<BeatmapSet> load() { return List.of(); }
            public void save(BeatmapSet set) throws java.io.IOException {
                if (fail[0]) throw new java.io.IOException("disk full");
            }
        });
        assertSame(library.all(),library.all()); assertEquals(0,library.revision());
        library.add(set("a","first")); var first = library.all();
        assertSame(first,library.all()); assertEquals(1,library.revision());
        fail[0] = true;
        assertThrows(LibraryStorageException.class,() -> library.add(set("a","replacement")));
        assertSame(first,library.all()); assertEquals(1,library.revision());
        fail[0] = false; library.add(set("a","replacement"));
        assertNotSame(first,library.all()); assertEquals(2,library.revision());
        assertEquals("first",first.getFirst().title());
        assertEquals("replacement",library.all().getFirst().title());
    }
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
