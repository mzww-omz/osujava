package dev.osujava.ui;

import com.badlogic.gdx.Input.Keys;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectKeyRepeatTest {
    private final SongSelectKeyRepeat repeat = new SongSelectKeyRepeat();
    private final List<Integer> events = new ArrayList<>();
    private void frame(double ms) { repeat.advance(ms, events::add); }

    @Test void firstFrameDoesNotAccumulateAndTheThresholdIsCheckedBeforeAddingDelta() {
        assertTrue(repeat.press(Keys.DOWN)); frame(500);
        frame(219); frame(1);
        assertTrue(events.isEmpty());
        frame(0); assertEquals(List.of(Keys.DOWN), events);
        frame(99); frame(1); assertEquals(1, events.size());
        frame(0); assertEquals(List.of(Keys.DOWN, Keys.DOWN), events);
    }
    @Test void longFrameProducesOnlyOnePulsePerFollowingUpdate() {
        repeat.press(Keys.DOWN); frame(0); frame(1000);
        assertTrue(events.isEmpty());
        frame(0); assertEquals(1, events.size());
        frame(0); assertEquals(2, events.size());
        repeat.release(Keys.DOWN); frame(1000);
        assertEquals(2, events.size());
        repeat.press(Keys.DOWN); frame(1000); frame(219); frame(0);
        assertEquals(2, events.size());
    }
    @Test void duplicateDownDoesNotRestartTheCounterButANewModifierDoes() {
        repeat.press(Keys.DOWN); frame(0); frame(220);
        assertFalse(repeat.press(Keys.DOWN)); frame(0);
        assertEquals(1, events.size());
        frame(100); repeat.press(Keys.SHIFT_LEFT); frame(219); frame(1);
        assertEquals(1, events.size());
        frame(0); assertEquals(2, events.size());
    }
    @Test void modifierOnlyFramesDoNotPrimeTheFirstNavigationPress() {
        for (int key : new int[]{Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT, Keys.CONTROL_LEFT,
                Keys.CONTROL_RIGHT, Keys.ALT_LEFT, Keys.ALT_RIGHT}) repeat.press(key);
        frame(1000); frame(1000);
        repeat.press(Keys.UP); frame(1000); frame(219); frame(1);
        assertTrue(events.isEmpty());
        frame(0); assertEquals(List.of(Keys.UP), events);
    }
    @Test void aNewKeyResetsTheSharedCounterAndAllHeldNavigationKeysRepeatInNativeOrder() {
        repeat.press(Keys.DOWN); frame(0); frame(220);
        repeat.press(Keys.PAGE_DOWN); repeat.press(Keys.UP); repeat.press(Keys.PAGE_UP);
        frame(219); frame(1); assertTrue(events.isEmpty());
        frame(0);
        assertEquals(List.of(Keys.PAGE_UP, Keys.PAGE_DOWN, Keys.UP, Keys.DOWN), events);
    }
    @Test void releasingOneKeyKeepsTheSharedCadenceForTheOthers() {
        repeat.press(Keys.UP); repeat.press(Keys.DOWN); frame(0); frame(220);
        repeat.release(Keys.UP); frame(0);
        assertEquals(List.of(Keys.DOWN), events);
        frame(100); frame(0); assertEquals(List.of(Keys.DOWN, Keys.DOWN), events);
    }
    @Test void singleShotAndTextKeysNeverReceiveSyntheticRepeats() {
        for (int key : new int[]{Keys.LEFT, Keys.RIGHT, Keys.ENTER, Keys.SPACE, Keys.ESCAPE,
                Keys.F1, Keys.F2, Keys.F3, Keys.F6, Keys.I, Keys.A, Keys.BACKSPACE}) repeat.press(key);
        frame(0); frame(1000); frame(0);
        assertTrue(events.isEmpty());
        // Those keys still participate in the shared native counter.
        repeat.press(Keys.DOWN); frame(220); frame(0);
        assertEquals(List.of(Keys.DOWN), events);
    }
    @Test void lostReleaseAndScreenCancellationCannotLeaveHeldCommandsBehind() {
        repeat.press(Keys.DOWN); frame(0); frame(220);
        repeat.reconcile(key -> false); frame(0); assertTrue(events.isEmpty());
        repeat.press(Keys.DOWN); frame(0); frame(220); repeat.clear(); frame(0);
        assertTrue(events.isEmpty()); assertTrue(repeat.press(Keys.DOWN));
    }
    @Test void invalidDeltasDoNotPoisonTheCounter() {
        repeat.press(Keys.DOWN); frame(0);
        frame(Double.NaN); frame(Double.POSITIVE_INFINITY); frame(-100);
        frame(220); frame(0); assertEquals(List.of(Keys.DOWN), events);
    }
    @ParameterizedTest @CsvSource({"30,7 10 13 16 19 22 25 28", "60,14 20 26 32 38 44 50 56",
            "144,32 47 61 75 90 104 119 133"})
    void frameQuantizationMatchesTheNativeCounterWithoutCatchUpLoops(int fps, String expectedFrames) {
        repeat.press(Keys.DOWN); frame(0);
        List<Integer> frames = new ArrayList<>();
        double delta = 1000.0 / fps;
        for (int i = 0; i < fps; i++) {
            int before = events.size();
            frame(delta);
            if (events.size() > before) frames.add(i);
        }
        assertEquals(java.util.Arrays.stream(expectedFrames.split(" ")).map(Integer::valueOf).toList(), frames);
    }
}
