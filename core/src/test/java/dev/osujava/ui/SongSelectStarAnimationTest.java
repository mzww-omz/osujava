package dev.osujava.ui;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectStarAnimationTest {
    private SongSelectRowPresentation.Stars rating(double rating) {
        return SongSelectRowPresentation.Stars.of(OptionalDouble.of(rating));
    }
    private float scale(SongSelectStarAnimation animation, int index) { return animation.snapshot().glyphs().get(index).scale(); }

    @ParameterizedTest @CsvSource({"1,false,true", "1,true,false", "2.2,false,false", "2.2,true,false", "2.7,false,false"})
    void cropModeDependsOnVersionAndBackgroundProvider(double version, boolean builtInBackground, boolean expected) {
        assertEquals(expected, SongSelectStarAnimation.cropped(version,builtInBackground));
    }
    @Test void scaleTargetsUseMinimumSizeAndIndexGrowthInsteadOfFractionalArea() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(.1),true,100,16);
        assertEquals(.3f,scale(animation,0),.00001);
        assertEquals(0,scale(animation,1));
        animation.update(rating(3.25),true,200,16);
        assertEquals(.6f,scale(animation,0),.00001);
        assertEquals(.624f,scale(animation,1),.00001);
        assertEquals(.648f,scale(animation,2),.00001);
        assertEquals(.3f,scale(animation,3),.00001);
        assertEquals(0,scale(animation,4));
        animation.update(rating(12.84),true,300,16);
        assertEquals(.816f,scale(animation,9),.00001);
        assertEquals(10,animation.snapshot().glyphs().size());
    }
    @Test void initialNegativeCachedRatingDelaysFirstScaleTo130msAndStaggersBy80ms() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(3),false,1000,16);
        animation.advance(1129); assertEquals(0,scale(animation,0));
        animation.advance(1130); assertEquals(0,scale(animation,0));
        animation.advance(1210);
        assertEquals(.35963855f,scale(animation,0),.000001);
        assertEquals(0,scale(animation,1));
        animation.advance(1380);
        assertEquals(.6526185f,scale(animation,0),.000001,"OutBack overshoot must survive");
        animation.advance(1630); assertEquals(.6f,scale(animation,0),.000001);
        assertEquals(1,animation.snapshot().backgroundOpacity());
    }
    @Test void unchangedRatingDoesNotRestartWhenSelectionOrForegroundTintChanges() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(1),false,1000,16);
        animation.update(rating(1),false,1380,16);
        assertEquals(.6526185f,scale(animation,0),.000001);
        animation.update(rating(1),false,1630,16);
        assertEquals(.6f,scale(animation,0),.000001);
    }
    @Test void increasingRatingContinuesFromDisplayedScaleWithDelayRelativeToOldRating() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(1),true,100,16);
        animation.update(rating(2),false,1000,16);
        animation.advance(1050); assertEquals(0,scale(animation,1));
        animation.advance(1300); assertEquals(.67872324f,scale(animation,1),.000001);
        animation.update(rating(1),false,1300,0);
        assertEquals(.67872324f,scale(animation,1),.000001,"Interruption uses the last displayed scale");
        animation.advance(1600); assertTrue(scale(animation,1)<0,"Shrinking OutBack also overshoots");
        animation.advance(1850); assertEquals(0,scale(animation,1));
    }
    @Test void shrinkingReversesTheOrderOfRemovedStars() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(4),true,100,16);
        animation.update(rating(1),false,1000,16);
        animation.advance(1100);
        assertTrue(scale(animation,3)<.672f);
        assertEquals(.648f,scale(animation,2),.000001);
        assertEquals(.624f,scale(animation,1),.000001);
        animation.advance(1200);
        assertTrue(scale(animation,2)<.648f);
        assertEquals(.624f,scale(animation,1),.000001);
    }
    @Test void delayOrderUsesTheOriginalDoubleRatingAcrossAnIntegerBoundary() {
        var animation = new SongSelectStarAnimation(false,40);
        var almostThree = rating(3-1e-9);
        assertEquals(1,almostThree.lastFill(),"The static float fill rounds to one, but the timing input must not");
        animation.update(almostThree,true,100,16);
        animation.update(rating(2),false,1000,0);
        assertTrue(scale(animation,2)<.648f,"floor(old-new)=0 puts this shrinking star's start 30ms before now");
    }
    @Test void cropUsesIntegerLogicalPixelsAndOutCubicOverFiveHundredMilliseconds() {
        var animation = new SongSelectStarAnimation(true,40);
        animation.update(rating(2.5),false,1000,0);
        assertEquals(0,animation.snapshot().glyphs().get(0).crop());
        animation.advance(1250);
        assertEquals(1,animation.snapshot().glyphs().get(0).crop());
        assertEquals(1,animation.snapshot().glyphs().get(1).crop());
        assertEquals(.175f,animation.snapshot().glyphs().get(2).crop(),.000001);
        assertEquals(0,animation.snapshot().glyphs().get(3).crop());
        assertEquals(.25f,animation.snapshot().backgroundOpacity(),.000001);
        animation.advance(1500);
        assertEquals(.5f,animation.snapshot().glyphs().get(2).crop(),.000001);
        assertEquals(.5f,animation.snapshot().backgroundOpacity(),.000001);
    }
    @Test void croppedTransitionStartsOneIntegerFrameBeforeNow() {
        var animation = new SongSelectStarAnimation(true,40);
        animation.update(rating(.5),false,1000,16);
        assertEquals(.025f,animation.snapshot().glyphs().get(0).crop(),.000001);
        assertEquals(16f/1016,animation.snapshot().backgroundOpacity(),.000001);
    }
    @Test void zeroDurationScaleStillHasTheNativeZeroDeltaBoundary() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(1),true,100,0);
        assertEquals(0,scale(animation,0));
        animation.advance(101); assertEquals(.6f,scale(animation,0),.000001);
    }
    @Test void unknownRatingHasNoInventedStarsAndAcquisitionFadesBackground() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(-1),false,100,0);
        assertEquals(0,animation.snapshot().backgroundOpacity());
        assertTrue(animation.snapshot().glyphs().stream().allMatch(g -> g.scale()==0));
        animation.update(rating(0),false,1000,0);
        animation.advance(1300); assertEquals(.5f,animation.snapshot().backgroundOpacity(),.00001);
        assertTrue(animation.snapshot().glyphs().stream().allMatch(g -> g.scale()==0));
        animation.update(rating(-1),false,1300,0);
        animation.advance(1600); assertEquals(.25f,animation.snapshot().backgroundOpacity(),.00001);
        animation.advance(1900); assertEquals(0,animation.snapshot().backgroundOpacity());
    }
    @Test void retirementPreservesScaleMotionWhileFadingBothLayersForThreeHundredMilliseconds() {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(1),false,1000,0);
        animation.advance(1300);
        float before=scale(animation,0);
        animation.retire(1300,0); animation.advance(1450);
        assertEquals(.5f,animation.snapshot().foregroundOpacity(),.00001);
        assertEquals(.25f,animation.snapshot().backgroundOpacity(),.00001);
        assertNotEquals(before,scale(animation,0));
        animation.advance(1600); assertEquals(0,animation.snapshot().foregroundOpacity());
        assertFalse(animation.finished(1600)); assertTrue(animation.finished(1601));
    }
    @ParameterizedTest @ValueSource(ints={30,60,144})
    void absoluteTimeCurveSurvivesDifferentFrameRatesAndSnapshotsAreImmutable(int fps) {
        var animation = new SongSelectStarAnimation(false,40);
        animation.update(rating(9.25),false,1000,0);
        var first=animation.snapshot();
        for (int frame=1;frame<=fps;frame++) animation.advance(1000+(long)(frame*1000.0/fps));
        assertEquals(.6f,scale(animation,0),.000001);
        assertTrue(scale(animation,9)>0);
        animation.advance(2350);
        assertEquals(.366f,scale(animation,9),.000001);
        assertEquals(0,first.glyphs().getFirst().scale());
        assertThrows(UnsupportedOperationException.class,() -> first.glyphs().clear());
    }
}
