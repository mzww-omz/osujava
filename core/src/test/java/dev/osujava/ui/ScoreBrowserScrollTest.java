package dev.osujava.ui;

import dev.osujava.score.*;
import dev.osujava.gameplay.*;
import dev.osujava.ui.theme.UiLayout;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ScoreBrowserScrollTest {
    private final LocalScoreStore store = new LocalScoreStore();
    private final DifficultyIdentity target = new DifficultyIdentity("set", "map.osu");
    private final ScoreBrowserModel scores = new ScoreBrowserModel(store);
    private final ScoreBrowserScroll drag = new ScoreBrowserScroll();
    private void save() {
        store.save(new LocalScore(UUID.randomUUID(),target,0,new ScoreState(100,0,10,10,0,0,0,1)),GameplayRunMode.MANUAL);
    }
    private ScoreBrowserBounds setup(int width, int height, int count) {
        for (int i = 0; i < count; i++) save();
        var bounds = ScoreBrowserBounds.of(UiLayout.fromPixels(width,height));
        scores.target(target); scores.capacity(bounds.capacity());
        return bounds;
    }
    @ParameterizedTest @CsvSource({"1280,720", "1280,800", "1024,768", "1920,1080"})
    void grabOffsetEndpointsOutsideColumnAndRelease(int width, int height) {
        var bounds = setup(width,height,100);
        scores.select(0); var selected = scores.selected();
        scores.scroll(.75f);
        var thumb = bounds.thumb(0,100);
        float x = thumb.x()+thumb.width()/2, y = thumb.y()+thumb.height()*.25f;
        assertFalse(drag.press(bounds,scores,thumb.x()-.01f,y));
        assertTrue(drag.press(bounds,scores,x,y));
        assertTrue(drag.update(bounds,scores,y,true,true)); assertEquals(0,scores.first());
        float travel = bounds.top()-bounds.bottom()-thumb.height();
        drag.update(bounds,scores,y-travel/2,true,true);
        assertEquals(Math.round((100-scores.capacity())/2f),scores.first());
        drag.update(bounds,scores,bounds.bottom()-1000,true,true);
        assertEquals(100-scores.capacity(),scores.first());
        assertTrue(drag.update(bounds,scores,bounds.top()+1000,false,true));
        assertFalse(drag.captured()); assertEquals(0,scores.first());
        assertEquals(selected,scores.selected());
        scores.scroll(.25f); assertEquals(0,scores.first(),"Absolute drag clears old fractional wheel movement");
        assertFalse(drag.update(bounds,scores,y,false,true));
    }
    @ParameterizedTest @CsvSource({"0", "1", "3"})
    void noThumbWithoutOverflow(int count) {
        var bounds = setup(1280,720,count);
        assertFalse(drag.press(bounds,scores,bounds.x()+bounds.width()-1,bounds.top()-1));
        assertFalse(drag.captured());
    }
    @ParameterizedTest @CsvSource({"resize", "target", "revision", "overlay", "pause"})
    void contextChangesCancelButConsumeUntilRelease(String change) {
        var bounds = setup(1280,720,100); var thumb = bounds.thumb(0,100);
        assertTrue(drag.press(bounds,scores,thumb.x()+1,thumb.y()+1));
        var next = bounds;
        switch (change) {
            case "resize" -> next = ScoreBrowserBounds.of(UiLayout.fromPixels(1024,768));
            case "target" -> scores.target(new DifficultyIdentity("other","map.osu"));
            case "revision" -> { save(); scores.target(target); }
            case "pause" -> drag.cancel();
        }
        assertTrue(drag.update(next,scores,bounds.bottom()-1000,true,!change.equals("overlay")));
        assertEquals(0,scores.first());
        assertTrue(drag.captured());
        assertTrue(drag.update(next,scores,bounds.bottom()-1000,false,true));
        assertFalse(drag.captured()); assertEquals(0,scores.first());
    }
}
