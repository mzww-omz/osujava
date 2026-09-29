package dev.osujava.ui;

import com.badlogic.gdx.InputAdapter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectWheelInputTest {
    private final List<Float> events = new ArrayList<>();
    private final SongSelectWheelInput input = new SongSelectWheelInput(new InputAdapter() {
        @Override public boolean scrolled(float x, float y) { events.add(y); return true; }
    });

    @ParameterizedTest @ValueSource(floats = {.0001f, .25f, 1, 20, Float.MAX_VALUE})
    void magnitudeOnlyChoosesDirectionAtTheNextUpdate(float amount) {
        assertTrue(input.scrolled(0, amount)); assertTrue(events.isEmpty());
        input.dispatch(); assertEquals(List.of(1f), events);
        input.dispatch(); assertEquals(1, events.size());
        input.scrolled(0, -amount); input.dispatch(); assertEquals(List.of(1f, -1f), events);
    }
    @Test void manyCallbacksInOneUpdateProduceOnlyOneImpulse() {
        for (int i = 0; i < 100; i++) input.scrolled(0, 1);
        input.dispatch(); assertEquals(List.of(1f), events);
        for (int i = 0; i < 3; i++) { input.scrolled(0, 1); input.dispatch(); }
        assertEquals(List.of(1f, 1f, 1f, 1f), events);
    }
    @Test void oppositeDeltasCancelBeforeRoutingRatherThanAcceleratingThenBraking() {
        input.scrolled(0, 4); input.scrolled(0, -4); input.dispatch();
        assertTrue(events.isEmpty());
        input.scrolled(0, 3); input.scrolled(0, -2); input.dispatch();
        input.scrolled(0, -3); input.scrolled(0, 2); input.dispatch();
        assertEquals(List.of(1f, -1f), events);
    }
    @Test void finiteFloatExtremesCanAccumulateAndCancelWithoutOverflow() {
        input.scrolled(0, Float.MAX_VALUE); input.scrolled(0, Float.MAX_VALUE);
        input.scrolled(0, -Float.MAX_VALUE); input.scrolled(0, -Float.MAX_VALUE);
        input.dispatch(); assertTrue(events.isEmpty());
        input.scrolled(0, Float.MAX_VALUE); input.scrolled(0, Float.MAX_VALUE);
        input.dispatch(); assertEquals(List.of(1f), events);
    }
    @Test void invalidAndHorizontalOnlyEventsCannotPoisonAnAcceptedDelta() {
        input.scrolled(0, -1);
        for (float bad : new float[]{0, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertFalse(input.scrolled(1, bad));
        input.dispatch(); assertEquals(List.of(-1f), events);
    }
    @Test void cancellationDiscardsThePendingEventAndNewInputStillWorks() {
        input.scrolled(0, 1); input.cancel(); input.dispatch(); assertTrue(events.isEmpty());
        input.scrolled(0, -1); input.dispatch(); assertEquals(List.of(-1f), events);
    }
}
