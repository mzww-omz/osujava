package dev.osujava.ui;

import com.badlogic.gdx.*;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.ruleset.osu.OsuRuleset;
import dev.osujava.ui.theme.UiNavigation;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectNavigationTest {
    private Input oldInput;
    private InputProcessor processor;
    private final BeatmapLibrary library = new BeatmapLibrary();
    private SongSelectScreen screen;
    private int importRequests;

    @BeforeEach void setup() {
        oldInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor;
            default -> m.getReturnType() == boolean.class ? false : null;
        });
        for (String title : List.of("Alpha", "Beta", "Gamma")) {
            var easy = new BeatmapDifficulty(title,"Artist","Creator","Easy",0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,Path.of(title + "-easy.png"));
            var hard = new BeatmapDifficulty(title,"Artist","Creator","Hard",0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,Path.of(title + "-hard.png"));
            library.add(new BeatmapSet(title,title,"Artist","Creator",null,easy.backgroundPath(),List.of(easy,hard),List.of()));
        }
    }
    @AfterEach void cleanup() { if (screen != null) screen.dispose(); Gdx.input = oldInput; }
    private void open(String set, int difficulty) {
        var game = new OsuJavaGame(onSelected -> importRequests++,null) {
            @Override public BeatmapLibrary library() { return library; }
            @Override public OsuRuleset osuRuleset() { return new OsuRuleset(); }
        };
        screen = new SongSelectScreen(game,set,difficulty); screen.show();
    }
    private Object field(String name) throws Exception {
        var field = SongSelectScreen.class.getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    private void setField(String name, Object value) throws Exception {
        var field = SongSelectScreen.class.getDeclaredField(name); field.setAccessible(true); field.set(screen,value);
    }
    private void selected(int set, int difficulty, String background) throws Exception {
        assertEquals(set, field("selectedSetIndex")); assertEquals(difficulty, field("selectedDifficultyIndex"));
        assertEquals(Path.of(background),field("backgroundPath"));
    }
    private void key(int key) { assertTrue(processor.keyDown(key)); }

    @Test void preferredDifficultyAndExistingNavigationKeepBackgroundInSync() throws Exception {
        open("Beta",1); selected(1,1,"Beta-hard.png");
        key(Input.Keys.UP); selected(1,0,"Beta-easy.png");
        key(Input.Keys.UP); selected(0,1,"Alpha-hard.png");
        key(Input.Keys.DOWN); selected(1,0,"Beta-easy.png");
        key(Input.Keys.RIGHT); selected(1,1,"Beta-hard.png");
        key(Input.Keys.LEFT); selected(1,0,"Beta-easy.png");
        key(Input.Keys.PAGE_DOWN); selected(2,0,"Gamma-easy.png");
        key(Input.Keys.PAGE_UP); selected(1,0,"Beta-easy.png");
    }

    @Test void randomActuallyChangesSetAndHonoursSearchIncludingNoMatches() throws Exception {
        open("Alpha",1);
        key(Input.Keys.F2);
        assertNotEquals(0,field("selectedSetIndex")); assertEquals(0,field("selectedDifficultyIndex"));
        setField("searchActive",true);
        for (char c : "Beta".toCharArray()) assertTrue(processor.keyTyped(c));
        selected(1,0,"Beta-easy.png");
        key(Input.Keys.ENTER); // Close search, preserving the original Enter behaviour.
        for (int i = 0; i < 20; i++) key(Input.Keys.F2);
        selected(1,0,"Beta-easy.png");
        setField("searchActive",true);
        assertTrue(processor.keyTyped('x')); key(Input.Keys.ESCAPE);
        key(Input.Keys.F2); selected(1,0,"Beta-easy.png");
        setField("searchActive",true); key(Input.Keys.BACKSPACE);
        assertEquals("Beta",field("search"));
        assertFalse(((UiNavigation)field("outgoing")).pending());
    }

    @Test void importShortcutStillOpensTheChooser() throws Exception {
        open("Beta",1); key(Input.Keys.I);
        assertEquals(1,importRequests);
        assertFalse(((UiNavigation)field("outgoing")).pending());
        selected(1,1,"Beta-hard.png");
    }

    @Test void enterSpaceDebugAutoAndBackStillRequestTheirTransition() throws Exception {
        for (int key : new int[]{Input.Keys.ENTER,Input.Keys.SPACE,Input.Keys.F6,Input.Keys.ESCAPE}) {
            open("Beta",1); key(key);
            assertTrue(((UiNavigation)field("outgoing")).pending());
            selected(1,1,"Beta-hard.png"); screen.dispose();
        }
    }
}
