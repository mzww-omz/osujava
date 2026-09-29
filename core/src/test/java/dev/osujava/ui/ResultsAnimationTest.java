package dev.osujava.ui;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultsAnimationTest {
    @Test void skipCompletesEveryItemAndRepeatedEnterDoesNotRequestNavigation() {
        var animation = new ResultsAnimation();
        assertEquals(0,animation.item(0,false)); assertFalse(animation.skip());
        for(int i=0;i<9;i++) { assertEquals(1,animation.item(i,false)); assertEquals(1,animation.item(i,true)); }
        assertEquals(1,animation.grade(true)); assertEquals(1,animation.graph());
        assertEquals("12345678",animation.score("12345678",new Random(1)));
        assertTrue(animation.skip());
    }
    @Test void eachDigitLocksAt500msAndUnfixedCharactersNeverContainNine() {
        var animation = new ResultsAnimation(); var random = new Random(1);
        animation.advance(.8f);
        String value = animation.score("99999999",random);
        assertEquals('9',value.charAt(0));
        assertFalse(value.substring(1).contains("9"));
        animation.advance(3.5f); assertEquals("99999999",animation.score("99999999",random));
    }
    @Test void imageAndNumberUseVerifiedQuadraticEasingAndIndependentStartTimes() {
        var image = new ResultsAnimation(); image.advance(.75f);
        assertEquals(.25f,image.item(0,false),.000001);
        assertEquals(0,image.item(0,true));
        var number = new ResultsAnimation(); number.advance(.95f);
        assertEquals(.75f,number.item(0,true),.000001);
        assertEquals(1,number.item(0,false)); assertTrue(number.item(1,false)<1);
    }
    @Test void invalidFrameDeltaDoesNotPoisonClock() {
        var animation = new ResultsAnimation();
        animation.advance(Float.NaN); animation.advance(Float.POSITIVE_INFINITY); animation.advance(-1);
        animation.advance(.75f); assertEquals(.25f,animation.item(0,false),.000001);
    }
}
