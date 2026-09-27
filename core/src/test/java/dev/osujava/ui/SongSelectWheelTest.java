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

    @Test void outsideBothBrowsersHeaderAndToolbarAdjustVolumeWithoutF4AtAllResolutions() {
        addBeatmap();
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440}}) {
            width = size[0]; height = size[1];
            var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
            try {
                for (float[] point : new float[][]{{.42f,.5f},{.8f,.02f},{.8f,.98f},{.99f,.95f}}) {
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
    @Test void leftBrowserReservesWheelWithoutMovingCarousel() throws Exception {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.05f,.5f); assertTrue(screen.usesMouseWheelAt(pointerX,pointerY));
            var field=SongSelectScreen.class.getDeclaredField("carousel"); field.setAccessible(true);
            var carousel=(SongSelectCarousel)field.get(screen);
            var before=carousel.rows(); assertTrue(processor.scrolled(0,1)); assertSame(before,carousel.rows());
            assertEquals(100,game.volumeHud().percent(Channel.MASTER)); assertFalse(game.volumeHud().active());
        } finally { screen.dispose(); }
    }
    @Test void scoreScrollAndCarouselTargetsAreIndependentAndDifficultyRefreshIsImmediate() throws Exception {
        var a = new BeatmapDifficulty("Song","Artist","Creator","Easy",0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,null,java.nio.file.Path.of("easy.osu"));
        var b = new BeatmapDifficulty("Song","Artist","Creator","Hard",0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,null,java.nio.file.Path.of("hard.osu"));
        library.add(new BeatmapSet("fixture","Song","Artist","Creator",null,null,List.of(a,b),List.of()));
        var game=game();
        var identity=dev.osujava.score.DifficultyIdentity.of("fixture",a);
        for(int i=0;i<100;i++)game.localScores().save(new dev.osujava.score.LocalScore(new java.util.UUID(0,i+1),identity,0,
                new dev.osujava.gameplay.ScoreState(i,0,1,1,0,0,0,1)),dev.osujava.gameplay.GameplayRunMode.MANUAL);
        var screen=new SongSelectScreen(game); game.navigate(screen); screen.resize(width,height);
        try {
            var f=SongSelectScreen.class.getDeclaredField("scores"); f.setAccessible(true); var scores=(ScoreBrowserModel)f.get(screen);
            var carousel=carousel(screen); float before=carousel.scrollTarget();
            pointer(.05f,.5f); assertTrue(processor.scrolled(0,3)); assertEquals(3,scores.first()); assertEquals(before,carousel.scrollTarget());
            pointer(.8f,.5f); assertTrue(processor.scrolled(0,1)); assertEquals(3,scores.first());
            processor.keyDown(Input.Keys.RIGHT); assertEquals(dev.osujava.score.DifficultyIdentity.of("fixture",b),scores.target());
            assertTrue(scores.rows().isEmpty()); assertEquals(0,scores.first());
            processor.keyDown(Input.Keys.LEFT); assertEquals(100,scores.rows().size()); assertEquals(identity,scores.target());
            for(var sort:SongBrowserModel.Sort.values())for(var group:SongBrowserModel.Group.values()) {
                screen.browserMode(sort,group); assertEquals(identity,scores.target()); assertEquals(100,scores.rows().size());
            }
            for(char c:"nonexistent".toCharArray())processor.keyTyped(c);
            assertNull(scores.target()); assertTrue(scores.rows().isEmpty());
            for(int i=0;i<11;i++)processor.keyDown(Input.Keys.BACKSPACE);
            assertEquals(identity,scores.target()); assertEquals(100,scores.rows().size());
        } finally { screen.dispose(); }
    }

    @Test void carouselKeepsWheelUnlessAltOrAnExplicitHudOverridesIt() {
        addBeatmap(); var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f);
            assertTrue(screen.usesMouseWheelAt(pointerX,pointerY),"Rows must reserve the wheel before the first render");
            assertTrue(processor.scrolled(0,1));
            assertEquals(100,game.volumeHud().percent(Channel.MASTER)); assertFalse(game.volumeHud().active());
            alt = true; assertTrue(processor.scrolled(0,1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            alt = false; assertTrue(processor.scrolled(0,1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            game.volumeHud().advance(2); assertFalse(game.volumeHud().active());
            assertTrue(processor.scrolled(0,-1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            assertTrue(processor.keyDown(Input.Keys.F4));
            assertTrue(processor.scrolled(0,-1)); assertEquals(100,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }
    @Test void emptyLibraryStillReservesTheCarouselViewport() {
        var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); assertTrue(screen.usesMouseWheelAt(pointerX,pointerY));
            assertTrue(processor.scrolled(0,1)); assertEquals(100,game.volumeHud().percent(Channel.MASTER));
            assertFalse(game.volumeHud().active());
        } finally { screen.dispose(); }
    }
    private SongSelectCarousel carousel(SongSelectScreen screen) throws Exception {
        var field = SongSelectScreen.class.getDeclaredField("carousel"); field.setAccessible(true);
        return (SongSelectCarousel) field.get(screen);
    }
    private int selected(SongSelectScreen screen, String fieldName) throws Exception {
        var field = SongSelectScreen.class.getDeclaredField(fieldName); field.setAccessible(true);
        return field.getInt(screen);
    }

    @Test void rowGapsAndSpaceLeftOfCurvedRowsScrollWithoutOpeningVolumeAtAllResolutions() throws Exception {
        addBeatmap();
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440}}) {
            width=size[0]; height=size[1];
            var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
            try {
                var model=carousel(screen);
                for (float[] point : new float[][]{{.51f,.5f},{.8f,.2f},{.95f,.3f}}) {
                    pointer(point[0],point[1]);
                    assertTrue(screen.usesMouseWheelAt(pointerX,pointerY));
                    float before=model.scrollTarget();
                    assertTrue(processor.scrolled(0,.25f));
                    assertTrue(model.scrollTarget()>before);
                    assertEquals(100,game.volumeHud().percent(Channel.MASTER));
                    assertFalse(game.volumeHud().active());
                }
            } finally { screen.dispose(); }
        }
    }

    @Test void artworkOverlappingCarouselDoesNotStealWheelAtAllResolutions() throws Exception {
        addBeatmap();
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440}}) {
            width=size[0]; height=size[1];
            var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
            try {
                screen.resize(width,height);
                pointer(.97f,.85f);
                assertTrue(screen.usesMouseWheelAt(pointerX,pointerY));
                float before=carousel(screen).scrollTarget();
                assertTrue(processor.scrolled(0,.25f));
                assertTrue(carousel(screen).scrollTarget()>before);
                assertEquals(100,game.volumeHud().percent(Channel.MASTER));
                assertFalse(game.volumeHud().active());
            } finally { screen.dispose(); }
        }
    }

    @Test void wheelBrowsesPastOtherSetsWithoutSelectingOrExpandingThem() throws Exception {
        addBeatmap();
        var diff=library.all().iterator().next().difficulties().get(0);
        for (int i=0;i<10;i++) library.add(new BeatmapSet("set"+i,"Other "+i,"Artist","Creator",null,null,List.of(diff),List.of()));
        var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f);
            var model=carousel(screen); var rows=model.rows();
            int set=selected(screen,"selectedSetIndex"), difficulty=selected(screen,"selectedDifficultyIndex");
            for (int i=0;i<20;i++) assertTrue(processor.scrolled(0,1));
            assertSame(rows,model.rows());
            assertEquals(set,selected(screen,"selectedSetIndex"));
            assertEquals(difficulty,selected(screen,"selectedDifficultyIndex"));
            assertEquals(model.maxScroll(),model.scrollTarget());
            assertEquals(100,game.volumeHud().percent(Channel.MASTER));
            assertFalse(game.volumeHud().active());
            for (int i=0;i<20;i++) assertTrue(processor.scrolled(0,-1));
            assertEquals(0,model.scrollTarget());
            assertSame(rows,model.rows());
        } finally { screen.dispose(); }
    }

    @Test void volumeFeedbackFromOutsideCarouselDoesNotStealWheelAfterPointerReturns() {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.42f,.5f); assertTrue(processor.scrolled(0,1));
            assertEquals(95,game.volumeHud().percent(Channel.MASTER)); assertTrue(game.volumeHud().active());
            pointer(.51f,.5f); assertTrue(processor.scrolled(0,1));
            assertEquals(95,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }

    @Test void extremeFiniteWheelAndTinyTrackpadDeltasRemainSafe() throws Exception {
        addBeatmap();
        var diff = library.all().iterator().next().difficulties().getFirst();
        for (int i = 0; i < 30; i++) library.add(new BeatmapSet("set" + i,"Other " + i,"Artist","Creator",null,null,List.of(diff),List.of()));
        var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); var model = carousel(screen);
            float target = model.scrollTarget();
            assertTrue(processor.scrolled(0,.0001f));
            assertEquals(target + .0001f * model.rowHeight(),model.scrollTarget(),.001);
            assertTrue(processor.scrolled(0,Float.MAX_VALUE));
            assertEquals(model.maxScroll(),model.scrollTarget());
            assertTrue(Float.isFinite(model.scrollVelocity()));
            assertTrue(processor.scrolled(0,-Float.MAX_VALUE));
            assertEquals(0,model.scrollTarget());
            assertEquals(100,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }

}
