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
    private boolean alt, left;
    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();
    private final BeatmapLibrary library = new BeatmapLibrary();

    @BeforeEach void installBackend() {
        oldInput = Gdx.input; oldGraphics = Gdx.graphics;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> {
            return switch(m.getName()) {
                case "getInputProcessor" -> processor;
                case "setInputProcessor" -> { processor = (InputProcessor)a[0]; yield null; }
                case "getX" -> pointerX;
                case "getY" -> pointerY;
                case "isKeyPressed" -> heldKeys.contains((int)a[0]) || alt && ((int)a[0] == Input.Keys.ALT_LEFT || (int)a[0] == Input.Keys.ALT_RIGHT);
                case "isButtonPressed" -> left && (int)a[0] == Input.Buttons.LEFT;
                default -> m.getReturnType() == boolean.class ? false : null;
            };
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a) ->
                switch(m.getName()) { case "getWidth" -> width; case "getHeight" -> height; default -> null; });
    }
    @AfterEach void restoreBackend() { Gdx.input = oldInput; Gdx.graphics = oldGraphics; }

    private OsuJavaGame game() {
        return new OsuJavaGame(null,null) {
            @Override public BeatmapLibrary library() { return library; }
            @Override public dev.osujava.ruleset.osu.OsuRuleset osuRuleset() { return new dev.osujava.ruleset.osu.OsuRuleset(); }
        };
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
                    assertTrue(wheel(1));
                    assertEquals(before - 5,game.volumeHud().percent(Channel.MASTER));
                    assertTrue(game.volumeHud().active());
                    assertTrue(wheel(-1));
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
            var before=carousel.rows(); assertTrue(wheel(1)); assertSame(before,carousel.rows());
            assertEquals(100,game.volumeHud().percent(Channel.MASTER)); assertFalse(game.volumeHud().active());
        } finally { screen.dispose(); }
    }
    @Test void scoreScrollAndCarouselTargetsAreIndependentAndDifficultyRefreshIsImmediate() throws Exception {
        var objects = List.of(new dev.osujava.beatmap.HitObject(0,0,1000,dev.osujava.beatmap.HitObject.Type.CIRCLE,1,0));
        var a = new BeatmapDifficulty("Song","Artist","Creator","Easy",0,"","",DifficultySettings.defaults(),List.of(),objects,null,null,java.nio.file.Path.of("easy.osu"));
        var b = new BeatmapDifficulty("Song","Artist","Creator","Hard",0,"","",DifficultySettings.defaults(),List.of(),objects,null,null,java.nio.file.Path.of("hard.osu"));
        library.add(new BeatmapSet("fixture","Song","Artist","Creator",null,null,List.of(a,b),List.of()));
        var game=game();
        var identity=dev.osujava.score.DifficultyIdentity.of("fixture",a);
        for(int i=0;i<100;i++)game.localScores().save(new dev.osujava.score.LocalScore(new java.util.UUID(0,i+1),identity,0,
                new dev.osujava.gameplay.ScoreState(i,0,1,1,0,0,0,1)),dev.osujava.gameplay.GameplayRunMode.MANUAL);
        var screen=new SongSelectScreen(game); game.navigate(screen); screen.resize(width,height);
        try {
            var f=SongSelectScreen.class.getDeclaredField("scores"); f.setAccessible(true); var scores=(ScoreBrowserModel)f.get(screen);
            var carousel=carousel(screen); float before=carousel.scrollTarget();
            pointer(.05f,.5f); assertTrue(wheel(3)); assertEquals(1,scores.first()); assertEquals(before,carousel.scrollTarget());
            pointer(.8f,.5f); assertTrue(wheel(1)); assertEquals(1,scores.first());
            processor.keyDown(Input.Keys.DOWN); assertEquals(dev.osujava.score.DifficultyIdentity.of("fixture",b),scores.target());
            assertTrue(scores.rows().isEmpty()); assertEquals(0,scores.first());
            processor.keyDown(Input.Keys.UP); assertEquals(100,scores.rows().size()); assertEquals(identity,scores.target());
            for(var sort:SongBrowserModel.Sort.values())for(var group:SongBrowserModel.Group.values()) {
                if(group==SongBrowserModel.Group.COLLECTIONS) continue; // No membership in this scroll fixture.
                screen.browserMode(sort,group); assertEquals(identity,scores.target()); assertEquals(100,scores.rows().size());
            }
            for(char c:"nonexistent".toCharArray())processor.keyTyped(c);
            assertNull(scores.target()); assertTrue(scores.rows().isEmpty());
            for(int i=0;i<11;i++) { processor.keyDown(Input.Keys.BACKSPACE); processor.keyUp(Input.Keys.BACKSPACE); }
            assertEquals(identity,scores.target()); assertEquals(100,scores.rows().size());
        } finally { screen.dispose(); }
    }

    @Test void carouselKeepsWheelUnlessAltOrAnExplicitHudOverridesIt() {
        addBeatmap(); var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f);
            assertTrue(screen.usesMouseWheelAt(pointerX,pointerY),"Rows must reserve the wheel before the first render");
            assertTrue(wheel(1));
            assertEquals(100,game.volumeHud().percent(Channel.MASTER)); assertFalse(game.volumeHud().active());
            alt = true; assertTrue(wheel(1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            alt = false; assertTrue(wheel(1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            game.volumeHud().advance(2); assertFalse(game.volumeHud().active());
            assertTrue(wheel(-1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
            assertTrue(processor.keyDown(Input.Keys.F4));
            assertTrue(wheel(-1)); assertEquals(100,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }
    @Test void emptyLibraryStillReservesTheCarouselViewport() {
        var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); assertTrue(screen.usesMouseWheelAt(pointerX,pointerY));
            assertTrue(wheel(1)); assertEquals(100,game.volumeHud().percent(Channel.MASTER));
            assertFalse(game.volumeHud().active());
        } finally { screen.dispose(); }
    }
    private SongSelectCarousel carousel(SongSelectScreen screen) throws Exception {
        var field = SongSelectScreen.class.getDeclaredField("carousel"); field.setAccessible(true);
        return (SongSelectCarousel) field.get(screen);
    }
    private boolean wheel(float amount) {
        boolean consumed = processor.scrolled(0, amount);
        ((SongSelectWheelInput) processor).dispatch();
        return consumed;
    }
    private void update(SongSelectScreen screen, float delta) throws Exception {
        var method = SongSelectScreen.class.getDeclaredMethod("update", dev.osujava.ui.theme.UiLayout.class, float.class);
        method.setAccessible(true);
        method.invoke(screen, dev.osujava.ui.theme.UiLayout.fromPixels(width, height), delta);
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
                    float before=model.scrollVelocity();
                    assertTrue(wheel(.25f));
                    assertTrue(model.scrollVelocity()>before);
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
                assertTrue(wheel(.25f));
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
            for (int i=0;i<20;i++) assertTrue(wheel(1));
            assertSame(rows,model.rows());
            assertEquals(set,selected(screen,"selectedSetIndex"));
            assertEquals(difficulty,selected(screen,"selectedDifficultyIndex"));
            assertEquals(model.maxScroll(),model.scrollTarget());
            assertEquals(100,game.volumeHud().percent(Channel.MASTER));
            assertFalse(game.volumeHud().active());
            for (int i=0;i<20;i++) assertTrue(wheel(-1));
            assertEquals(0,model.scrollTarget());
            assertSame(rows,model.rows());
        } finally { screen.dispose(); }
    }

    @Test void volumeFeedbackFromOutsideCarouselDoesNotStealWheelAfterPointerReturns() {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.42f,.5f); assertTrue(wheel(1));
            assertEquals(95,game.volumeHud().percent(Channel.MASTER)); assertTrue(game.volumeHud().active());
            pointer(.51f,.5f); assertTrue(wheel(1));
            assertEquals(95,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }

    @Test void extremeFiniteWheelAndTinyTrackpadDeltasUseOneImpulsePerUpdate() throws Exception {
        addBeatmap();
        var diff = library.all().iterator().next().difficulties().getFirst();
        for (int i = 0; i < 30; i++) library.add(new BeatmapSet("set" + i,"Other " + i,"Artist","Creator",null,null,List.of(diff),List.of()));
        var game = game(); var screen = new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); var model = carousel(screen);
            float target = model.scrollTarget();
            assertTrue(wheel(.0001f));
            assertEquals(target + .4 / -Math.log(.994) * 1.5,model.scrollTarget(),.00001);
            assertTrue(wheel(Float.MAX_VALUE));
            assertEquals(.88 * 1500,model.scrollVelocity(),.001);
            assertTrue(Float.isFinite(model.scrollVelocity()));
            assertTrue(wheel(-Float.MAX_VALUE));
            assertEquals(.304 * 1500,model.scrollVelocity(),.001);
            assertEquals(100,game.volumeHud().percent(Channel.MASTER));
        } finally { screen.dispose(); }
    }

    @Test void productionUpdateCoalescesWheelBeforeMotionIntegration() throws Exception {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); update(screen, 0); var model=carousel(screen);
            float position=model.scrollOffset(), velocity=model.scrollVelocity();
            for (int i=0;i<20;i++) assertTrue(processor.scrolled(0,1));
            assertEquals(velocity,model.scrollVelocity());
            update(screen,.01f);
            assertEquals(.4 * Math.pow(.994,10) * 1500,model.scrollVelocity(),.001);
            assertEquals(Math.min(model.maxScroll(), position + .4 * Math.expm1(Math.log(.994)*10) / Math.log(.994) * 1.5),model.scrollOffset(),.001);
            update(screen,0); assertEquals(.4 * Math.pow(.994,10) * 1500,model.scrollVelocity(),.001);
        } finally { screen.dispose(); }
    }
    @Test void cancelledBatchNeverReachesEitherBrowserOrVolume() throws Exception {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); processor.scrolled(0,3);
            pointer(.42f,.5f); processor.scrolled(0,-3); update(screen,0);
            assertEquals(0,carousel(screen).scrollVelocity());
            assertEquals(100,game.volumeHud().percent(Channel.MASTER)); assertFalse(game.volumeHud().active());
        } finally { screen.dispose(); }
    }
    @Test void routingUsesThePointerAtDispatchInBothDirections() throws Exception {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); processor.scrolled(0,5); pointer(.42f,.5f); update(screen,0);
            assertEquals(95,game.volumeHud().percent(Channel.MASTER)); assertEquals(0,carousel(screen).scrollVelocity());
            processor.scrolled(0,5); pointer(.8f,.5f); update(screen,0);
            assertEquals(95,game.volumeHud().percent(Channel.MASTER)); assertEquals(.4 * 1500,carousel(screen).scrollVelocity(),.001);
        } finally { screen.dispose(); }
    }
    @Test void dueHeldKeyRunsAfterWheelAndRestoresKeyboardTracking() throws Exception {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            pointer(.8f,.5f); heldKeys.add(Input.Keys.DOWN); processor.keyDown(Input.Keys.DOWN);
            update(screen,0); update(screen,.25f);
            processor.scrolled(0,1); update(screen,0);
            assertFalse(carousel(screen).pointerCancellationEnabled(300),"The repeat after wheel must restore keyboard tracking");
        } finally { screen.dispose(); }
    }
    @Test void wheelPrecedesReleaseDampingOnAnExistingStationaryDrag() throws Exception {
        addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
        try {
            update(screen,.016f); // Initial instant sprites must have evaluated opacity before pressing.
            var f=SongSelectScreen.class.getDeclaredField("visibleRows"); f.setAccessible(true);
            var row=((List<?>)f.get(screen)).stream().map(SongSelectRow.class::cast).filter(SongSelectRow::selected).findFirst().orElseThrow();
            pointerX=Math.round(row.x()+120); pointerY=height-Math.round(row.y()+row.height()/2);
            left=true; update(screen,0); update(screen,.1f);
            processor.scrolled(0,1); left=false; update(screen,0);
            assertEquals(.4 * Math.pow(.95,34) * 1500,carousel(screen).scrollVelocity(),.001);
        } finally { screen.dispose(); }
    }
    @Test void pauseHideAndDisposeDiscardPendingWheelAndResumeDoesNotWrapTwice() throws Exception {
        for (String action : List.of("pause","hide","dispose")) {
            addBeatmap(); var game=game(); var screen=new SongSelectScreen(game); game.navigate(screen);
            try {
                pointer(.42f,.5f); processor.scrolled(0,1);
                switch(action) { case "pause" -> screen.pause(); case "hide" -> screen.hide(); default -> screen.dispose(); }
                ((SongSelectWheelInput)processor).dispatch(); assertEquals(100,game.volumeHud().percent(Channel.MASTER));
                if (!action.equals("dispose")) {
                    game.resume(); game.resume();
                    assertEquals(2,((SongSelectWheelInput)processor).getProcessors().size);
                    assertTrue(wheel(1)); assertEquals(95,game.volumeHud().percent(Channel.MASTER));
                }
            } finally { screen.dispose(); }
        }
    }

}
