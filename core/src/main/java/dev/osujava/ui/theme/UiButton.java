package dev.osujava.ui.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

/** Screen-owned button rectangle with shared visual states. */
public final class UiButton {
    private float x, y, width, height;
    private String label;
    private boolean enabled = true;
    private boolean primary;
    private float hover;

    public UiButton(String label, boolean primary) { this.label = label; this.primary = primary; }
    public void bounds(float x, float y, float width, float height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }
    public void enabled(boolean enabled) { this.enabled = enabled; }
    public boolean hit(float px, float py) {
        return enabled && px >= x && px <= x + width && py >= y && py <= y + height;
    }
    public void drawShape(UiView view, UiLayout layout, float delta) {
        boolean hovered = hit(layout.pointerX(Gdx.input.getX()), layout.pointerY(Gdx.input.getY()));
        float target = hovered ? 1 : 0;
        hover += (target - hover) * Math.min(1, Math.max(0, delta) / UiTheme.HOVER_SECONDS);
        Color color = !enabled ? UiTheme.DISABLED : primary ? UiTheme.ACCENT : UiTheme.SURFACE_RAISED;
        if (hovered && enabled) color = primary ? UiTheme.ACCENT_HOVER : UiTheme.ACCENT;
        if (hovered && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) color = UiTheme.ACCENT_PRESSED;
        view.box(x - hover * 3, y - hover * 2, width + hover * 6, height + hover * 4,
                UiTheme.RADIUS, color);
    }
    public void drawText(UiView view) {
        view.text(label, x + 8, y + height / 2 + 6, width - 16, UiTheme.BODY,
                enabled ? UiTheme.TEXT : UiTheme.MUTED, Align.center);
    }
}
