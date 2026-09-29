package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets.Selection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectHoverTest {
    @Test void selectionFadeReversesFromCurrentOpacityOverFullDuration() {
        var hover = new SongSelectHover();
        hover.advance(.05,SongSelectAction.MODS);
        assertEquals(.505,hover.appearance().alpha(Selection.MODS),1e-6);
        hover.advance(.05,null);
        assertEquals(.2575,hover.appearance().mods(),1e-6);
        hover.advance(.05,null);
        assertEquals(.01,hover.appearance().mods(),1e-6);
        hover.advance(.1,SongSelectAction.MODS);
        assertEquals(1,hover.appearance().mods());
    }
    @Test void backHasSeparateAdditiveOpacityAndQuarterSecondFade() {
        var hover = new SongSelectHover();
        hover.advance(.25,null);
        assertEquals(1f/255,hover.appearance().back(),1e-6);
        hover.advance(.125,SongSelectAction.BACK);
        assertEquals((1f/255+.4)/2,hover.appearance().back(),1e-6);
        hover.advance(.125,SongSelectAction.BACK);
        assertEquals(.4,hover.appearance().back(),1e-6);
    }
    @Test void fadesAreFrameRateIndependentAndDrawingSnapshotsCannotAdvanceThem() {
        for (int frames : new int[]{3,6,14,30}) {
            var hover = new SongSelectHover();
            for (int i=0;i<frames;i++) hover.advance(.1/frames,SongSelectAction.RANDOM);
            assertEquals(1,hover.appearance().random(),1e-6);
            var snapshot = hover.appearance();
            hover.advance(Double.NaN,SongSelectAction.RANDOM);
            assertEquals(snapshot,hover.appearance());
        }
    }
}
