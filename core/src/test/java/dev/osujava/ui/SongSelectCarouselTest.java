package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectCarouselTest {
    private List<SongSelectCarousel.Entry> entries(int count) {
        return IntStream.range(0, count).mapToObj(i -> new SongSelectCarousel.Entry("set#" + i, i, 0, "family" + i, false)).toList();
    }
    private SongSelectCarousel model(int count) {
        var model = new SongSelectCarousel();
        model.content(entries(count), 620, 76, 72, "set#0");
        return model;
    }
    private void settle(SongSelectCarousel model) { for (int i = 0; i < 120; i++) model.advance(1f / 60, null); }

    @Test void selectionChangesTargetWithoutMovingLogicalRowsOrTeleportingViewport() {
        var model = model(20); model.keyboardNavigation();
        var rows = model.rows();
        float y = rows.get(12).logicalY;
        model.select("set#12");
        assertSame(rows, model.rows());
        assertEquals(y, rows.get(12).logicalY);
        assertEquals(0, model.scrollOffset());
        assertEquals(12 * 72 - 20 * 620f / 480, model.scrollTarget(), .001);
        model.advance(1f / 60, null);
        assertTrue(model.scrollOffset() > 0 && model.scrollOffset() < model.scrollTarget());
        settle(model);
        assertEquals(373.83333, model.renderY(rows.get(12), 658) + model.rowHeight() / 2, .01);
    }
    @Test void selectionTracksY220ExceptFirstRowClampedAtY200() {
        var model = model(25); model.keyboardNavigation();
        model.select("set#24"); settle(model);
        assertEquals(model.maxScroll() - 20 * 620f / 480, model.scrollOffset(), .01);
        assertEquals(284.16667, model.rows().get(24).logicalY - model.scrollOffset(), .01);
        model.select("set#0"); settle(model);
        assertEquals(0, model.scrollOffset(), .01);
    }
    @Test void expansionPreservesClickedSetPositionBeforeSmoothCentering() {
        var model = model(3);
        model.content(List.of(new SongSelectCarousel.Entry("first#-1",0,-1),
                new SongSelectCarousel.Entry("second#-1",1,-1), new SongSelectCarousel.Entry("third#-1",2,-1)),620,76,72,"first#-1");
        float oldY = model.renderY(model.rows().get(1), 620);
        model.content(List.of(new SongSelectCarousel.Entry("first#-1",0,-1),
                new SongSelectCarousel.Entry("second#0",1,0),new SongSelectCarousel.Entry("second#1",1,1),
                new SongSelectCarousel.Entry("third#-1",2,-1)),620,76,72,"second#0");
        assertEquals(oldY, model.renderY(model.rows().get(1), 620));
        assertNotEquals(model.scrollOffset(), model.scrollTarget());
    }
    @Test void hoverMovesLeftAndSeparatesNeighboursWithoutChangingLogicalY() {
        var model = model(5);
        var above = model.rows().get(1); var hovered = model.rows().get(2); var below = model.rows().get(3);
        float oldX = model.renderX(hovered,1280), oldY = above.logicalY;
        model.advance(.016f,"set#2");
        assertTrue(model.renderX(hovered,1280) < oldX);
        assertTrue(above.separationY > 0); assertTrue(below.separationY < 0);
        assertEquals(0,hovered.separationY); assertEquals(oldY,above.logicalY);
        assertTrue(hovered.hoverAmount > 0 && hovered.hoverAmount < 1);
    }
    @Test void missingCandidateClearsHoverImmediatelyAndVisualsEaseBack() {
        var model = model(5);
        model.advance(.05f,"set#2");
        float amount = model.rows().get(2).hoverAmount;
        model.advance(.025f,null);
        assertNull(model.hoverKey());
        assertTrue(model.rows().get(2).hoverAmount < amount);
        model.advance(.016f,"set#3");
        assertTrue(model.rows().get(2).hoverAmount > 0);
        assertTrue(model.rows().get(3).hoverAmount > 0);
        settle(model);
        assertEquals(0,model.rows().get(2).hoverAmount,.001);
        assertEquals(0,model.rows().get(1).separationY,.001);
    }
    @Test void shrinkingAndEmptyContentClampOffsetAndTarget() {
        var model = model(30); model.select("set#29"); settle(model);
        model.content(entries(2),620,76,72,"set#1");
        assertTrue(model.scrollOffset() <= model.maxScroll());
        assertTrue(model.scrollTarget() <= model.maxScroll());
        model.content(List.of(),620,76,72,null);
        assertEquals(0,model.scrollOffset()); assertEquals(0,model.scrollTarget()); assertEquals(0,model.maxScroll());
        model.content(entries(1),620,76,72,"set#0"); settle(model);
        assertEquals(258.33333,model.rows().get(0).logicalY - model.scrollOffset(), .001);
    }
    @Test void viewportResizeScalesExistingTravelAndRetainsHover() {
        var model = model(8); model.select("set#4"); settle(model); model.advance(.1f,"set#4");
        float hover = model.rows().get(4).hoverAmount, offset = model.scrollOffset();
        model.content(entries(8),420,90,93,"set#4");
        assertEquals(hover,model.rows().get(4).hoverAmount);
        settle(model);
        assertEquals(offset * 420 / 620, model.scrollOffset(), .01);
    }
    @Test void continuousCurveIsSymmetricAndGradualWithMoreThanThreeOffsets() {
        float previous = SongSelectCarousel.curveX(0,1280);
        for (float d : new float[]{.1f,.2f,.4f,.6f,.8f,1f}) {
            float x = SongSelectCarousel.curveX(d,1280);
            assertTrue(x > previous); assertEquals(x,SongSelectCarousel.curveX(-d,1280)); previous = x;
        }
        assertTrue(Math.abs(SongSelectCarousel.curveX(.5001f,1280) - SongSelectCarousel.curveX(.5f,1280)) < .1f);
    }
    @Test void skinSizeRespectsLogicalAspectAndDensityWithSafeBounds() {
        assertEquals(100,SongSelectCarousel.skinRowHeight(700,117,700f / 117 * 100),.001);
        assertEquals(SongSelectCarousel.skinRowHeight(700,117,600),SongSelectCarousel.skinRowHeight(1400 / 2f,234 / 2f,600));
        assertEquals(110,SongSelectCarousel.skinRowHeight(300,100,600));
        assertEquals(68,SongSelectCarousel.skinRowHeight(1200,100,600));
        for (float[] bad : new float[][]{{1,1},{2048,1},{1,2048},{0,0},{Float.NaN,1}})
            assertEquals(100,SongSelectCarousel.skinRowHeight(bad[0],bad[1],600));
    }
    @Test void interpolationIsIndependentOfFrameRateAndIgnoresInvalidTime() {
        var a = model(15); var b = model(15); a.select("set#14"); b.select("set#14");
        for (int i=0;i<30;i++) a.advance(1f/30,null);
        for (int i=0;i<120;i++) b.advance(1f/120,null);
        assertEquals(a.scrollOffset(),b.scrollOffset(),.01);
        float offset = a.scrollOffset(); a.advance(Float.NaN,null); a.advance(-1,null);
        assertEquals(offset,a.scrollOffset());
    }
    @Test void wheelTargetIsSmoothClampedAndDoesNotChangeSelectionOrLogicalRows() {
        var model=model(12); var rows=model.rows();
        model.scrollBy(38);
        assertEquals(38,model.scrollTarget()); assertEquals(0,model.scrollOffset());
        model.advance(1f/60,null);
        assertTrue(model.scrollOffset()>0 && model.scrollOffset()<38);
        assertSame(rows,model.rows()); assertEquals(1,rows.get(0).selectedAmount);
        model.scrollBy(10000); assertEquals(model.maxScroll(),model.scrollTarget());
        model.scrollBy(-10000); assertEquals(0,model.scrollTarget());
        model.scrollBy(Float.NaN); model.scrollBy(Float.POSITIVE_INFINITY);
        assertEquals(0,model.scrollTarget());
        model.select("set#4"); assertEquals(4*72 - 20 * 620f / 480,model.scrollTarget(), .001);
    }

    @Test void expansionUsesExplicitFamilyEvenWhenPersistentPathsHaveDifferentHashes() {
        var m = new SongSelectCarousel();
        var first = new SongSelectCarousel.Entry("a#folder/easy.osu", 0, -1, "set-a", false);
        var other = new SongSelectCarousel.Entry("b#folder/normal.osu", 1, 0, "set-b", false);
        m.content(List.of(first, other), 620, 76, 72, other.key());
        settle(m);
        float oldY = m.renderY(m.rows().getFirst(), 658);
        var child = new SongSelectCarousel.Entry("completely#different#path/hard.osu", 0, 1, "set-a", true);
        m.content(List.of(new SongSelectCarousel.Entry(first.key(), 0, 0, "set-a", true), child, other),
                620, 76, 72, child.key());
        assertEquals(oldY, m.renderY(m.rows().get(1), 658), .001);
    }
    @Test void adjacentGroupCardsHaveFullRowPitchAndAGroupBoundaryAddsTenUnits() {
        var m = new SongSelectCarousel();
        m.content(List.of(new SongSelectCarousel.Entry("group:A", -1, -2),
                new SongSelectCarousel.Entry("group:B", -1, -2),
                new SongSelectCarousel.Entry("b", 0, 0),
                new SongSelectCarousel.Entry("group:C", -1, -2)), 620, 72, 72, "group:A");
        assertEquals(72, m.rows().get(1).logicalY - m.rows().get(0).logicalY, .001);
        assertEquals(72 + 10 * 620f / 480, m.rows().get(2).logicalY - m.rows().get(1).logicalY, .001);
        assertEquals(72 + 10 * 620f / 480, m.rows().get(3).logicalY - m.rows().get(2).logicalY, .001);
    }

    @Test void focusColourTransitionTakesFiftyMillisecondsAndRestartsFromInterruptedValue() {
        var m = model(3); var row = m.rows().get(1);
        m.focus("set#1"); m.advance(.025f, null);
        assertEquals(.5f, row.focusAmount, .0001f);
        m.focus(null); m.advance(.025f, null);
        assertEquals(.25f, row.focusAmount, .0001f);
        m.advance(.025f, null); assertEquals(0, row.focusAmount, .0001f);
        m.focus("set#1"); m.advance(.05f, null); assertEquals(1, row.focusAmount, .0001f);
        m.content(entries(3),620,76,72,"set#0");
        assertEquals(1, m.rows().get(1).focusAmount, .0001f);
        m.focus(null); m.advance(.05f, null); assertEquals(0, m.rows().get(1).focusAmount, .0001f);
    }
    @Test void focusingAndScrollingToAnotherRowDoesNotGiveItSelectedEmphasis() {
        var m = model(6); m.keyboardNavigation();
        m.select("set#4"); m.focus("set#4"); m.emphasize("set#0");
        settle(m);
        assertEquals(1, m.rows().get(4).focusAmount, .0001f);
        assertEquals(0, m.rows().get(4).selectedAmount, .0001f);
        assertEquals(1, m.rows().get(0).selectedAmount, .0001f);
        assertEquals(4 * 72 - 20 * 620f / 480, m.scrollTarget(), .001f);
    }

}
