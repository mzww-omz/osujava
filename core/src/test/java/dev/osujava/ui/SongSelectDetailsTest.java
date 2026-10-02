package dev.osujava.ui;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectDetailsTest {
    @Test void selectedDifficultyOwnsTitleArtistAndMapperInsteadOfTheSetSummary() {
        var selected = SongBrowserModelTest.set("a","Selected title","Selected artist","Selected mapper",120,100000)
                .difficulties().getFirst();
        var set = new dev.osujava.beatmap.BeatmapSet("a","Set title","Set artist","Set mapper",null,null,
                java.util.List.of(selected),java.util.List.of());
        var details = SongSelectDetails.of(set,selected,SongSelectRowPresentation.Stars.of(OptionalDouble.empty()));
        assertEquals("Selected artist - Selected title [Extra 星]",details.title());
        assertEquals("Mapped by Selected mapper",details.mapper());
        var row = SongSelectRowPresentation.content(set,selected,OptionalDouble.empty());
        assertEquals("Selected title",row.title()); assertEquals("Selected artist // Selected mapper",row.byline());
    }
    @Test void selectedMetadataUsesLibraryEndTimeAndNeverInventsStars() {
        var set = SongBrowserModelTest.set("a", "夜の星", "Artist", "Mapper", 180, 65000);
        var absent = SongSelectRowPresentation.Stars.of(OptionalDouble.empty());
        var details = SongSelectDetails.of(set, set.difficulties().getFirst(), absent);
        assertEquals("Artist - 夜の星 [Extra 星]", details.title());
        assertTrue(details.mapper().contains("Mapped by Mapper"));
        assertEquals("Length 1:06    BPM 180    Objects 2", details.summary());
        assertTrue(details.stats().contains("Circles 2"));
        assertEquals("Local beatmap", details.status());
    }
    @Test void emptyChartHasSafeDetails() {
        var set = SongBrowserModelTest.set("a", "Title", "Artist", "Mapper", 0, -1);
        var rating = SongSelectRowPresentation.Stars.of(OptionalDouble.of(3.5));
        var details = SongSelectDetails.of(set, set.difficulties().getFirst(), rating);
        assertEquals("Length 0:00    BPM —    Objects 0", details.summary());
        assertEquals("Local beatmap    Stars 3.50", details.status());
    }

    @Test void displayedTempoRangeIncludesCommonTempoAndIgnoresChangesAfterTheLastObject() throws Exception {
        var diff = new dev.osujava.beatmap.parse.BeatmapFileParser().parse("""
                osu file format v14
                [TimingPoints]
                0,500,4,0,0,100,1,0
                50000,250,4,0,0,100,1,0
                90000,100,4,0,0,100,1,0
                [HitObjects]
                256,192,10000,1,0
                256,192,66999,1,0
                """, "tempo.osu").difficulty();
        var set = new dev.osujava.beatmap.BeatmapSet("tempo","Title","Artist","Mapper",null,null,java.util.List.of(diff),java.util.List.of());
        var details = SongSelectDetails.of(set,diff,SongSelectRowPresentation.Stars.of(OptionalDouble.empty()));
        assertEquals("Length 1:06    BPM 120–240 (120)    Objects 2",details.summary());
    }
}
