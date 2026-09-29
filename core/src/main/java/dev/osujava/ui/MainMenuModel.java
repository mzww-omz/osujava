package dev.osujava.ui;

/** Explicit menu state and input/animation controller; drawing cannot alter navigation or audio. */
final class MainMenuModel {
    static final double EXPAND_MS = 380, LOGO_MS = 200, CLOSE_MS = 300, EXIT_MS = 200;
    private MainMenuState state = MainMenuState.CLOSED;
    private double elapsed, startReveal, startScale = 1;
    private float startFrame;
    static final double FRAME_MS = 200;
    private Runnable pending;
    private double exitElapsed;
    private int exploded = -1;
    private final Spring[] hover = {new Spring(), new Spring()};
    private final MenuCookieMotion cookie = new MenuCookieMotion();
    MainMenuState state() { return state; }
    void toggle() {
        if (pending != null) return;
        startFrame = frameEmphasis();
        startReveal = reveal(); startScale = transitionScale(); elapsed = 0; cookie.click();
        state = state == MainMenuState.CLOSED || state == MainMenuState.CLOSING ? MainMenuState.OPENING : MainMenuState.CLOSING;
    }
    float reveal() {
        return switch(state) {
            case CLOSED -> 0; case OPEN -> 1;
            case OPENING -> (float) startReveal + (1 - (float) startReveal) * MainMenuMotion.outExpo(elapsed / EXPAND_MS);
            case CLOSING -> (float) startReveal * (1 - MainMenuMotion.outExpo(elapsed / CLOSE_MS));
        };
    }
    /** A quiet linear fade, independent of logo easing; reversals retain current opacity. */
    float frameEmphasis() {
        return switch (state) {
            case CLOSED -> 0;
            case OPEN -> 1;
            case OPENING -> startFrame + (1 - startFrame) * MainMenuMotion.clamp(elapsed / FRAME_MS);
            case CLOSING -> startFrame * (1 - MainMenuMotion.clamp(elapsed / FRAME_MS));
        };
    }
    float transitionScale() {
        return switch(state) {
            case CLOSED -> 1; case OPEN -> .65f;
            case OPENING -> (float) startScale + (.65f - (float) startScale) * (float) Math.pow(MainMenuMotion.clamp(elapsed / LOGO_MS), 2);
            case CLOSING -> (float) startScale + (1 - (float) startScale) * MainMenuMotion.outExpo(elapsed / CLOSE_MS);
        };
    }
    void pointer(boolean logo, int button, boolean down) {
        cookie.pointer(logo);
        hover[0].target(button == 0 ? 1 : 0); hover[1].target(button == 1 ? 1 : 0);
    }
    void request(int button, Runnable action) {
        if (pending != null) return;
        pending = action; exitElapsed = 0; exploded = button;
    }
    boolean pending() { return pending != null; }
    boolean buttonsEnabled() { return !pending() && (state == MainMenuState.OPEN || state == MainMenuState.OPENING); }
    float fade() { return pending() ? MainMenuMotion.clamp(exitElapsed / EXIT_MS) : 0; }
    float explosion(int button) { return pending() && exploded == button ? MainMenuMotion.outExpo(exitElapsed / EXIT_MS) : 0; }
    float buttonAlpha(int button) { return reveal() * (1 - (pending() && exploded == button ? fade() : 0)); }
    float hover(int button) { return hover[button].value; }
    float scale() { return cookie.scale() * transitionScale(); }
    MenuCookieMotion cookie() { return cookie; }
    void resetTrackAnalysis() { cookie.resetTrack(); }
    boolean advance(double ms, MenuBeatTiming.Beat beat, MenuAudioAnalysis analysis) {
        ms = Math.max(0, ms); elapsed += ms;
        if (state == MainMenuState.OPENING && elapsed >= EXPAND_MS) state = MainMenuState.OPEN;
        if (state == MainMenuState.CLOSING && elapsed >= CLOSE_MS) state = MainMenuState.CLOSED;
        for (Spring h : hover) h.advance(ms, 500);
        cookie.advance(ms, beat, analysis);
        if (pending == null) return false;
        exitElapsed += ms;
        if (exitElapsed < EXIT_MS) return false;
        Runnable action = pending; pending = null; action.run(); return true;
    }
    /** Capture uses the same controller with explicit state/time; no wall clock or history leaks. */
    void captureState(MainMenuState requested, double ms) {
        if (requested == MainMenuState.OPEN || requested == MainMenuState.CLOSING) { toggle(); elapsed = EXPAND_MS; state = MainMenuState.OPEN; }
        if (requested == MainMenuState.OPENING || requested == MainMenuState.CLOSING) toggle();
        elapsed = ms;

    }
    void settleCapture() {
        for (Spring h : hover) h.advance(500, 500);
    }
    private static final class Spring {
        float value, start, target;
        double ms;
        void target(float next) { if (target != next) { start = value; target = next; ms = 0; } }
        void advance(double delta, double duration) { advance(delta, duration, true); }
        void advance(double delta, double duration, boolean elastic) {
            ms += delta;
            float t = MainMenuMotion.clamp(ms / duration);
            float ease = elastic ? MainMenuMotion.elastic(t) : 1 - (1 - t) * (1 - t);
            value = start + (target - start) * ease;
        }
    }
}
