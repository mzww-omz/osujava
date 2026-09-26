package dev.osujava.gameplay;

import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.osujava.gameplay.FollowCircleAnimation.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class FollowCircleAnimationTest {
    private FollowCircleAnimation.Event event(FollowCircleAnimation.Kind kind, double time) {
        return new FollowCircleAnimation.Event(kind, time);
    }
    @Test void pressUsesIndependentDurationsAndOutQuadraticScale() {
        var events = List.of(event(PRESS, 1000));
        assertEquals(new FollowCircleAnimation.Frame(1, 0), FollowCircleAnimation.at(events, 1000, 2000));
        assertEquals(0.5, FollowCircleAnimation.at(events, 1030, 2000).alpha());
        assertEquals(1.75, FollowCircleAnimation.at(events, 1090, 2000).scale());
        assertEquals(new FollowCircleAnimation.Frame(2, 1), FollowCircleAnimation.at(events, 1180, 2000));
        assertEquals(new FollowCircleAnimation.Frame(2, 1), FollowCircleAnimation.at(events, 1040, 1040));
    }
    @Test void releaseDoesNothingAndRepressResets() {
        var events = List.of(event(PRESS, 1000), event(RELEASE, 1200), event(PRESS, 1500));
        assertEquals(new FollowCircleAnimation.Frame(2, 1), FollowCircleAnimation.at(events, 1400, 2000));
        assertEquals(new FollowCircleAnimation.Frame(1, 0), FollowCircleAnimation.at(events, 1500, 2000));
    }
    @Test void tickAndRepeatPulseImmediatelyThenReturnLinearly() {
        var events = List.of(event(PRESS, 1000), event(TICK, 1200));
        assertEquals(2.2, FollowCircleAnimation.at(events, 1200, 2000).scale());
        assertEquals(2.1, FollowCircleAnimation.at(events, 1300, 2000).scale());
        assertEquals(2, FollowCircleAnimation.at(events, 1400, 2000).scale());
        var earlyTick = List.of(event(PRESS, 1000), event(TICK, 1030));
        assertTrue(FollowCircleAnimation.at(earlyTick, 1030, 2000).scale() < 2);
    }
    @Test void endHasOutScaleAndInFadeAndCanBeScheduledAhead() {
        var events = List.of(event(PRESS, 1000), event(END, 2000));
        assertEquals(new FollowCircleAnimation.Frame(2, 1), FollowCircleAnimation.at(events, 1999, 2000));
        assertEquals(1.7, FollowCircleAnimation.at(events, 2100, 2000).scale(), 1e-9);
        assertEquals(0.75, FollowCircleAnimation.at(events, 2100, 2000).alpha());
        assertEquals(new FollowCircleAnimation.Frame(1.6, 0), FollowCircleAnimation.at(events, 2200, 2000));
    }
    @Test void breakGrowsToFourAndFadesLinearlyIn100ms() {
        var events = List.of(event(PRESS, 1000), event(RELEASE, 1200), event(BREAK, 1500));
        assertEquals(new FollowCircleAnimation.Frame(3, 0.5), FollowCircleAnimation.at(events, 1550, 2000));
        assertEquals(new FollowCircleAnimation.Frame(4, 0), FollowCircleAnimation.at(events, 1600, 2000));
        // Evaluation order / render frequency cannot change history.
        assertEquals(new FollowCircleAnimation.Frame(2, 1), FollowCircleAnimation.at(events, 1400, 2000));
    }
}
