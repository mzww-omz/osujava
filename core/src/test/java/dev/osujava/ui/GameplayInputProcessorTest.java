package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.JudgementWindows;
import dev.osujava.ruleset.osu.OsuGameplaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayInputProcessorTest {
    @Test
    void pressOutsidePlayfieldStillHoldsLogicalActionForSliderTracking() {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = proxy(Graphics.class, method -> method.equals("getHeight") ? 384 : null);
        try {
            ManualClock clock = new ManualClock();
            clock.set(1000);
            OsuGameplaySession session = session(clock);
            GameplayInputProcessor input = new GameplayInputProcessor(session, () -> { });
            input.setViewport(PlayfieldViewport.fit(512, 384));
            input.touchDown(-40, 100, 0, Input.Buttons.LEFT);
            assertFalse(session.state().sliders().getFirst().headHit());
            clock.set(1100);
            input.mouseMoved(128, 100);
            assertTrue(session.update().sliders().getFirst().tracking());
            input.touchUp(-40, 100, 0, Input.Buttons.LEFT);
            assertFalse(session.update().sliders().getFirst().tracking());
        } finally {
            Gdx.graphics = previousGraphics;
        }
    }

    @Test
    void rightMouseAndKeyboardKeysShareTrackingAndReleaseOnlyWhenAllInputsAreUp() {
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        int[] cursor = {100, 100};
        Gdx.graphics = proxy(Graphics.class, method -> method.equals("getHeight") ? 384 : null);
        Gdx.input = proxy(Input.class, method -> switch (method) {
            case "getX" -> cursor[0];
            case "getY" -> cursor[1];
            default -> null;
        });

        try {
            ManualClock mouseClock = new ManualClock();
            mouseClock.set(1000);
            OsuGameplaySession mouseSession = session(mouseClock);
            GameplayInputProcessor mouse = new GameplayInputProcessor(mouseSession, () -> { });
            mouse.setViewport(PlayfieldViewport.fit(512, 384));

            assertTrue(mouse.touchDown(100, 100, 0, Input.Buttons.RIGHT));
            assertTrue(mouseSession.state().sliders().getFirst().headHit());
            assertTrue(mouseSession.state().sliders().getFirst().tracking());

            assertTrue(mouse.touchDown(100, 100, 0, Input.Buttons.LEFT));
            mouse.touchUp(100, 100, 0, Input.Buttons.RIGHT);
            mouseClock.set(1100);
            mouse.mouseMoved(128, 100);
            GameplayState whileLeftRemainsDown = mouseSession.update();
            assertTrue(whileLeftRemainsDown.sliders().getFirst().tracking());

            mouse.touchUp(128, 100, 0, Input.Buttons.LEFT);
            assertFalse(mouseSession.update().sliders().getFirst().tracking());

            ManualClock keyboardClock = new ManualClock();
            keyboardClock.set(1000);
            OsuGameplaySession keyboardSession = session(keyboardClock);
            GameplayInputProcessor keyboard = new GameplayInputProcessor(keyboardSession, () -> { });
            keyboard.setViewport(PlayfieldViewport.fit(512, 384));
            cursor[0] = 100;
            cursor[1] = 100;

            assertTrue(keyboard.keyDown(Input.Keys.Z));
            assertTrue(keyboard.keyDown(Input.Keys.X));
            assertTrue(keyboardSession.state().sliders().getFirst().headHit());
            keyboardClock.set(1100);
            cursor[0] = 128;
            keyboard.mouseMoved(cursor[0], cursor[1]);
            assertTrue(keyboardSession.update().sliders().getFirst().tracking());

            assertTrue(keyboard.keyUp(Input.Keys.Z));
            assertTrue(keyboardSession.update().sliders().getFirst().tracking());
            assertTrue(keyboard.keyUp(Input.Keys.X));
            assertFalse(keyboardSession.update().sliders().getFirst().tracking());
        } finally {
            Gdx.graphics = previousGraphics;
            Gdx.input = previousInput;
        }
    }

    @Test
    void debugAutoInputIgnoresHumanHitsButKeepsEscapeNavigation() {
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        Gdx.graphics = proxy(Graphics.class, method -> method.equals("getHeight") ? 384 : null);
        Gdx.input = proxy(Input.class, method -> switch (method) {
            case "getX" -> 100;
            case "getY" -> 284;
            default -> null;
        });

        try {
            ManualClock clock = new ManualClock();
            clock.set(1000);
            OsuGameplaySession session = session(clock);
            int[] backs = {0};
            GameplayInputProcessor input = new GameplayInputProcessor(session, () -> backs[0]++, false);
            input.setViewport(PlayfieldViewport.fit(512, 384));

            assertTrue(input.touchDown(100, 284, 0, Input.Buttons.LEFT));
            assertTrue(input.mouseMoved(300, 100));
            assertTrue(input.keyDown(Input.Keys.Z));
            input.touchUp(100, 284, 0, Input.Buttons.LEFT);
            input.keyUp(Input.Keys.Z);
            assertFalse(session.state().sliders().getFirst().headHit());
            assertFalse(session.state().sliders().getFirst().tracking());

            assertTrue(input.keyDown(Input.Keys.ESCAPE));
            assertEquals(1, backs[0]);
        } finally {
            Gdx.graphics = previousGraphics;
            Gdx.input = previousInput;
        }
    }

    @Test
    void visualReadsExactSessionPositionAndAllFourPhysicalInputsWithoutPrematureContract() {
        Graphics oldGraphics = Gdx.graphics; Input oldInput = Gdx.input;
        Gdx.graphics = proxy(Graphics.class, m -> m.equals("getHeight") ? 384 : null);
        Gdx.input = proxy(Input.class, m -> m.equals("getX") || m.equals("getY") ? 100 : null);
        try {
            var clock = new ManualClock(); clock.set(1000);
            var actual = session(clock);
            var visual = new dev.osujava.ruleset.osu.render.LegacyCursorVisual(
                    dev.osujava.skin.SkinConfiguration.Cursor.defaults(), true, true, 40);
            var tracked = new CursorTrackingSession(actual, clock, visual);
            var input = new GameplayInputProcessor(tracked, () -> { }); input.setViewport(PlayfieldViewport.fit(512, 384));
            input.keyDown(Input.Keys.Z); input.keyDown(Input.Keys.X);
            input.touchDown(100, 100, 0, Input.Buttons.LEFT); input.touchDown(100, 100, 0, Input.Buttons.RIGHT);
            clock.set(1100); input.mouseMoved(-20, 400);
            assertEquals(actual.pointerState().x(), visual.cursor(1100).x());
            assertEquals(actual.pointerState().y(), visual.cursor(1100).y());
            input.keyUp(Input.Keys.Z); input.touchUp(100, 100, 0, Input.Buttons.LEFT); input.keyUp(Input.Keys.X);
            assertTrue(actual.pointerState().pressed()); assertEquals(1.3, visual.expandedScale(1200), 1e-6);
            input.touchUp(100, 100, 0, Input.Buttons.RIGHT); assertFalse(actual.pointerState().pressed());
            assertEquals(1.075, visual.expandedScale(1150), 1e-6); assertEquals(1, visual.expandedScale(1200));
        } finally { Gdx.graphics = oldGraphics; Gdx.input = oldInput; }
    }

    @Test
    void autoAndManualAdapterPreserveJudgementsAndAutoPositionAtEverySample() {
        var clock = new ManualClock(); var plain = session(clock); var actual = session(clock);
        var visual = new dev.osujava.ruleset.osu.render.LegacyCursorVisual(
                dev.osujava.skin.SkinConfiguration.Cursor.defaults(), true, true, 40);
        var tracked = new CursorTrackingSession(actual, clock, visual);
        // The fixture session and the Auto target use the same beatmap.
        HitObject slider = new HitObject(100, 100, 1000, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(100, 100), new BeatmapPoint(200, 100)))), 0, 100));
        var difficulty = new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0, "", "",
                new DifficultySettings(5, 5, 5, 5, 1.4, 1),
                List.of(new dev.osujava.beatmap.TimingPoint(0, 500, 4, 0, 0, 100, true, 0)), List.of(slider), null, null);
        var a = new dev.osujava.ruleset.osu.DebugAutoPlayer(difficulty, clock, tracked);
        var b = new dev.osujava.ruleset.osu.DebugAutoPlayer(difficulty, clock, plain);
        for (int t = 0; t < 2500; t += 8) {
            clock.set(t); a.update(); b.update();
            assertEquals(plain.update().score(), tracked.update().score());
            a.afterSessionUpdate(); b.afterSessionUpdate(); visual.advance(t);
            if (visual.positioned()) {
                assertEquals(a.cursorX(), visual.cursor(t).x()); assertEquals(a.cursorY(), visual.cursor(t).y());
                assertEquals(actual.pointerState().x(), visual.cursor(t).x());
            }
            assertEquals(a.cursorX(), b.cursorX()); assertEquals(a.cursorY(), b.cursorY());
        }
    }

    private OsuGameplaySession session(ManualClock clock) {
        HitObject slider = new HitObject(100, 100, 1000, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(100, 100), new BeatmapPoint(200, 100)))), 0, 100));
        BeatmapDifficulty difficulty = new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0,
                "", "", new DifficultySettings(5, 5, 5, 5, 1.4, 1),
                List.of(new dev.osujava.beatmap.TimingPoint(0, 500, 4, 0, 0, 100, true, 0)),
                List.of(slider), null, null);
        return new OsuGameplaySession(difficulty, clock, new JudgementWindows(49.5, 99.5, 149.5));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.util.function.Function<String, Object> valueForMethod) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object value = valueForMethod.apply(method.getName());
            if (value != null) return value;
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0f;
            if (returnType == double.class) return 0d;
            return null;
        });
    }

    private static final class ManualClock implements GameClock {
        private long timeMs;

        @Override
        public long nowMs() {
            return timeMs;
        }

        void set(long timeMs) {
            this.timeMs = timeMs;
        }
    }
}
