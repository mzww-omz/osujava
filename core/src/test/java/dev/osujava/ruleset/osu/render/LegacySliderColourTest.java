package dev.osujava.ruleset.osu.render;

import com.badlogic.gdx.graphics.Color;
import dev.osujava.skin.SkinConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacySliderColourTest {
    private final Color track = new Color(.2f,.4f,.8f,.1f);
    @Test void shadowAndSmoothPathEdgeAntialiasing() {
        assertEquals(0, LegacySliderColour.at(0,Color.WHITE,track).a);
        assertEquals(.125f,LegacySliderColour.at(5f/128,Color.WHITE,track).a,1e-6);
        assertEquals(.25f,LegacySliderColour.at(5f/64,Color.WHITE,track).a,1e-6);
        assertEquals(.25f*.01f/(5f/64)*.5f,LegacySliderColour.at(.01f,Color.WHITE,track).a,1e-6);
    }
    @Test void borderOuterAndInnerMatchLegacyProfile() {
        var border = new Color(.3f,.5f,.9f,1);
        assertEquals(border,LegacySliderColour.at(.1f,border,track));
        assertEquals(border,LegacySliderColour.at(.1875f,border,track));
        var outer = LegacySliderColour.at(.187501f,border,track);
        assertEquals(.2f/1.1f,outer.r,1e-5);
        assertEquals(.7f,outer.a);
        var inner = LegacySliderColour.at(1,border,track);
        assertEquals(.475f,inner.r,1e-6);
        assertEquals(.7f,inner.g,1e-6);
        assertEquals(1,inner.b,1e-6);
    }
    @Test void nonlinearMeansInterpolationOfEncodedSrgbValues() {
        var colour = LegacySliderColour.at((1+.1875f)/2,Color.WHITE,track);
        assertEquals((.2f/1.1f+.475f)/2,colour.r,1e-6);
        assertEquals(.7f,colour.a);
    }
    @Test void defaultsAndOverridesAlwaysForceTrackAlpha() {
        var defaults = SkinConfiguration.Colours.defaults();
        assertEquals(Color.WHITE,LegacySliderColour.border(defaults));
        assertEquals(new Color(.2f,.4f,.8f,.7f),LegacySliderColour.track(defaults,track));
        var colours = new SkinConfiguration.Colours(new SkinConfiguration.Rgb(.1f,.2f,.3f),
                new SkinConfiguration.Rgb(.8f,.6f,.4f));
        assertEquals(new Color(.8f,.6f,.4f,.7f),LegacySliderColour.track(colours,track));
        assertEquals(new Color(.1f,.2f,.3f,1),LegacySliderColour.border(colours));
        assertEquals(.1f,track.a,"Source colours must not be mutated");
    }
}
