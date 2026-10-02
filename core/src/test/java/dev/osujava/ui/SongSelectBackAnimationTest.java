package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectBackAnimationTest {
    @Test void delayedFirstUpdateStartsAtZeroAndExistingSpriteKeepsItsEpoch() {
        var sprite = new SongSelectBackAnimation();
        sprite.update(5321,3,2); assertEquals(0,sprite.frame());
        sprite.update(5820,3,2); assertEquals(0,sprite.frame());
        sprite.update(5821,3,2); assertEquals(1,sprite.frame()); assertEquals(0,sprite.geometryFrame());
        sprite.update(5821,3,2); assertEquals(1,sprite.geometryFrame());
        sprite.update(6321,3,2); assertEquals(2,sprite.frame());
        sprite.update(6821,3,2); assertEquals(0,sprite.frame());
        sprite.update(8321,3,2); assertEquals(0,sprite.frame());
        var regenerated = new SongSelectBackAnimation();
        regenerated.update(8321,3,2); assertEquals(0,regenerated.frame());
        regenerated.update(8821,3,2); assertEquals(1,regenerated.frame()); assertEquals(0,regenerated.geometryFrame());
        sprite.update(5821,3,2); assertEquals(1,sprite.frame(),"A rewind after the epoch samples the earlier frame");
        sprite.update(5000,3,2); assertEquals(0,sprite.frame(),"A rewind before the epoch restarts the sprite");
        sprite.update(5500,3,2); assertEquals(1,sprite.frame());
    }
    @Test void omittedFramerateUsesSinglePrecisionOneSecondLoopInterval() {
        var sprite = new SongSelectBackAnimation();
        sprite.update(10000,3,0);
        sprite.update(10333,3,0); assertEquals(0,sprite.frame());
        sprite.update(10334,3,0); assertEquals(1,sprite.frame());
        sprite.update(10667,3,0); assertEquals(2,sprite.frame());
        sprite.update(11000,3,0); assertEquals(2,sprite.frame(),"Single division makes the third interval slightly longer than 1000ms");
        sprite.update(11001,3,0); assertEquals(0,sprite.frame());
        sprite.update(1,1,0); assertEquals(0,sprite.frame());
    }
}
