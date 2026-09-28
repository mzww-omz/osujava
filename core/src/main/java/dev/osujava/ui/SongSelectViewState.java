package dev.osujava.ui;

import dev.osujava.ui.theme.UiLayout;

/** Sampled display input; never persisted in Library and never sampled by the renderer. */
final class SongSelectViewState {
    final SongSelectCarousel carousel = new SongSelectCarousel();
    final dev.osujava.ui.theme.UiTransition entrance = new dev.osujava.ui.theme.UiTransition();
    float elapsed;
    float pointerX, pointerY;
    void advance(float delta) { elapsed += delta; entrance.advance(delta); }
    boolean pointerPressed;

    void sample(UiLayout layout, int windowX, int windowY, boolean pressed) {
        pointerX = layout.pointerX(windowX);
        pointerY = layout.pointerY(windowY);
        pointerPressed = pressed;
    }
}
