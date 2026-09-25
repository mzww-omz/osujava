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
