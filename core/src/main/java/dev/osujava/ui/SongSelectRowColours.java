package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;

/** Sprite colour targets from stable 06000fb1/fda/fe2 and 060025e6; independent of Skin lookup. */
final class SongSelectRowColours {
    private SongSelectRowColours() { }

    static Color background(Color out, SongSelectRow row, boolean played, boolean groupContainsSelection) {
        base(out, row.group(), row.groupExpanded(), row.selected(), row.sibling(), played, groupContainsSelection);
        // Fixed target preview for diagnostics. Live drawing receives the animated RGBA from Screen.
        focusTint(out, row.focusAmount());
        hoverTint(out, row.hoverAmount());
        out.a *= row.revealAmount();
        return out;
    }

    static Color base(Color out, boolean group, boolean expanded, boolean selected, boolean sibling,
                      boolean played, boolean groupContainsSelection) {
        if (group) {
            if (expanded) rgba(out, 163, 240, 44, 255);
            else if (groupContainsSelection) rgba(out, 35, 90, 193, 255);
            else rgba(out, 35, 50, 143, 255);
        } else switch (SongSelectRowPresentation.tone(selected, sibling, played)) {
            case SELECTED -> rgba(out, 255, 255, 255, 220);
            case SIBLING -> rgba(out, 0, 150, 236, 240);
            case PLAYED -> rgba(out, 233, 104, 0, 240);
            case UNPLAYED -> rgba(out, 235, 73, 153, 240);
        }
        return out;
    }

    /** 06001965: brighten RGB by 40%, saturate each byte, and leave alpha unchanged. */
    static void focusTint(Color color, float amount) {
        color.r += (Math.min(255, (int) (color.r * 255 * 1.4f)) / 255f - color.r) * amount;
        color.g += (Math.min(255, (int) (color.g * 255 * 1.4f)) / 255f - color.g) * amount;
        color.b += (Math.min(255, (int) (color.b * 255 * 1.4f)) / 255f - color.b) * amount;
    }

    /** 06001966 with amount .3: byte RGB * 1.075 + 38.25, saturated; alpha is preserved. */
    static void hoverTint(Color color, float amount) {
        color.r += (Math.min(255, (int) (color.r * 255 * 1.075f + 38.25f)) / 255f - color.r) * amount;
        color.g += (Math.min(255, (int) (color.g * 255 * 1.075f + 38.25f)) / 255f - color.g) * amount;
        color.b += (Math.min(255, (int) (color.b * 255 * 1.075f + 38.25f)) / 255f - color.b) * amount;
    }

    /** 06000fc7/196c/fe2: both title and byline use alpha 50 in state 3. */
    static Color label(Color out, Color base, SongSelectRow row, boolean titleOrByline) {
        out.set(base);
        if (titleOrByline && row.sibling() && !row.selected()) out.a = 50 / 255f;
        out.a *= row.revealAmount();
        return out;
    }

    private static void rgba(Color out, int red, int green, int blue, int alpha) {
        out.set(red / 255f, green / 255f, blue / 255f, alpha / 255f);
    }
}
