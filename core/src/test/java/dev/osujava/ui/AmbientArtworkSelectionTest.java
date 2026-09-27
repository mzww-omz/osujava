package dev.osujava.ui;

import dev.osujava.beatmap.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AmbientArtworkSelectionTest {
    @TempDir Path temporary;
    private BeatmapSet set(String title, Path artwork, Path difficultyArtwork) {
        var difficulty = new BeatmapDifficulty(title,"Artist","Mapper","Normal",0,"","",null,List.of(),List.of(),null,difficultyArtwork);
        return new BeatmapSet(title,title,"Artist","Mapper",null,artwork,List.of(difficulty),List.of());
    }
    @Test void artworkWinsOverAlphabeticalMinimumAndMissingPaths() throws Exception {
        Path image = Files.createFile(temporary.resolve("background.png"));
        BeatmapSet empty = set("A",null,null), missing = set("B",temporary.resolve("missing.png"),null);
        BeatmapSet valid = set("Z",null,image);
        assertSame(valid,AmbientArtworkSelection.choose(List.of(empty,missing,valid),new Random(4)));
        assertNull(AmbientArtworkSelection.choose(List.of(),new Random(4)));
    }
    @Test void fixedRandomProducesReproducibleOneTimeSelectionAndNoArtworkIsAllowed() {
        var sets = List.of(set("A",null,null),set("Z",null,null));
        assertSame(AmbientArtworkSelection.choose(sets,new Random(8)),AmbientArtworkSelection.choose(sets,new Random(8)));
        assertTrue(sets.contains(AmbientArtworkSelection.choose(sets,new Random(2))));
    }
}
