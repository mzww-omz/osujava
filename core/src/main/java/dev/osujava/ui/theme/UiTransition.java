package dev.osujava.ui.theme;

public final class UiTransition {
    private float elapsed;
    public void advance(float delta) { elapsed = Math.min(UiTheme.ENTER_SECONDS, elapsed + Math.max(0, delta)); }
    public float opacity() { return 1f - elapsed / UiTheme.ENTER_SECONDS; }
    public float progress() { return 1f - opacity(); }
}
