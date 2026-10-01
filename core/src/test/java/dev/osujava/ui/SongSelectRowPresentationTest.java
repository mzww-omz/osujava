package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectRowPresentationTest {
    private BeatmapDifficulty difficulty(String version) {
        return new BeatmapDifficulty("Title", "Artist", "Difficulty mapper", version, 0, "", "",
                DifficultySettings.defaults(), List.of(), List.of(), null, null);
    }
    private BeatmapSet set(int count) {
        return new BeatmapSet("set", "夜空 中文 별빛 ✦", "Artist", "Set mapper", null, Path.of("fallback.png"),
                java.util.stream.IntStream.range(0, count).mapToObj(i -> difficulty("Difficulty " + i)).toList(), List.of());
    }
    @Test void modeFollowsEachDifficultyAndMixedSetsDoNotPretendToBeStandard() {
        var diffs = java.util.stream.IntStream.range(0, 4).mapToObj(mode -> new BeatmapDifficulty(
                "Title", "Artist", "Mapper", "Mode " + mode, mode, "", "", null, List.of(), List.of(), null, null)).toList();
        var set = new BeatmapSet("mixed", "Title", "Artist", "Mapper", null, null, diffs, List.of());
        assertEquals(-1, SongSelectRowPresentation.content(set, null, OptionalDouble.empty()).mode());
        for (var diff : diffs)
            assertEquals(diff.mode(), SongSelectRowPresentation.content(set, diff, OptionalDouble.empty()).mode());
    }

    @Test void absentRatingNeverInventsDifficultyFromSettings() {
        var set = set(1);
        var content = SongSelectRowPresentation.content(set, set.difficulties().getFirst(), OptionalDouble.empty());
        assertFalse(content.stars().present());
        assertEquals(0, content.stars().slots());
        assertEquals("", content.stars().label());
    }
    @ParameterizedTest @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1})
    void invalidRatingsAreAbsent(double value) {
        assertFalse(SongSelectRowPresentation.Stars.of(OptionalDouble.of(value)).present());
    }
    @Test void fullAndPartialStarsUseOnlySuppliedRating() {
        var whole = SongSelectRowPresentation.Stars.of(OptionalDouble.of(5));
        assertEquals(5, whole.count()); assertEquals("5.00", whole.label());
        for (int i = 0; i < whole.count(); i++) assertEquals(1, whole.fill(i));
        var fraction = SongSelectRowPresentation.Stars.of(OptionalDouble.of(5.42));
        assertEquals(6, fraction.count()); assertEquals(.42f, fraction.fill(5), .0001f);
        assertEquals(1, fraction.fill(4)); assertEquals("5.42", fraction.label());
        var low = SongSelectRowPresentation.Stars.of(OptionalDouble.of(.65));
        assertEquals(1, low.count()); assertEquals(.65f, low.fill(0), .0001f);
    }
    @ParameterizedTest @ValueSource(doubles = {7.01, 10, 12.84, 1000, Double.MAX_VALUE})
    void highRatingsHaveBoundedCountAndMetadataLabel(double value) {
        var stars = SongSelectRowPresentation.Stars.of(OptionalDouble.of(value));
        assertTrue(stars.count() <= 10); assertEquals(1, stars.fill(0));
        assertEquals(10,stars.slots()); assertTrue(stars.label().length() <= 8);
    }
    @ParameterizedTest @ValueSource(doubles = {0, .65, 5, 9, 9.25, 10, 12.84})
    void knownRatingsHaveTenBackgroundSlotsAndForegroundSaturatesAtTen(double rating) {
        var stars = SongSelectRowPresentation.Stars.of(OptionalDouble.of(rating));
        assertEquals(10, stars.slots());
        double foreground = 0;
        for (int i = 0; i < 10; i++) foreground += stars.fill(i);
        assertEquals(Math.min(10, rating), foreground, .0001);
        assertEquals(0, stars.fill(10));
        assertEquals(0, stars.fill(-1));
        assertEquals(0, SongSelectRowPresentation.Stars.of(OptionalDouble.empty()).slots());
    }

    @Test void tenthStarCanBePartialWithoutClampingTheNumericalRating() {
        var partial = SongSelectRowPresentation.Stars.of(OptionalDouble.of(9.25));
        assertEquals(10, partial.count());
        assertEquals(.25f, partial.fill(9));
        assertEquals("9.25", partial.label());
        var extreme = SongSelectRowPresentation.Stars.of(OptionalDouble.of(12.84));
        assertEquals(1, extreme.fill(9));
        assertEquals("12.84", extreme.label());
    }
    @Test void knownZeroHasEmptyIconAndZeroLabel() {
        var stars = SongSelectRowPresentation.Stars.of(OptionalDouble.of(0));
        assertTrue(stars.present()); assertEquals(0, stars.fill(0)); assertEquals("0.00", stars.label());
    }
    @ParameterizedTest @ValueSource(ints = {1, 16})
    void setAndDifficultyContentKeepSeparateHierarchy(int count) {
        var set = set(count);
        var parent = SongSelectRowPresentation.content(set, null, OptionalDouble.of(5));
        assertEquals(set.title(), parent.title()); assertEquals("Artist // Set mapper", parent.byline());
        assertEquals("", parent.detail());
        assertFalse(parent.stars().present());
        var child = SongSelectRowPresentation.content(set, set.difficulties().getFirst(), OptionalDouble.of(5));
        assertEquals("Artist // Difficulty mapper", child.byline()); assertEquals("Difficulty 0", child.detail());
        assertEquals(set.backgroundPath(), child.thumbnail()); assertTrue(child.stars().present());
    }
    @ParameterizedTest @ValueSource(ints = {68, 76, 110})
    void geometrySeparatesNativeThumbnailEnvelopeFromRowPitch(int height) {
        for (boolean modern : new boolean[]{false, true}) {
            var geometry = SongSelectRowPresentation.geometry(607, height, 410, modern);
            assertEquals(height / 2f - .25f * height / 48,
                    geometry.thumbnailY() + geometry.thumbnailHeight() / 2, .0001);
            assertTrue(geometry.thumbnailX() + geometry.thumbnailWidth() < geometry.textX());
            assertTrue(geometry.textX() + geometry.textWidth() <= 410);
            if (modern) {
                assertEquals(4f / 3, geometry.thumbnailWidth() / geometry.thumbnailHeight(), .0001f);
                assertEquals(85.5f / 76.8f, geometry.thumbnailHeight() / height, .0001f);
                assertEquals(5.2f / 48, geometry.thumbnailX() / height, .0001f);
                assertTrue(geometry.thumbnailY() < 0); // Full artwork extends beyond the row's input pitch.
            } else assertEquals(0, geometry.thumbnailWidth());
            assertTrue(geometry.detailY() > geometry.starsY());
            assertTrue(geometry.titleY() > geometry.bylineY()); assertTrue(geometry.bylineY() > geometry.detailY());
        }
    }
    @Test void noAvailableTextWidthDoesNotForceOverlap() {
        assertEquals(0, SongSelectRowPresentation.geometry(600, 76, 100, true).textWidth());
    }
    @Test void thumbnailFadeNeverChangesRowGeometry() {
        var before = SongSelectRowPresentation.geometry(600, 76, 410, false);
        var animation = new SongSelectForegroundAnimation();
        animation.update(3, false, 1000, 0);
        animation.thumbnailLoaded(1100, 0);
        animation.update(3, false, 1300, 0);
        assertEquals(.5f, animation.snapshot().thumbnailOpacity());
        assertEquals(before, SongSelectRowPresentation.geometry(600, 76, 410, false));
    }
    @Test void missingThumbnailIsCachedWithoutLoadingOrChangingLayout() {
        var thumbnails = new BeatmapThumbnails();
        assertNull(thumbnails.get(null)); assertNull(thumbnails.get(Path.of("missing-fixture-image.png")));
        assertNull(thumbnails.get(Path.of("missing-fixture-image.png")));
        thumbnails.close(); thumbnails.close();
    }
}
