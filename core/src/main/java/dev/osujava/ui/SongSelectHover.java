package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets.Selection;

/** Update-owned fades. Stable b20230727.9: 136e, 3a15, 40b9/40ba, 42ec, 2b54. */
final class SongSelectHover {
    private final Fade[] selections = {new Fade(.01f),new Fade(.01f),new Fade(.01f),new Fade(.01f)};
    private final Fade back = new Fade(.01f);
    record Appearance(float mode, float mods, float random, float options, float back) {
        float alpha(Selection selection) {
            return switch (selection) { case MODE -> mode; case MODS -> mods; case RANDOM -> random; case OPTIONS -> options; };
        }
    }
    void advance(double seconds, SongSelectAction hovered) {
        for (var selection : Selection.values()) selections[selection.ordinal()].advance(
                seconds, hovered != null && hovered.name().equals(selection.name()) ? 1 : .01f, .1);
        back.advance(seconds, hovered == SongSelectAction.BACK ? .4f : 1f/255, .25);
    }
    Appearance appearance() {
        return new Appearance(selections[0].value,selections[1].value,selections[2].value,selections[3].value,back.value);
    }
    private static final class Fade {
        private float start, target, value;
        private double elapsed;
        Fade(float initial) { start = target = value = initial; }
        void advance(double seconds, float next, double duration) {
            if (next != target) { start = value; target = next; elapsed = 0; }
            if (Double.isFinite(seconds)) elapsed = Math.min(duration, elapsed + Math.max(0, seconds));
            value = start + (target - start) * (float)(elapsed/duration);
        }
    }
}
