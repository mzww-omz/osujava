package dev.osujava.ui;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectDetailsTest {
    @Test void selectedMetadataUsesActualObjectSpanAndNeverInventsStars() {
        var set = SongBrowserModelTest.set("a", "夜の星", "Artist", "Mapper", 180, 65000);
        var absent = SongSelectRowPresentation.Stars.of(OptionalDouble.empty());
        var details = SongSelectDetails.of(set, set.difficulties().getFirst(), absent);
        assertEquals("Artist - 夜の星 [Extra 星]", details.title());
        assertTrue(details.mapper().contains("Mapped by Mapper"));
        assertEquals("Length 1:05    BPM 180    Objects 2", details.summary());
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
}
