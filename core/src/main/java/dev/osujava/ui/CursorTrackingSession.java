package dev.osujava.ui;

import dev.osujava.gameplay.*;
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import java.util.List;

/** Forwards input unchanged, then observes the actual Session pointer/action snapshot.
 * Both human input and DebugAutoPlayer use this same adapter; rendering never calls it.
 */
public final class CursorTrackingSession implements GameplaySession {
    private final GameplaySession session;
    private final GameClock clock;
    private final LegacyCursorVisual cursor;
    public CursorTrackingSession(GameplaySession session, GameClock clock, LegacyCursorVisual cursor) {
        this.session = session; this.clock = clock; this.cursor = cursor;
    }
    private void observe(boolean press) { cursor.input(clock.nowMs(), session.pointerState(), press); }
    @Override public void click(double x, double y) { session.click(x, y); observe(true); }
    @Override public void press(GameInputAction action, double x, double y) { session.press(action, x, y); observe(true); }
    @Override public void release(GameInputAction action) { session.release(action); observe(false); }
    @Override public void pointerMoved(double x, double y) { session.pointerMoved(x, y); observe(false); }
    @Override public void pointerReleased() { session.pointerReleased(); observe(false); }
    @Override public PointerState pointerState() { return session.pointerState(); }
    @Override public GameplayState update() { return session.update(); }
    @Override public GameplayState state() { return session.state(); }
    @Override public void finish() { session.finish(); }
    @Override public List<GameplayAudioCue> drainAudioCues() { return session.drainAudioCues(); }
}
