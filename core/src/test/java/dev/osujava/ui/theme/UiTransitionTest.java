package dev.osujava.ui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiTransitionTest {
    @Test void enterFadeIsBounded() {
        UiTransition transition = new UiTransition();
        assertEquals(1, transition.opacity(), 0.001);
        transition.advance(UiTheme.ENTER_SECONDS / 2);
        assertEquals(0.5, transition.opacity(), 0.001);
        transition.advance(10);
        assertEquals(0, transition.opacity(), 0.001);
        transition.advance(-2);
        assertEquals(0, transition.opacity(), 0.001);
    }
}
