package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectTopCoverageTest {
    @Test void leftAndRightInsetsFollowVisiblePixelsRatherThanTransparentCanvas() {
        var coverage = SongSelectTopCoverage.detect(100,200,1,(x,y) -> y < (x < 50 ? 150 : 80) ? 200 : 0);
        assertEquals(150,coverage.depth(0,40));
        assertEquals(80,coverage.depth(60,100));
        assertEquals(80,coverage.depth(110,200));
    }
    @Test void highDensityAndTransparentChromeKeepTheirSemantics() {
        var sd = SongSelectTopCoverage.detect(100,200,1,(x,y) -> y < 80 ? 255 : 0);
        var hd = SongSelectTopCoverage.detect(200,400,2,(x,y) -> y < 160 ? 255 : 0);
        assertEquals(sd.depth(0,100),hd.depth(0,100));
        assertEquals(sd.depth(110,200),hd.depth(110,200));
        assertEquals(0,SongSelectTopCoverage.detect(1,1,1,(x,y)->0).depth(0,1366));
    }
}
