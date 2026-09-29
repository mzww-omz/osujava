package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectFoundationTest {
    private SongSelectCarousel carousel() {
        var motion = new SongSelectCarousel();
        motion.content(IntStream.range(0, 20).mapToObj(i -> new SongSelectCarousel.Entry("s" + i, i, -1)).toList(),
                574, 72, 72, "s0");
        return motion;
    }
    private SongSelectInputController input() {
        // Pointer tests never route a keyboard command.
        return new SongSelectInputController(new SongSelectToolboxState(), new SongBrowserControls(), null);
    }

    @Test void modeAndGradeColumnScalesWithTheRowWithoutStealingThumbnailSpace() {
        for (float height : new float[]{72, 96, 144}) for (boolean grade : new boolean[]{false, true}) {
            var row = new SongSelectRow(0, 0, null, true, false, 600, 300, 800, height, 0, 1);
            var geometry = SongSelectLayout.row(row, 0, 600, 300, 1600, 0, 1200, true, grade);
            float thumbnailEnd = geometry.thumbnail().x() + geometry.thumbnail().width();
            float textStart = row.x() + geometry.text().textX();
            assertTrue(textStart - thumbnailEnd >= (grade ? 52 : 32) * height / 72);
            assertEquals(height, geometry.hit().height());
        }
    }

    @Test void directDragCancelsSelectionSpringAndDoesNotClickOnReturn() {
        var input = input(); var motion = carousel();
        motion.scrollBy(300); motion.advance(.05f, null);
        float start = motion.scrollOffset();
        input.pressRow("s0", 800, 300);
        assertNull(input.pointer(true, "s0", 800, 330, motion));
        assertEquals(start + 30, motion.scrollOffset(), .001);
        assertEquals(motion.scrollOffset(), motion.scrollTarget());
        assertEquals(0, motion.scrollVelocity());
        assertEquals(SongSelectInputController.PointerState.DRAGGING, input.pointerState());
        assertNull(input.pointer(false, "s0", 800, 300, motion));
        assertEquals(start, motion.scrollOffset(), .001);
        motion.advance(1, null);
        assertEquals(start, motion.scrollOffset(), .001, "Old selection target must not resume after release");
    }
    @Test void clickSlopAndCancelHaveNoScrollSideEffects() {
        var input = input(); var motion = carousel();
        input.pressRow("s0", 800, 300);
        assertEquals("s0", input.pointer(false, "s0", 802, 301, motion));
        assertEquals(0, motion.scrollOffset());
        input.pressRow("s0", 800, 300); input.cancelPointer();
        assertNull(input.pointer(false, "s0", 800, 300, motion));
    }
    @Test void horizontalExcursionCancelsClickEvenIfPointerReturns() {
        var input = input(); var motion = carousel();
        input.pressRow("s0", 800, 300);
        input.pointer(true, "s0", 820, 300, motion);
        assertNull(input.pointer(false, "s0", 800, 300, motion));
        assertEquals(0, motion.scrollOffset());
    }
    @Test void directDragAndWheelRemainBoundedAtBothEnds() {
        var motion = carousel();
        motion.dragBy(-Float.MAX_VALUE); assertEquals(0, motion.scrollOffset());
        motion.dragBy(Float.MAX_VALUE); assertEquals(motion.maxScroll(), motion.scrollOffset());
        motion.dragBy(Float.NaN); assertEquals(motion.maxScroll(), motion.scrollOffset());
        motion.scrollBy(-72); motion.advance(.1f, null);
        assertTrue(motion.scrollOffset() < motion.maxScroll());
    }
    @ParameterizedTest @ValueSource(ints = {30, 60, 144})
    void scrollTracksElapsedTimeAcrossFrameRates(int fps) {
        var reference = carousel(); reference.scrollBy(350); reference.advance(1, null);
        var motion = carousel(); motion.scrollBy(350);
        for (int i = 0; i < fps; i++) motion.advance(1f / fps, null);
        assertEquals(reference.scrollOffset(), motion.scrollOffset(), .002);
    }
    @ParameterizedTest @CsvSource({"1280,720", "1920,1080", "1024,768", "2560,1440"})
    void screenRegionsAndClippedRowsAreDensityIndependent(int width, int height) {
        var ui = UiLayout.fromPixels(width, height);
        var layout = SongSelectLayout.create(ui, null);
        assertTrue(layout.viewport().height() > 0);
        assertEquals(ui.width(), layout.toolbox().chrome.width());
        assertTrue(layout.scores().bottom() >= layout.chrome().bottom());
        assertTrue(layout.search().x() >= 0);
        var row = new SongSelectRow(0, 0, null, true, false, ui.width() - 300, layout.chrome().bottom() - 20,
                600, 80, 1, 1);
        var geometry = SongSelectLayout.row(row, 4, 700, 200, ui.width(), layout.chrome().bottom(), layout.chrome().carouselTop(), true, false);
        assertEquals(4, geometry.logicalIndex());
        assertEquals(Integer.MAX_VALUE, geometry.zOrder());
        assertEquals(geometry.clip(), geometry.hit());
        assertEquals(300, geometry.clip().width(), .001);
        assertEquals(60, geometry.clip().height(), .001);
        assertEquals(80f * 5.2f / 48, geometry.thumbnail().x() - row.x(), .001);
        assertEquals(80f * 85.5f / 76.8f, geometry.thumbnail().height(), .001);
        assertEquals(80f * 114 / 76.8f, geometry.thumbnail().width(), .001);
        assertFalse(geometry.hit().contains(row.x(), row.y()));
        var sample = new SongSelectViewState(); sample.sample(ui, width / 2, height / 2, true);
        assertEquals(ui.width() / 2, sample.pointerX, .001);
        assertEquals(ui.height() / 2, sample.pointerY, .001);
    }
    @Test void headersAndInvisibleRowsNeverBecomeHitTargets() {
        for (int set : new int[]{-1, 0}) {
            var row = new SongSelectRow(set, 0, "header", false, false, 500, 300, 600, 80, 0, .01f);
            var geometry = SongSelectLayout.row(row, 0, 500, 300, 1280, 84, 650, false, false);
            assertEquals(0, geometry.hit().width());
        }
    }
}
