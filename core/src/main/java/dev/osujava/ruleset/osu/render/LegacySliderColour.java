package dev.osujava.ruleset.osu.render;

import com.badlogic.gdx.graphics.Color;
import dev.osujava.skin.SkinConfiguration;

/** LegacySliderBody.ColourAt: position is zero at the outside and one at the centre. */
public final class LegacySliderColour {
    public static final float SHADOW_PORTION = 5f / 64;
    public static final float BORDER_PORTION = .1875f;
    public static final float TRACK_ALPHA = .7f;
    private LegacySliderColour() { }

    public static Color border(SkinConfiguration.Colours colours) {
        var rgb = colours.sliderBorder();
        return new Color(rgb.r(), rgb.g(), rgb.b(), 1);
    }

    public static Color track(SkinConfiguration.Colours colours, Color accent) {
        var rgb = colours.sliderTrackOverride();
        return rgb == null ? new Color(accent.r, accent.g, accent.b, TRACK_ALPHA)
                : new Color(rgb.r(), rgb.g(), rgb.b(), TRACK_ALPHA);
    }

    public static Color outer(Color track) {
        // osu.Framework Color4Extensions.Darken(0.1) divides RGB by 1.1, retaining alpha.
        return new Color(track.r / 1.1f, track.g / 1.1f, track.b / 1.1f, TRACK_ALPHA);
    }

    public static Color inner(Color track) {
        return new Color(Math.min(1, track.r * 1.125f + .25f),
                Math.min(1, track.g * 1.125f + .25f), Math.min(1, track.b * 1.125f + .25f), TRACK_ALPHA);
    }

    public static Color at(float position, Color border, Color track) {
        float p = Math.max(0, Math.min(1, position));
        Color result;
        if (p <= SHADOW_PORTION) result = new Color(0, 0, 0, .25f * p / SHADOW_PORTION);
        else if (p <= BORDER_PORTION) result = new Color(border);
        else {
            // LegacyUtils.InterpolateNonLinear interpolates the encoded sRGB components directly.
            result = outer(track).lerp(inner(track), (p - BORDER_PORTION) / (1 - BORDER_PORTION));
        }
        // SmoothPath's outer-edge anti-aliasing multiplier.
        result.a *= Math.min(p / .02f, 1);
        return result;
    }
}
