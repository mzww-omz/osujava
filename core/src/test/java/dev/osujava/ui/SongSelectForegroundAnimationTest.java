package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectForegroundAnimationTest {
    @Test void groupEntryHasIndependent200And300And1000MillisecondFades() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(4, false, 1000, 0);
        assertEquals(new SongSelectForegroundAnimation.Snapshot(0, 0, 0, 50), animation.snapshot());
        animation.update(4, false, 1100, 100);
        assertEquals(.5f, animation.snapshot().baseOpacity());
        assertEquals(1f/3, animation.snapshot().detailOpacity(), .00001);
        assertEquals(.1f, animation.snapshot().thumbnailOpacity());
        assertEquals(118, animation.snapshot().thumbnailBrightness());
        animation.update(4, false, 1300, 100);
        assertEquals(1, animation.snapshot().baseOpacity());
        assertEquals(1, animation.snapshot().detailOpacity());
        assertEquals(.3f, animation.snapshot().thumbnailOpacity());
        assertEquals(255, animation.snapshot().thumbnailBrightness());
        animation.update(4, false, 2000, 100);
        assertEquals(1, animation.snapshot().thumbnailOpacity());
    }

    @Test void viewportEntryIsImmediateButZeroDeltaResetKeepsTheNativeBoundary() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(4, true, 1000, 0);
        assertEquals(0, animation.snapshot().baseOpacity());
        assertEquals(1, animation.snapshot().detailOpacity());
        assertEquals(0, animation.snapshot().thumbnailOpacity());
        animation.update(4, false, 1001, 1);
        assertEquals(SongSelectForegroundAnimation.Snapshot.VISIBLE, animation.snapshot());
        var positiveFrame = new SongSelectForegroundAnimation();
        positiveFrame.update(4, true, 1000, 16);
        assertEquals(SongSelectForegroundAnimation.Snapshot.VISIBLE, positiveFrame.snapshot());
    }

    @Test void singletonIsDarkAndCollapsedDetailsAreInitiallyInvisible() {
        var singleton = new SongSelectForegroundAnimation();
        singleton.update(2, true, 1000, 16);
        assertEquals(new SongSelectForegroundAnimation.Snapshot(1, 1, 1, 50), singleton.snapshot());
        var collapsed = new SongSelectForegroundAnimation();
        collapsed.update(1, true, 1000, 16);
        assertEquals(new SongSelectForegroundAnimation.Snapshot(1, 0, 1, 50), collapsed.snapshot());
    }

    @Test void expansionResetsOnlyTheThumbnailWhileDetailFadesFromItsDisplayedValue() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(1, true, 1000, 16);
        animation.update(3, false, 1100, 0);
        assertEquals(new SongSelectForegroundAnimation.Snapshot(1, 0, 0, 50), animation.snapshot());
        animation.update(3, false, 1250, 150);
        assertEquals(new SongSelectForegroundAnimation.Snapshot(1, .5f, .15f, 152), animation.snapshot());
        animation.update(1, false, 1250, 0);
        animation.update(1, false, 1400, 150);
        assertEquals(.25f, animation.snapshot().detailOpacity());
        assertEquals(.3f, animation.snapshot().thumbnailOpacity()); // collapse preserves the opacity transform
        assertEquals(101, animation.snapshot().thumbnailBrightness());
        animation.update(4, false, 1400, 0);
        assertEquals(.25f, animation.snapshot().detailOpacity());
        assertEquals(0, animation.snapshot().thumbnailOpacity());
        animation.update(4, false, 1550, 150);
        assertEquals(.625f, animation.snapshot().detailOpacity());
        assertEquals(178, animation.snapshot().thumbnailBrightness());
    }

    @Test void changingSelectedSiblingDoesNotRestartAnyTransforms() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(3, false, 1000, 0);
        animation.update(4, false, 1100, 100);
        assertEquals(.1f, animation.snapshot().thumbnailOpacity());
        animation.update(3, false, 1300, 200);
        assertEquals(1, animation.snapshot().detailOpacity());
        assertEquals(.3f, animation.snapshot().thumbnailOpacity());
    }

    @Test void helperStartIncludesPreviousIntegerFrameAndEventsUsePreviouslyDisplayedValues() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(1, true, 1000, 16);
        animation.update(3, false, 1100, 16);
        assertEquals(16f/316, animation.snapshot().detailOpacity(), .000001);
        assertEquals(16f/1016, animation.snapshot().thumbnailOpacity(), .000001);
        assertEquals(60, animation.snapshot().thumbnailBrightness());
        float prior = animation.snapshot().detailOpacity();
        animation.update(1, false, 1250, 0);
        assertEquals(prior, animation.snapshot().detailOpacity());
    }

    @Test void hiddenStateOverridesBaseAndDetailWithTheirSeparateDurations() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(4, true, 1000, 16);
        animation.update(0, false, 1100, 0);
        animation.update(0, false, 1250, 150);
        assertEquals(.25f, animation.snapshot().baseOpacity());
        assertEquals(.5f, animation.snapshot().detailOpacity());
        assertEquals(.25f, animation.snapshot().thumbnailOpacity());
    }

    @Test void loadCompletionReplacesExpansionFadeAndOnlyRunsOnce() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(3, false, 1000, 0);
        animation.update(3, false, 1300, 100);
        animation.thumbnailLoaded(1300, 0);
        assertEquals(0, animation.snapshot().thumbnailOpacity());
        animation.update(3, false, 1500, 100);
        assertEquals(.5f, animation.snapshot().thumbnailOpacity());
        animation.thumbnailLoaded(1500, 100);
        assertEquals(.5f, animation.snapshot().thumbnailOpacity());
        animation.update(3, false, 1700, 100);
        assertEquals(1, animation.snapshot().thumbnailOpacity());
    }

    @Test void thumbnailLoadDelaysFollowStateAndGapsResetConsecutiveDrawTime() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(1, true, 1000, 16);
        for (long now : new long[]{1000, 1100, 1200, 1300, 1499}) assertFalse(animation.requestThumbnail(now));
        assertTrue(animation.requestThumbnail(1500));
        assertTrue(animation.requestThumbnail(3000));
        var expanded = new SongSelectForegroundAnimation();
        expanded.update(4, true, 1000, 16);
        assertFalse(expanded.requestThumbnail(1000));
        assertFalse(expanded.requestThumbnail(1050));
        assertFalse(expanded.requestThumbnail(1250)); // exact 200ms gap resets
        assertFalse(expanded.requestThumbnail(1349));
        assertTrue(expanded.requestThumbnail(1350));
    }

    @Test void expansionShortensPendingLoadDelayWithoutResettingItsCounter() {
        var animation = new SongSelectForegroundAnimation();
        animation.update(1, true, 1000, 16);
        assertFalse(animation.requestThumbnail(1000));
        assertFalse(animation.requestThumbnail(1150));
        animation.update(3, false, 1150, 0);
        assertTrue(animation.requestThumbnail(1150));
    }

    @ParameterizedTest @ValueSource(ints = {30, 60, 144})
    void independentDurationsSurviveDifferentFrameRates(int fps) {
        var animation = new SongSelectForegroundAnimation();
        animation.update(4, false, 1000, 0);
        var frozen = animation.snapshot();
        for (int frame = 1; frame <= fps; frame++) animation.update(4, false, 1000 + frame * 1000L / fps, 1000 / fps);
        assertEquals(SongSelectForegroundAnimation.Snapshot.VISIBLE, animation.snapshot());
        assertEquals(0, frozen.detailOpacity());
    }
}
