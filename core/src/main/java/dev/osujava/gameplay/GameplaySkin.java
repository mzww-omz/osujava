package dev.osujava.gameplay;

import com.badlogic.gdx.graphics.Color;

/** Visual component lookup used by renderers; gameplay state does not depend on a skin. */
public interface GameplaySkin {
    Color component(GameplaySkinComponent component);

    Color comboColor(int comboColorIndex);

    Color judgementColor(Judgement judgement);
}
