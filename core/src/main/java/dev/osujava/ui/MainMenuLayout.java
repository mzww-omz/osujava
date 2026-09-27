package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;

/** Centre-rooted mirrored wedges. Draw and hitbox share the exact animated outer edge. */
record MainMenuLayout(float width, float height, float cx, float cy, float radius, float targetWidth, float buttonHeight) {
    static final float WEDGE = 20;
    static MainMenuLayout from(UiLayout ui) {
        float r = Math.min(ui.height() * .235f, ui.width() * .18f);
        return new MainMenuLayout(ui.width(), ui.height(), ui.width() / 2, ui.height() / 2, r,
                (ui.width() / 2 - 32) / 1.22f, Math.min(92, ui.height() * .125f));
    }
    int direction(int button) { return button == 0 ? 1 : -1; }
    float extent(float reveal, float hover, float explosion) { return targetWidth * reveal * (1 + .15f * hover + .25f * explosion); }
    float outer(int button, float y, float reveal, float hover, float explosion) {
        float vertical = MainMenuMotion.clamp((y - cy + buttonHeight / 2) / buttonHeight);
        return cx + direction(button) * Math.max(0, extent(reveal, hover, explosion) - WEDGE * reveal * (1 - vertical));
    }
    boolean logoHit(float x, float y, float scale) { return Math.hypot(x - cx, y - cy) <= radius * scale; }
    int buttonAt(float x, float y, MainMenuModel model) {
        if (!model.buttonsEnabled() || model.reveal() < .8f || logoHit(x, y, model.scale()) || Math.abs(y - cy) > buttonHeight / 2) return -1;
        for (int b = 0; b < 2; b++) {
            float distance = direction(b) * (x - cx);
            float edge = direction(b) * (outer(b, y, model.reveal(), model.hover(b), 0) - cx);
            if (distance > 0 && distance <= edge) return b;
        }
        return -1;
    }
}
