package dev.osujava.ui;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectCursorInputTest {
    record Sample(double time, int x, boolean held, boolean press) { }
    @Test void preservesShortClicksAndIndependentButtonPressesInEventOrder() {
        var input = new SongSelectCursorInput(); var samples = new ArrayList<Sample>();
        input.event(1_000_000, 1, 2, Input.Buttons.LEFT, true);
        input.event(2_000_000, 2, 2, Input.Buttons.RIGHT, true);
        input.event(3_000_000, 3, 2, Input.Buttons.LEFT, false);
        input.event(4_000_000, 4, 2, Input.Buttons.RIGHT, false);
        input.advance(10, 10_000_000, 5, 2, false, false,
                (time,x,y,held,press) -> samples.add(new Sample(time,x,held,press)));
        assertEquals(new Sample(1,1,true,true), samples.get(0));
        assertEquals(new Sample(2,2,true,true), samples.get(1));
        assertEquals(new Sample(3,3,true,false), samples.get(2));
        assertEquals(new Sample(4,4,false,false), samples.get(3));
        assertEquals(new Sample(10,5,false,false), samples.get(4));
    }
    @Test void reconcilesMissingEventsAndClampsOutOfOrderTimestamps() {
        var input = new SongSelectCursorInput(); var samples = new ArrayList<Sample>();
        SongSelectCursorInput.Sink sink = (time,x,y,held,press) -> samples.add(new Sample(time,x,held,press));
        input.advance(10, 10_000_000, 0, 0, true, false, sink);
        input.event(1_000_000, 1, 0, -1, false);
        input.event(50_000_000, 2, 0, -1, false);
        input.advance(20, 20_000_000, 2, 0, false, false, sink);
        assertTrue(samples.getFirst().press());
        assertEquals(10, samples.get(1).time()); assertEquals(20, samples.get(2).time());
        assertFalse(samples.getLast().held());
    }
}
