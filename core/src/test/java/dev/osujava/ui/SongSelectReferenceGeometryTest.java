package dev.osujava.ui;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectReferenceGeometryTest {
    @Test void chromeAndCookieRatiosStayConsistentAcrossViewports() {
        for (float height : new float[]{720,1080,1440}) {
            float bottom = SongSelectChrome.bottomHeight(height), radius = SongSelectChrome.cookieRadius(height);
            assertTrue(bottom / height > .11f && bottom / height < .13f);
            assertEquals(.27f, 2 * radius / height, .001f);
            assertTrue(SongSelectChrome.cookieY(radius) - radius < 0);
            assertTrue(SongSelectChrome.cookieY(radius) + radius > bottom);
            assertTrue(SongSelectChrome.cookieX(1280,radius) + radius > 1280);
        }
    }
    @Test void expandedGroupAdvancesTogetherWithSmallSelectedEmphasis() {
        var model = new SongSelectCarousel();
        model.content(List.of(new SongSelectCarousel.Entry("a#-1",0,-1),
                new SongSelectCarousel.Entry("b#0",1,0), new SongSelectCarousel.Entry("b#1",1,1),
                new SongSelectCarousel.Entry("c#-1",2,-1)), 574, 80, 80 * .96f, 80 * 1.02f, "b#0");
        for (int i = 0; i < 120; i++) model.advance(1f/60, null);
        for (var row : model.rows()) {
            float screenDown = 658 - model.renderY(row,658) - 40;
            float shift = SongSelectMetrics.curveX(screenDown,1280,574) - model.renderX(row,1280);
            if (row.entry.difficultyIndex() >= 0) assertTrue(shift / 1280 > .05 && shift / 1280 < .06);
            else assertEquals(0, shift, .01);
        }
        assertEquals(1.02f, (model.rows().get(2).logicalY - model.rows().get(1).logicalY) / 80, .001);
        assertEquals(.96f, (model.rows().get(1).logicalY - model.rows().get(0).logicalY) / 80, .001);
    }
    @Test void groupDisplacementEasesFromCollapsedPositionAndSurvivesChildSelection() {
        var model = new SongSelectCarousel();
        model.content(List.of(new SongSelectCarousel.Entry("a#-1",0,-1),
                new SongSelectCarousel.Entry("b#-1",1,-1)),574,80,76.8f,81.6f,"a#-1");
        model.content(List.of(new SongSelectCarousel.Entry("a#-1",0,-1),
                new SongSelectCarousel.Entry("b#0",1,0),new SongSelectCarousel.Entry("b#1",1,1)),
                574,80,76.8f,81.6f,"b#0");
        assertEquals(0,model.rows().get(1).groupAmount);
        model.advance(1f/60,null);
        float amount = model.rows().get(1).groupAmount;
        assertTrue(amount > 0 && amount < 1);
        assertEquals(amount,model.rows().get(2).groupAmount);
        model.select("b#1");
        assertEquals(amount,model.rows().get(1).groupAmount);
        for (int i=0;i<120;i++) model.advance(1f/60,null);
        assertEquals(1,model.rows().get(1).groupAmount,.001);
        assertEquals(1,model.rows().get(2).groupAmount,.001);
    }
    @Test void curveUsesConfirmedScreenRelativeLinearIndentation() {
        float width = 1280;
        assertEquals(56.25, SongSelectCarousel.curveX(1,width) - SongSelectCarousel.curveX(0,width), .001);
        assertTrue(SongSelectCarousel.curveX(.2f,width) > SongSelectCarousel.curveX(.1f,width));
        assertEquals(SongSelectCarousel.curveX(-.6f,width), SongSelectCarousel.curveX(.6f,width));
    }
    @Test void gradeSlotPrecedesTextAndStarsAreSeparateFromDifficultyWidth() {
        var plain = SongSelectRowPresentation.geometry(658,80,500,true);
        var grade = SongSelectRowPresentation.geometry(658,80,500,true,30);
        assertEquals(plain.textX() + 30, grade.textX());
        assertEquals(plain.thumbnailX(),grade.thumbnailX());
        assertEquals(plain.textWidth() - 30,grade.textWidth());
        assertTrue(plain.starsY() < plain.detailY());
        var stars = SongSelectRowPresentation.Stars.of(OptionalDouble.of(3.35));
        assertEquals(9,stars.slots()); assertEquals(0,stars.fill(8)); assertEquals(.35f,stars.fill(3), .001);
    }
}
