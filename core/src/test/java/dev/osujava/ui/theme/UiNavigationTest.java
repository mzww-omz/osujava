package dev.osujava.ui.theme;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UiNavigationTest {
    @Test void actionRunsOnceAfterShortFade() {
        UiNavigation navigation = new UiNavigation();
        AtomicInteger calls = new AtomicInteger();
        navigation.request(calls::incrementAndGet);
        navigation.request(calls::incrementAndGet);
        assertFalse(navigation.advance(0.05f));
        assertTrue(navigation.opacity() > 0 && navigation.opacity() < 1);
        assertTrue(navigation.advance(0.08f));
        assertEquals(1, calls.get());
        assertFalse(navigation.advance(1));
    }
}
