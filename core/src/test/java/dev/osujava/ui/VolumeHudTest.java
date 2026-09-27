package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import dev.osujava.OsuJavaGame;
import dev.osujava.audio.AudioVolumes;
import dev.osujava.beatmap.*;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.JudgementWindows;
import dev.osujava.ruleset.osu.OsuGameplaySession;
import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static dev.osujava.audio.AudioVolumes.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

class VolumeHudTest {
    @Test void startsWithMasterAndAdjustsChannelsIndependentlyAtFivePercent() {
        var volumes = new AudioVolumes(); var hud = new VolumeHud(volumes);
        hud.adjust(-1); assertEquals(MASTER, hud.selected()); assertEquals(95, hud.percent(MASTER));
        hud.select(1); hud.adjust(-2); assertEquals(MUSIC, hud.selected()); assertEquals(90, hud.percent(MUSIC));
        hud.select(1); hud.adjust(-3); assertEquals(EFFECT, hud.selected()); assertEquals(85, hud.percent(EFFECT));
        assertEquals(95, hud.percent(MASTER));
        hud.adjust(-100); assertEquals(0, hud.percent(EFFECT));
        hud.adjust(100); assertEquals(100, hud.percent(EFFECT));
        hud.close(); hud.open(); assertEquals(MASTER, hud.selected());
        hud.select(-1); assertEquals(EFFECT, hud.selected());
    }
    @Test void fadesInHoldsFadesOutAndRefreshesEvenAtVolumeLimit() {
        var hud = new VolumeHud(new AudioVolumes());
        assertFalse(hud.active()); assertEquals(0, hud.alpha());
        hud.open(); hud.advance(.06f); assertEquals(.5f, hud.alpha(), 1e-5);
        assertTrue(hud.scale() > .94f && hud.scale() < 1);
        hud.advance(1.3f); assertEquals(1, hud.alpha());
        hud.adjust(1); hud.advance(1.4f); assertTrue(hud.active()); assertEquals(1, hud.alpha());
        hud.advance(.2f); assertFalse(hud.active()); assertTrue(hud.alpha() > 0 && hud.alpha() < 1);
        hud.advance(.3f); assertEquals(0, hud.alpha());
        hud.select(1); hud.close(); hud.advance(1); hud.adjust(-1);
        assertEquals(MASTER, hud.selected()); hud.advance(.12f); assertEquals(1, hud.alpha(), 1e-5);
        hud.close(); hud.advance(.11f); assertEquals(.5f, hud.alpha(), 1e-5);
        hud.advance(10); assertEquals(0, hud.alpha());
    }
    @Test void arcFollowsSmoothlyWithoutOvershootAndReachesExactEndpoints() {
        var volumes = new AudioVolumes(); var hud = new VolumeHud(volumes);
        hud.adjust(-10); hud.advance(.045f);
        assertTrue(hud.displayed(MASTER) > .5f && hud.displayed(MASTER) < 1);
        assertEquals(1, hud.displayed(MUSIC));
        hud.advance(1); assertEquals(.5f, hud.displayed(MASTER));
        hud.adjust(-10); hud.advance(1); assertEquals(0, hud.displayed(MASTER));
        hud.adjust(20); hud.advance(1); assertEquals(1, hud.displayed(MASTER));
    }
    @Test void ringHasCorrectClockwiseSweepAtZeroTwentyFiftyAndHundredPercent() {
        assertEquals(0, VolumeHudGeometry.end(0));
        assertEquals(0, VolumeHudGeometry.x(0)); assertEquals(1, VolumeHudGeometry.y(0));
        assertEquals(38.4f, VolumeHudGeometry.end(.2f), 1e-5);
        float quarter = VolumeHudGeometry.end(.25f);
        assertEquals(1, VolumeHudGeometry.x(quarter)); assertEquals(0, VolumeHudGeometry.y(quarter), 1e-6);
        float half = VolumeHudGeometry.end(.5f);
        assertEquals(96, half); assertEquals(0, VolumeHudGeometry.x(half), 1e-6);
        assertEquals(-1, VolumeHudGeometry.y(half));
        assertEquals(192, VolumeHudGeometry.end(1));
        assertEquals(VolumeHudGeometry.x(0), VolumeHudGeometry.x(192));
        assertEquals(VolumeHudGeometry.y(0), VolumeHudGeometry.y(192));
    }
    @Test void layoutScalesAtAllRequestedResolutionsAndStaysInsideTheWindow() {
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440},{960,540},{720,1280}}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var layout = new VolumeHudLayout(ui.width(),ui.height());
            assertTrue(layout.radius(MASTER) > layout.radius(MUSIC) * 2);
            for (var channel : AudioVolumes.Channel.values()) {
                assertTrue(layout.x(channel) > ui.width() / 2);
                assertTrue(layout.x(channel) - layout.radius(channel) - 15 > 0);
                assertTrue(layout.x(channel) + layout.radius(channel) + 15 < ui.width());
                assertTrue(layout.y(channel) - layout.radius(channel) - 30 > 0);
                assertTrue(layout.y(channel) + layout.radius(channel) + 35 < ui.height());
            }
        }
    }
    @Test void shortcutsAndWheelConsumeOnlyVolumeControlsAndPreserveSongSelectScrolling() {
        var hud = new VolumeHud(new AudioVolumes()); boolean[] reserve = {true}, alt = {false};
        var input = new VolumeHudInput(hud, () -> reserve[0], () -> alt[0]);
        assertFalse(input.scrolled(0,1)); assertFalse(input.keyDown(Input.Keys.DOWN));
        alt[0] = true; assertTrue(input.scrolled(0,1)); assertEquals(95,hud.percent(MASTER));
        alt[0] = false; assertFalse(input.scrolled(0,-1)); assertEquals(95,hud.percent(MASTER));
        assertTrue(input.keyDown(Input.Keys.TAB)); assertEquals(MUSIC,hud.selected());
        assertTrue(input.keyDown(Input.Keys.DOWN)); assertEquals(95,hud.percent(MUSIC));
        assertTrue(input.keyDown(Input.Keys.RIGHT)); assertEquals(EFFECT,hud.selected());
        assertTrue(input.keyDown(Input.Keys.LEFT)); assertEquals(MUSIC,hud.selected());
        assertFalse(input.keyDown(Input.Keys.Z)); assertFalse(input.keyUp(Input.Keys.Z));
        assertFalse(input.touchDown(0,0,0,Input.Buttons.LEFT)); assertFalse(input.touchUp(0,0,0,Input.Buttons.LEFT));
        assertFalse(input.mouseMoved(0,0)); assertFalse(input.touchDragged(0,0,0));
        assertTrue(input.keyDown(Input.Keys.ESCAPE)); assertFalse(hud.active());
        assertFalse(input.keyDown(Input.Keys.ESCAPE));
        reserve[0] = false; assertTrue(input.scrolled(0,1)); assertEquals(MASTER,hud.selected());
        assertEquals(90,hud.percent(MASTER));
        assertTrue(input.keyDown(Input.Keys.F4)); assertFalse(hud.active());
        assertTrue(input.keyDown(Input.Keys.F4)); assertTrue(hud.active());
        assertFalse(input.scrolled(0,Float.NaN)); assertFalse(input.scrolled(1,0));
    }
    @Test void globalInputSurvivesScreenNavigationAndGameplayContinuesRendering() {
        var oldInput = Gdx.input; var oldGraphics = Gdx.graphics;
        InputProcessor[] processor = {null}; int[] hitDown = {0}, hitUp = {0}, renders = {0};
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class}, (p,m,a) -> {
            if (m.getName().equals("setInputProcessor")) { processor[0] = (InputProcessor)a[0]; return null; }
            if (m.getName().equals("getInputProcessor")) return processor[0];
            return m.getReturnType() == boolean.class ? false : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class}, (p,m,a) ->
                switch(m.getName()) { case "getWidth" -> 1280; case "getHeight" -> 720; case "getDeltaTime" -> .016f; default -> null; });
        try {
            var game = new OsuJavaGame(null);
            for (int screen = 0; screen < 4; screen++) {
                game.navigate(new ScreenAdapter() {
                    @Override public void show() { Gdx.input.setInputProcessor(new InputAdapter() {
                        @Override public boolean keyDown(int key) { if (key == Input.Keys.Z) hitDown[0]++; return true; }
                        @Override public boolean keyUp(int key) { if (key == Input.Keys.Z) hitUp[0]++; return true; }
                    }); }
                    @Override public void render(float delta) { renders[0]++; }
                });
                assertInstanceOf(InputMultiplexer.class,processor[0]);
                processor[0].keyDown(Input.Keys.F4); processor[0].keyDown(Input.Keys.Z);
                processor[0].scrolled(0,1); processor[0].keyUp(Input.Keys.Z);
                game.render(); assertTrue(game.volumeHud().active());
                processor[0].keyDown(Input.Keys.ESCAPE);
            }
            assertEquals(4,renders[0]); assertEquals(4,hitDown[0]); assertEquals(4,hitUp[0]);
            assertEquals(80,game.volumeHud().percent(MASTER));
            game.resume(); assertEquals(2,((InputMultiplexer)processor[0]).getProcessors().size);
        } finally { Gdx.input = oldInput; Gdx.graphics = oldGraphics; }
    }
    @Test void realGameplayJudgementsAndHeldInputAreUnchangedWhileAdjustingTheHud() {
        var oldInput = Gdx.input; var oldGraphics = Gdx.graphics;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) ->
                switch(m.getName()) { case "getX" -> 640; case "getY" -> 360; default -> false; });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a) ->
                switch(m.getName()) { case "getWidth" -> 1280; case "getHeight" -> 720; default -> null; });
        try {
            long[] time = {900}; GameClock clock = () -> time[0];
            var map = new BeatmapDifficulty("HUD","Test","Test","Normal",0,"","",DifficultySettings.defaults(),List.of(),
                    List.of(new HitObject(256,192,1000,HitObject.Type.CIRCLE,1,0)),null,null);
            var baseline = new OsuGameplaySession(map,clock,JudgementWindows.fromOverallDifficulty(5));
            var actual = new OsuGameplaySession(map,clock,JudgementWindows.fromOverallDifficulty(5));
            int[] back = {0}; var hitInput = new GameplayInputProcessor(actual,() -> back[0]++);
            var plainInput = new GameplayInputProcessor(baseline,() -> {});
            hitInput.setViewport(PlayfieldViewport.fit(1280,720)); plainInput.setViewport(PlayfieldViewport.fit(1280,720));
            var hud = new VolumeHud(new AudioVolumes());
            var input = new InputMultiplexer(new VolumeHudInput(hud,() -> false,() -> false),hitInput);
            input.keyDown(Input.Keys.F4); input.scrolled(0,1); input.keyDown(Input.Keys.TAB); input.keyDown(Input.Keys.DOWN);
            assertEquals(900,clock.nowMs());
            time[0] = 1000; input.keyDown(Input.Keys.Z); plainInput.keyDown(Input.Keys.Z);
            assertEquals(baseline.update().score(),actual.update().score()); assertEquals(1,actual.state().score().count300());
            assertTrue(actual.pointerState().pressed());
            input.keyDown(Input.Keys.RIGHT); input.scrolled(0,1);
            time[0] = 1200; input.keyUp(Input.Keys.Z); plainInput.keyUp(Input.Keys.Z);
            assertFalse(actual.pointerState().pressed()); assertEquals(baseline.update().score(),actual.update().score());
            input.keyDown(Input.Keys.ESCAPE); assertEquals(0,back[0]);
            input.keyDown(Input.Keys.ESCAPE); assertEquals(1,back[0]);
        } finally { Gdx.input = oldInput; Gdx.graphics = oldGraphics; }
    }
}
