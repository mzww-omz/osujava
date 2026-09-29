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

    private void press(SongSelectInputController input, SongSelectCarousel motion) {
        input.buttons(false, false, false, 0);
        input.pressPointer("s0", 800, 300, false, motion);
        input.samplePointer(input.buttons(true, false, false, 0), 800, 300, 1, 533, 200, 0, motion);
    }
    private String pointer(SongSelectInputController input, SongSelectCarousel motion, boolean held,
                           boolean inside, float x, float y, float scale, float delta) {
        var buttons = input.buttons(held, false, false, 0);
        String clicked = buttons.released() ? input.releasePointer(held, inside) : null;
        input.samplePointer(buttons, x, y, scale, x / scale, y / scale, delta, motion);
        return clicked;
    }

    @Test void modeAndGradeColumnScalesWithTheRowWithoutStealingThumbnailSpace() {
        for (float height : new float[]{72, 96, 144}) for (boolean grade : new boolean[]{false, true}) {
            var row = new SongSelectRow(0, 0, null, true, false, 600, 300, 800, height, 0, 1);
            var geometry = SongSelectLayout.row(row, 0, 600, 300, 1600, 0, 1200, true, grade);
            float thumbnailEnd = geometry.thumbnail().x() + geometry.thumbnail().width();
            float textStart = row.x() + geometry.text().textX();
            assertEquals((grade ? 100 : 83) * height / 48, textStart - row.x(), .0001);
            assertTrue(textStart > thumbnailEnd);
            assertEquals(height, geometry.hit().height());
        }
    }

    @Test void dragMovesDirectlyAndReleasesSmoothedVelocityWithoutClicking() {
        var input = input(); var motion = carousel();
        motion.scrollBy(300); motion.advance(.05f, null);
        float start = motion.scrollOffset();
        press(input, motion);
        assertNull(pointer(input, motion, true, true, 800, 210, 1, .02f));
        assertEquals(start + 90, motion.scrollOffset(), .001);
        float speed = motion.scrollVelocity();
        assertTrue(speed > 0);
        motion.advance(.02f, null);
        assertEquals(start + 90, motion.scrollOffset(), .001, "Held input must not integrate free flight");
        assertEquals(speed, motion.scrollVelocity());
        assertEquals(SongSelectInputController.PointerState.DRAGGING, input.pointerState());
        assertNull(pointer(input, motion, false, true, 800, 210, 1, .02f));
        motion.advance(.02f, null);
        assertTrue(motion.scrollOffset() > start + 90);
    }
    @Test void cancellingAHeldDragDiscardsVelocityAndCannotLeaveTheViewportFrozen() {
        var input = input(); var motion = carousel();
        press(input, motion);
        pointer(input, motion, true, true, 800, 210, 1, .02f);
        float position = motion.scrollOffset();
        input.cancelPointer(); motion.advance(.2f, null);
        assertEquals(position, motion.scrollOffset()); assertEquals(0, motion.scrollVelocity());
        assertNull(pointer(input, motion, false, true, 800, 210, 1, .02f));
        motion.wheel(1); motion.advance(.02f, null);
        assertTrue(motion.scrollOffset() > position);
    }
    @Test void smallMovementCanStillClickAndCancelledPressCannotClick() {
        var input = input(); var motion = carousel();
        press(input, motion);
        assertEquals("s0", pointer(input, motion, false, true, 802, 299, 1, .02f));
        assertEquals(0, motion.scrollOffset());
        press(input, motion); input.cancelPointer();
        assertNull(pointer(input, motion, false, true, 800, 300, 1, .02f));
    }
    @Test void horizontalExcursionCancelsClickEvenIfPointerReturns() {
        var input = input(); var motion = carousel();
        press(input, motion);
        pointer(input, motion, true, true, 881, 300, 1, .02f);
        assertNull(pointer(input, motion, false, true, 800, 300, 1, .02f));
        assertEquals(0, motion.scrollOffset());
    }
    @ParameterizedTest @ValueSource(floats = {1, 1.5f, 1.0666667f, 2})
    void cancellationUsesWindowPixelsWhileDragUsesLogicalCoordinates(float scale) {
        var input = input(); var motion = carousel();
        press(input, motion);
        pointer(input, motion, true, true, 800, 220, scale, .02f);
        assertEquals(80 / scale, motion.scrollOffset(), .001);
        assertEquals("s0", pointer(input, motion, false, true, 800, 220, scale, .02f));
        press(input, motion);
        pointer(input, motion, true, true, 881, 300, scale, .02f);
        assertNull(pointer(input, motion, false, true, 800, 300, scale, .02f));
    }
    @Test void releaseAndZeroTimeSamplesCannotAddDragDistanceOrVelocity() {
        var input = input(); var motion = carousel();
        press(input, motion);
        pointer(input, motion, true, true, 800, 290, 1, 0);
        assertEquals(0, motion.scrollOffset());
        pointer(input, motion, true, true, 800, 280, 1, .02f);
        assertEquals(10, motion.scrollOffset());
        float velocity = motion.scrollVelocity();
        pointer(input, motion, false, false, 800, 200, 1, .1f);
        assertEquals(10, motion.scrollOffset());
        assertEquals(velocity, motion.scrollVelocity());
    }
    @Test void rightScrollMustBeginOffRowsAndStopsOnReleaseOrCancellation() {
        var input = input(); var motion = carousel();
        input.pressPointer("s0", 450, 400, true, motion);
        input.samplePointer(input.buttons(false, true, false, 0), 450, 400, 1, 300, 400, 0, motion);
        assertFalse(input.rightScrolling()); assertEquals(0, motion.scrollTarget());
        input.releasePointer(false, false); input.buttons(false, false, false, 0);
        input.pressPointer(null, 450, 235, true, motion);
        var both = input.buttons(true, true, false, 0);
        input.samplePointer(both, 450, 235, 1, 300, 235, 0, motion);
        assertTrue(input.rightScrolling()); assertEquals(0, motion.scrollTarget());
        input.releasePointer(false, false);
        input.samplePointer(input.buttons(false, true, false, 0), 450, 235, 1, 300, 235, 0, motion);
        assertEquals(motion.maxScroll() / 2, motion.scrollTarget(), .001);
        float velocity = motion.scrollVelocity();
        input.samplePointer(input.buttons(false, false, false, 0), 450, 400, 1, 300, 400, 0, motion);
        assertFalse(input.rightScrolling()); assertEquals(velocity, motion.scrollVelocity());
        input.pressPointer(null, 450, 400, true, motion); input.cancelPointer();
        input.samplePointer(input.buttons(false, true, false, 0), 450, 400, 1, 300, 400, 0, motion);
        assertFalse(input.rightScrolling()); assertEquals(velocity, motion.scrollVelocity());
    }
    @Test void heldScrollKeepsThePressedHoverUntilRelease() {
        var input = input(); var motion = carousel();
        press(input, motion);
        motion.advance(.2f, "s1");
        assertTrue(motion.rows().get(0).hoverAmount > .9f);
        assertEquals(0, motion.rows().get(1).hoverAmount);
        pointer(input, motion, false, false, 800, 300, 1, .02f);
        motion.advance(.2f, "s1");
        assertTrue(motion.rows().get(1).hoverAmount > .9f);
        motion.advance(.2f, null, true);
        assertTrue(motion.rows().get(1).hoverAmount > .99f);
        motion.advance(.2f, null);
        assertTrue(motion.rows().get(1).hoverAmount < .1f);
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
    void preStopIntegrationTracksElapsedTimeAcrossFrameRates(int fps) {
        var reference = carousel(); reference.scrollBy(350); reference.advance(.4f, null);
        var motion = carousel(); motion.scrollBy(350);
        for (int i = 0; i < fps; i++) motion.advance(.4f / fps, null);
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
        assertEquals(4, geometry.zOrder());
        assertEquals(geometry.inputClip(), geometry.hit());
        assertEquals(300, geometry.inputClip().width(), .001);
        assertEquals(60, geometry.inputClip().height(), .001);
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
