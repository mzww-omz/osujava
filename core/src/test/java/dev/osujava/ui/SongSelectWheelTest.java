package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import dev.osujava.OsuJavaGame;
import dev.osujava.audio.AudioVolumes.Channel;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.library.BeatmapLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectWheelTest {
    private Input oldInput;
    private Graphics oldGraphics;
    private InputProcessor processor;
    private int width = 1280, height = 720, pointerX, pointerY;
    private boolean alt;
    private final BeatmapLibrary library = new BeatmapLibrary();

    @BeforeEach void installBackend() {
        oldInput = Gdx.input; oldGraphics = Gdx.graphics;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> {
            return switch(m.getName()) {
                case "getInputProcessor" -> processor;
                case "setInputProcessor" -> { processor = (InputProcessor)a[0]; yield null; }
                case "getX" -> pointerX;
                case "getY" -> pointerY;
                case "isKeyPressed" -> alt && ((int)a[0] == Input.Keys.ALT_LEFT || (int)a[0] == Input.Keys.ALT_RIGHT);
                default -> m.getReturnType() == boolean.class ? false : null;
            };
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a) ->
                switch(m.getName()) { case "getWidth" -> width; case "getHeight" -> height; default -> null; });
    }
    @AfterEach void restoreBackend() { Gdx.input = oldInput; Gdx.graphics = oldGraphics; }

    private OsuJavaGame game() {
        return new OsuJavaGame(null,null) { @Override public BeatmapLibrary library() { return library; } };
    }
    private void addBeatmap() {
        var a = new BeatmapDifficulty("Song","Artist","Creator","Easy",0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,null);
        var b = new BeatmapDifficulty("Song","Artist","Creator","Hard",0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,null);
        library.add(new BeatmapSet("fixture","Song","Artist","Creator",null,null,List.of(a,b),List.of()));
    }
    private void pointer(float x, float y) { pointerX = Math.round(width * x); pointerY = Math.round(height * y); }

    @Test void blankSpaceHeaderAndToolbarAdjustVolumeWithoutF4AtAllResolutions() {
        addBeatmap();
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440}}) {
            width = size[0]; height = size[1];
            var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
            try {
                for (float[] point : new float[][]{{.05f,.5f},{.8f,.02f},{.8f,.98f},{.99f,.95f}}) {
                    game.volumeHud().close(); pointer(point[0],point[1]);
                    assertFalse(screen.usesMouseWheelAt(pointerX,pointerY));
                    int before = game.volumeHud().percent(Channel.MASTER);
                    assertTrue(processor.scrolled(0,1));
                    assertEquals(before - 5,game.volumeHud().percent(Channel.MASTER));
                    assertTrue(game.volumeHud().active());
                    assertTrue(processor.scrolled(0,-1));
                    assertEquals(before,game.volumeHud().percent(Channel.MASTER));
                }
            } finally { screen.dispose(); }
        }
    }
    @Test void hoveredRowsKeepSelectionWhileAltAndAnOpenHudOverrideTheWheel() {
        addBeatmap(); var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f);
            assertTrue(screen.usesMouseWheelAt(pointerX,pointerY),"Rows must reserve the wheel before the first render");
            assertTrue(processor.scrolled(0,1));
            assertEquals(100,game.volumeHud().percent(Channel.MASTER)); assertFalse(game.volumeHud().active());
            alt = true; assertTrue(processor.scrolled(0,1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            alt = false; assertTrue(processor.scrolled(0,1)); assertEquals(90,game.volumeHud().percent(Channel.MASTER));
            game.volumeHud().advance(2); assertFalse(game.volumeHud().active());
            assertTrue(processor.scrolled(0,-1)); assertEquals(90,game.volumeHud().percent(Channel.MASTER));
            assertTrue(processor.keyDown(Input.Keys.F4));
            assertTrue(processor.scrolled(0,-1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }
    @Test void emptyLibraryDoesNotReserveWheelEvenInTheBeatmapArea() {
        var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); assertFalse(screen.usesMouseWheelAt(pointerX,pointerY));
            assertTrue(processor.scrolled(0,1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            assertTrue(game.volumeHud().active());
        } finally { screen.dispose(); }
    }
}
