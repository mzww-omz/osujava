package dev.osujava.ui;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectRowInputTest {
    private final SongSelectCarousel carousel = new SongSelectCarousel();
    private final Map<SongSelectCarousel.Row, SongSelectForegroundAnimation> foreground = new IdentityHashMap<>();
    private final SongSelectRowInput input = new SongSelectRowInput(carousel, foreground);
    private final SongSelectRow a = row("a", true, 0, 100), b = row("b", false, 1, 100);

    SongSelectRowInputTest() {
        content(List.of(entry("a", true), entry("b", true)));
        visible("a"); visible("b");
    }

    @Test void overlapUsesCreationPriorityEvenWhenTheOtherRowIsSelected() {
        assertSame(b, hit(List.of(a,b), 150, false));
        assertSame(b, hit(List.of(b,a), 150, false));
    }

    @Test void equalPriorityKeepsFirstCandidate() {
        carousel.row("b").mousePriority = carousel.row("a").mousePriority;
        assertSame(a, hit(List.of(a,b), 150, false));
        input.clear();
        assertSame(b, hit(List.of(b,a), 150, false));
    }

    @Test void fadingWinnerRetainsPreviousHoverUntilExactlyFullOpacity() {
        assertSame(a, hit(List.of(a), 150, false));
        var moved = row("a", true, 0, 300); // Previous hover no longer contains this pointer.
        fading("b", 1199);
        assertSame(moved, hit(List.of(moved,b), 150, false));
        foreground.get(carousel.row("b")).update(1, false, 1200, 0);
        assertSame(b, hit(List.of(moved,b), 150, false));
    }

    @Test void firstHoverCannotAcquireAFadingRowButZeroOpacityDoesNotWinPriority() {
        fading("b", 1199);
        assertNull(hit(List.of(b), 150, false));
        fading("b", 1000);
        assertSame(a, hit(List.of(a,b), 150, false));
    }

    @Test void currentHoverCanRemainDuringItsOwnFadeAndMissingCandidateClearsImmediately() {
        assertSame(b, hit(List.of(a,b), 150, false));
        fading("b", 1100);
        assertSame(b, hit(List.of(a,b), 150, false));
        assertNull(hit(List.of(a,b), 99, false));
        assertNull(hit(List.of(b), 150, false));
    }

    @Test void dragAndRightScrollPreserveHoverButRemovedRowsCannotBeRestored() {
        assertSame(a, hit(List.of(a), 150, false));
        assertSame(a, hit(List.of(a,b), 99, true));
        assertSame(a, hit(List.of(a,b), 150, true));
        assertNull(hit(List.of(b), 150, true));
        input.clear();
        assertNull(hit(List.of(a), 150, true));
    }

    @Test void reorderingResidentSpritesKeepsPriorityAndReturningSpritesCaptureNewDepth() {
        var originalA = carousel.row("a");
        float original = originalA.mousePriority;
        content(List.of(entry("b", true), entry("a", true)));
        assertSame(originalA, carousel.row("a"));
        assertEquals(original, originalA.mousePriority);
        assertNotEquals(-originalA.drawDepth, originalA.mousePriority);
        assertSame(b, hit(List.of(b,a), 150, false));
        content(List.of(entry("b", true), entry("a", false)));
        content(List.of(entry("b", true), entry("a", true)));
        assertEquals(-originalA.drawDepth, originalA.mousePriority);
        assertEquals(carousel.row("b").mousePriority, originalA.mousePriority);
        visible("a");
        input.clear();
        assertSame(a, hit(List.of(a,b), 150, false)); // Equal captured depths: first wins.
    }

    @Test void hiddenRowsParticipateInDepthAccumulation() {
        content(List.of(entry("a", true), entry("hidden", false), entry("b", true)));
        float depth = .6f; depth += .00003f; depth += .00003f;
        assertEquals(depth, carousel.row("b").drawDepth);
    }

    @Test void viewportAndBackgroundCanvasEdgesExcludeChromeAndOffWindowInput() {
        var canvas = new SongSelectRow(0,0,null,true,false,100,110,200,30,0,1,0,100,110,"a",false,0,
                new SongSelectChrome.Bounds(100,90,200,80));
        assertSame(canvas, input.hit(List.of(canvas), 100, 100, 400, 84, 200, false));
        assertNull(input.hit(List.of(canvas), 300, 100, 400, 84, 200, false));
        assertNull(input.hit(List.of(canvas), 100, 90, 400, 84, 200, false));
        assertSame(canvas, input.hit(List.of(canvas), 100, 170, 400, 84, 200, false));
        for (float[] point : new float[][]{{-1,100},{400,100},{150,84},{150,200}})
            assertNull(input.hit(List.of(canvas),point[0],point[1],400,84,200,false));
    }

    private SongSelectRow hit(List<SongSelectRow> rows, float x, boolean preserve) {
        return input.hit(rows,x,140,400,84,300,preserve);
    }
    private void content(List<SongSelectCarousel.Entry> entries) {
        carousel.content(entries,216,48,38,"a",384,300);
        carousel.advance(.016f,null);
    }
    private SongSelectCarousel.Entry entry(String key, boolean visible) {
        return new SongSelectCarousel.Entry(key,0,0,key,false,visible);
    }
    private void visible(String key) {
        var animation = new SongSelectForegroundAnimation(carousel.row(key).spriteGeneration);
        animation.update(1,true,16,16);
        foreground.put(carousel.row(key),animation);
    }
    private void fading(String key, long now) {
        var animation = new SongSelectForegroundAnimation(carousel.row(key).spriteGeneration);
        animation.update(1,false,1000,0);
        animation.update(1,false,now,0);
        foreground.put(carousel.row(key),animation);
    }
    private SongSelectRow row(String key, boolean selected, int index, float x) {
        return new SongSelectRow(0,0,null,selected,false,x,100,200,80,0,1,index,x,100,key,false);
    }
}
