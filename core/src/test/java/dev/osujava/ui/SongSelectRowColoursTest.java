package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectRowColoursTest {
    private SongSelectRow row(boolean selected, boolean sibling, float hover, float focus, float reveal) {
        return new SongSelectRow(0, 0, null, selected, sibling, 100, 100, 300, 72,
                hover, reveal, 0, 100, 100, "row", false, focus);
    }
    private void rgba(Color color, int r, int g, int b, int a) {
        assertEquals(r / 255f, color.r, .00001);
        assertEquals(g / 255f, color.g, .00001);
        assertEquals(b / 255f, color.b, .00001);
        assertEquals(a / 255f, color.a, .00001);
    }
    @ParameterizedTest @CsvSource({
            "true,false,false,255,255,255,220", "true,true,true,255,255,255,220",
            "false,true,false,0,150,236,240", "false,true,true,0,150,236,240",
            "false,false,false,235,73,153,240", "false,false,true,233,104,0,240"})
    void backgroundTargetsMatchTheNativeStatePalette(boolean selected, boolean sibling, boolean played,
            int r, int g, int b, int a) {
        rgba(SongSelectRowColours.background(new Color(), row(selected,sibling,0,0,1), played, false),r,g,b,a);
    }
    @ParameterizedTest @CsvSource({"false,false,35,50,143", "false,true,35,90,193", "true,false,163,240,44", "true,true,163,240,44"})
    void groupPaletteDistinguishesTheClosedGroupContainingSelection(boolean open, boolean contains, int r, int g, int b) {
        var row = new SongSelectRow(-1,-2,"Group",false,false,100,100,300,72,0,1,0,100,100,"group",open,0);
        rgba(SongSelectRowColours.background(new Color(), row, true, contains),r,g,b,255);
    }
    @ParameterizedTest @CsvSource({"235,73,153,240,255,116,202", "233,104,0,240,255,150,38",
            "0,150,236,240,38,199,255", "35,50,143,255,75,92,191", "255,255,255,220,255,255,255"})
    void hoverUsesTheNativeByteTransformAndNeverRaisesAlpha(int r, int g, int b, int a, int hr, int hg, int hb) {
        var color = new Color(r/255f,g/255f,b/255f,a/255f);
        SongSelectRowColours.hoverTint(color,1); rgba(color,hr,hg,hb,a);
    }
    @Test void hoverDerivesFromTheFocusColourBeforeApplyingRevealOpacity() {
        var color = SongSelectRowColours.background(new Color(),row(false,true,1,1,.5f),false,false);
        assertEquals(38/255f,color.r,.00001); assertEquals(1,color.g); assertEquals(1,color.b);
        assertEquals(120/255f,color.a,.00001);
    }
    @Test void siblingTitleAndBylineUseAlphaFiftyWhileOtherForegroundKeepsTheSkinColour() {
        var base = new Color(.2f,.4f,.8f,.9f);
        var sibling = row(false,true,0,0,1);
        var label = SongSelectRowColours.label(new Color(),base,sibling,true);
        assertEquals(50/255f,label.a); assertEquals(base.r,label.r); assertEquals(base.g,label.g); assertEquals(base.b,label.b);
        assertEquals(base,SongSelectRowColours.label(new Color(),base,sibling,false));
        assertEquals(.9f,base.a,"Skin colours must not be mutated");
    }
    @Test void selectedAndCollapsedLabelsHaveNoAdditionalJavaOpacityReduction() {
        var base = new Color(.3f,.5f,.7f,1);
        for (var row : new SongSelectRow[]{row(true,true,0,0,1), row(false,false,0,0,1)})
            for (boolean title : new boolean[]{true,false})
                assertEquals(base,SongSelectRowColours.label(new Color(),base,row,title));
        assertEquals(25/255f,SongSelectRowColours.label(new Color(),base,row(false,true,0,0,.5f),true).a);
    }
}
