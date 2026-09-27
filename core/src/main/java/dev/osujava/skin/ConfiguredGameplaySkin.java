package dev.osujava.skin;

import com.badlogic.gdx.graphics.Color;
import dev.osujava.gameplay.GameplaySkin;
import dev.osujava.gameplay.GameplaySkinComponent;
import dev.osujava.gameplay.Judgement;

/** Applies optional skin.ini combo colours to rendering alone. */
public final class ConfiguredGameplaySkin implements GameplaySkin {
    private final GameplaySkin fallback;
    private final Color[] comboColours;

    public ConfiguredGameplaySkin(GameplaySkin fallback, SkinConfiguration.Colours colours) {
        this.fallback = fallback;
        comboColours = colours.comboColours().stream().map(c -> new Color(c.r(), c.g(), c.b(), 1)).toArray(Color[]::new);
    }

    @Override public Color comboColor(int index) {
        return comboColours.length == 0 ? fallback.comboColor(index) : comboColours[Math.floorMod(index, comboColours.length)];
    }
    @Override public Color component(GameplaySkinComponent component) { return fallback.component(component); }
    @Override public Color judgementColor(Judgement judgement) { return fallback.judgementColor(judgement); }
}
