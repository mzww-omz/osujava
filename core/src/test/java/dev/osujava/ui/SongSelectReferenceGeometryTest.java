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
    @Test void expandedSiblingsUseFixedIndentAndGapWithoutAdditionalSelectedDisplacement() {
        var model = new SongSelectCarousel();
        model.content(List.of(new SongSelectCarousel.Entry("a#-1",0,-1),
                new SongSelectCarousel.Entry("b#0",1,0), new SongSelectCarousel.Entry("b#1",1,1),
                new SongSelectCarousel.Entry("c#-1",2,-1)), 574, 80, 80 * .96f, "b#0");
        for (int i = 0; i < 120; i++) model.advance(1f/60, null);
        for (var row : model.rows()) {
            float screenDown = 658 - model.renderY(row,658) - 40;
            float shift = SongSelectMetrics.curveX(screenDown,1280,574) - model.renderX(row,1280);
            if (row.entry.difficultyIndex() >= 0) assertEquals(50 * 574f / 480, shift, .01);
            else assertEquals(0, shift, .01);
        }
        assertEquals(76.8f + 10 * 574f / 480, model.rows().get(2).logicalY - model.rows().get(1).logicalY, .001);
        assertEquals(76.8f + 10 * 574f / 480, model.rows().get(1).logicalY - model.rows().get(0).logicalY, .001);
    }
    @Test void groupDisplacementEasesFromCollapsedPositionAndSurvivesChildSelection() {
        var model = new SongSelectCarousel();
        model.content(List.of(new SongSelectCarousel.Entry("a#-1",0,-1),
                new SongSelectCarousel.Entry("b#-1",1,-1)),574,80,76.8f,"a#-1");
        model.content(List.of(new SongSelectCarousel.Entry("a#-1",0,-1),
                new SongSelectCarousel.Entry("b#0",1,0),new SongSelectCarousel.Entry("b#1",1,1)),
                574,80,76.8f,"b#0");
        assertEquals(0,model.rows().get(1).groupAmount);
        model.advance(1f/60,null);
        float amount = model.rows().get(1).groupAmount;
        assertTrue(amount > 0 && amount < 1);
        assertEquals(amount,model.rows().get(2).groupAmount);
        model.select("b#1");
        assertEquals(amount,model.rows().get(1).groupAmount);
        for (int i=0;i<180;i++) model.advance(1f/60,null);
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
        var grade = SongSelectRowPresentation.geometry(658,80,500,true,true,false,false,true);
        assertEquals(plain.textX() + 17 * 80f / 48, grade.textX(), .0001);
        assertEquals(plain.thumbnailX(),grade.thumbnailX());
        assertEquals(plain.textWidth() - 17 * 80f / 48,grade.textWidth(), .0001);
        assertTrue(plain.starsY() < plain.detailY());
        var stars = SongSelectRowPresentation.Stars.of(OptionalDouble.of(3.35));
        assertEquals(10,stars.slots()); assertEquals(0,stars.fill(8)); assertEquals(.35f,stars.fill(3), .001);
    }

    @Test void nativeOriginsUseRowStateSkinStyleAndOptionalBadgesWithoutShiftingTheThumbnail() {
        for (boolean thumbnails : new boolean[]{false, true}) for (boolean cropped : new boolean[]{false, true}) {
            var collapsed = SongSelectRowPresentation.geometry(600, 48, 500, thumbnails, false, cropped, true, true);
            var expanded = SongSelectRowPresentation.geometry(600, 48, 500, thumbnails, true, cropped, true, true);
            float column = thumbnails ? 75 : 5, style = cropped ? 15 : 5, cy = cropped ? 27 : 24;
            assertEquals(column + style + 3, collapsed.textX());
            assertEquals(column + style + 20, expanded.textX());
            assertEquals(expanded.textX() + 1, expanded.secondaryX());
            assertEquals(column + style + 1, expanded.modeX());
            assertEquals(column + style - 1, expanded.gradeX());
            assertEquals(cy + 13, expanded.modeY());
            assertEquals(cy - 14, expanded.gradeY());
            assertEquals(cy + 16, expanded.titleY());
            assertEquals(cy + 4, expanded.bylineY());
            assertEquals(cy - 7, expanded.detailY());
            assertEquals(cy - 18, expanded.starsY());
            assertEquals(collapsed.modeX(), expanded.modeX());
            assertEquals(collapsed.gradeX(), expanded.gradeX());
            assertEquals(collapsed.thumbnailX(), expanded.thumbnailX());
            var gradeOnly = SongSelectRowPresentation.geometry(600,48,500,thumbnails,true,cropped,false,true);
            assertEquals(cy, gradeOnly.gradeY());
            assertEquals(expanded.textX(), gradeOnly.textX());
        }
    }
}
