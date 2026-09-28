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
    private boolean shift;

    @BeforeEach void setup() {
        oldInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor;
            case "isKeyPressed" -> shift && ((int)a[0] == Input.Keys.SHIFT_LEFT || (int)a[0] == Input.Keys.SHIFT_RIGHT);
            default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
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

    @Test void typingStartsSearchWithoutTextboxFocusAndEnterReturnsToNavigation() throws Exception {
        open("Alpha",1);
        assertFalse((boolean)field("searchActive"));
        assertTrue(processor.keyTyped('B')); assertTrue(processor.keyTyped('e'));
        assertEquals("Be",field("search")); assertTrue((boolean)field("searchActive"));
        selected(1,0,"Beta-easy.png");
        key(Input.Keys.ENTER);
        assertFalse((boolean)field("searchActive"));
        assertFalse(((UiNavigation)field("outgoing")).pending());
        key(Input.Keys.ENTER);
        assertTrue(((UiNavigation)field("outgoing")).pending());
    }

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

    @Test void modsOverlayConsumesNavigationSearchImportPlayAndDebugUntilClosed() throws Exception {
        open("Beta",1); key(Input.Keys.F1);
        var toolbox = (SongSelectToolboxState) field("toolbox");
        assertEquals(SongSelectToolboxState.Overlay.MODS,toolbox.overlay());
        for (int k : new int[]{Input.Keys.F2,Input.Keys.F3,Input.Keys.I,Input.Keys.ENTER,Input.Keys.F6,Input.Keys.DOWN}) key(k);
        assertTrue(processor.keyTyped('A')); assertTrue(processor.scrolled(0,1));
        assertEquals("",field("search")); assertEquals(0,importRequests);
        selected(1,1,"Beta-hard.png"); assertFalse(((UiNavigation)field("outgoing")).pending());
        assertTrue(toolbox.active().isEmpty());
        for (var mod : SongSelectToolboxState.Mod.values()) { assertFalse(mod.available()); assertFalse(toolbox.toggle(mod)); }
        key(Input.Keys.NUM_1); assertTrue(toolbox.active().isEmpty());
        key(Input.Keys.ESCAPE); assertFalse(toolbox.open());
        screen.browserMode(SongBrowserModel.Sort.BPM,SongBrowserModel.Group.ARTIST);
        key(Input.Keys.F1); key(Input.Keys.F1); assertFalse(toolbox.open());
        key(Input.Keys.RIGHT); selected(1,1,"Beta-hard.png");
    }

    @Test void optionsShortcutReportsUnavailableWithoutOpeningFakeMenuOrGameplay() throws Exception {
        open("Beta",1); key(Input.Keys.F3);
        assertTrue(((String)field("toast")).contains("unavailable"));
        assertFalse(((SongSelectToolboxState)field("toolbox")).open());
        assertFalse(((UiNavigation)field("outgoing")).pending()); selected(1,1,"Beta-hard.png");
    }

    @Test void modeViewOnlyReportsActuallySupportedRulesetAndEscClosesIt() throws Exception {
        open("Beta",1);
        var perform = SongSelectScreen.class.getDeclaredMethod("perform",SongSelectAction.class); perform.setAccessible(true);
        perform.invoke(screen,SongSelectAction.MODE);
        assertEquals(SongSelectToolboxState.Overlay.MODE,((SongSelectToolboxState)field("toolbox")).overlay());
        for (int k : new int[]{Input.Keys.F2,Input.Keys.ENTER,Input.Keys.I}) key(k);
        selected(1,1,"Beta-hard.png"); assertEquals(0,importRequests);
        key(Input.Keys.ESCAPE); assertFalse(((SongSelectToolboxState)field("toolbox")).open());
    }

    @Test void enterSpaceDebugAutoAndBackStillRequestTheirTransition() throws Exception {
        for (int key : new int[]{Input.Keys.ENTER,Input.Keys.SPACE,Input.Keys.F6,Input.Keys.ESCAPE}) {
            open("Beta",1); key(key);
            assertTrue(((UiNavigation)field("outgoing")).pending());
            selected(1,1,"Beta-hard.png"); screen.dispose();
        }
    }
    private SongSelectCarousel carousel() throws Exception { return (SongSelectCarousel) field("carousel"); }
    private void settle() throws Exception {
        for (int i=0;i<120;i++) carousel().advance(1f/60,null);
        screen.resize(1280,720);
    }
    private void click(int set, int difficulty) throws Exception {
        var row = carousel().rows().stream().filter(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == difficulty).findFirst().orElseThrow();
        var method = SongSelectScreen.class.getDeclaredMethod("handleRowClick",float.class,float.class); method.setAccessible(true);
        method.invoke(screen,carousel().renderX(row,1280) + 150,carousel().renderY(row,658) + carousel().rowHeight()/2);
    }

    @Test void selectionMovesViewportTargetWhileLogicalContentRemainsStable() throws Exception {
        open("Beta",0); screen.resize(1280,720);
        var rows = carousel().rows(); float offset = carousel().scrollOffset();
        key(Input.Keys.DOWN);
        assertSame(rows,carousel().rows()); assertEquals(offset,carousel().scrollOffset());
        assertTrue(carousel().scrollTarget() > offset); settle();
        var selected = carousel().rows().stream().filter(r -> r.entry.setIndex() == 1 && r.entry.difficultyIndex() == 1).findFirst().orElseThrow();
        assertEquals(371,carousel().renderY(selected,658) + carousel().rowHeight()/2,.01);
    }

    @Test void expansionIncludesEveryDifficultyInLibraryOrderAndCollapsesPreviousSet() throws Exception {
        var diffs = java.util.stream.IntStream.range(0,16).mapToObj(i -> new BeatmapDifficulty("Many","Artist","Creator","Diff " + i,0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,null)).toList();
        library.add(new BeatmapSet("Many","Many","Artist","Creator",null,null,diffs,List.of()));
        open("Many",8); screen.resize(1280,720);
        assertEquals(16,carousel().rows().stream().filter(r -> r.entry.setIndex() == 3).count());
        assertEquals(java.util.stream.IntStream.range(0,16).boxed().toList(),carousel().rows().stream().filter(r -> r.entry.setIndex() == 3).map(r -> r.entry.difficultyIndex()).toList());
        key(Input.Keys.PAGE_UP);
        assertEquals(List.of(-1),carousel().rows().stream().filter(r -> r.entry.setIndex() == 3).map(r -> r.entry.difficultyIndex()).toList());
        assertEquals(2,carousel().rows().stream().filter(r -> r.entry.setIndex() == 2).count());
    }

    @Test void unselectedDifficultyClickSelectsAndSecondClickRequestsPlay() throws Exception {
        open("Beta",0); screen.resize(1280,720);
        click(1,1); selected(1,1,"Beta-hard.png");
        assertFalse(((UiNavigation)field("outgoing")).pending());
        settle(); click(1,1);
        assertTrue(((UiNavigation)field("outgoing")).pending());
    }

    @Test void rowUnderLogoSelectsSetWithoutStartingPlay() throws Exception {
        var diff = library.all().iterator().next().difficulties().get(0);
        for (String title : List.of("Delta", "Epsilon"))
            library.add(new BeatmapSet(title,title,"Artist","Creator",null,null,List.of(diff),List.of()));
        open("Beta",0); screen.resize(1280,720);
        var row = carousel().rows().stream().filter(r -> r.entry.setIndex() == 3).findFirst().orElseThrow();
        float x = 1245, y = carousel().renderY(row,658) + carousel().rowHeight()/2;
        assertTrue(y > 84 && y < 140, "Row overlaps the logo artwork");
        assertFalse(((OsuCookie)field("playCookie")).hit(x,y));
        var method = SongSelectScreen.class.getDeclaredMethod("handleRowClick",float.class,float.class);
        method.setAccessible(true); method.invoke(screen,x,y);
        selected(3,0,"Alpha-easy.png");
        assertFalse(((UiNavigation)field("outgoing")).pending());
    }

    @Test void setDoubleClickCannotPlayTheDifficultyReplacingItsRow() throws Exception {
        open("Beta",0); screen.resize(1280,720);
        click(2,-1); screen.resize(1280,720); click(2,0);
        selected(2,0,"Gamma-easy.png");
        assertFalse(((UiNavigation)field("outgoing")).pending());
        assertTrue((float)field("setClickGuard") > 0);
        setField("setClickGuard",0f); settle(); click(2,0);
        assertTrue(((UiNavigation)field("outgoing")).pending());
    }

    @Test void randomExpandsTargetAndSmoothlyMakesSelectedDifficultyVisible() throws Exception {
        open("Beta",1); screen.resize(1280,720);
        for (int i=0;i<10;i++) {
            key(Input.Keys.F2);
            assertEquals(2,carousel().rows().stream().filter(r -> r.entry.setIndex() == (int)uncheckedField("selectedSetIndex")).count());
            settle();
            var row = carousel().rows().stream().filter(r -> r.entry.setIndex() == (int)uncheckedField("selectedSetIndex") && r.entry.difficultyIndex() == 0).findFirst().orElseThrow();
            assertEquals(371,carousel().renderY(row,658) + carousel().rowHeight()/2,.01);
        }
    }
    private Object uncheckedField(String name) {
        try { return field(name); } catch (Exception e) { throw new AssertionError(e); }
    }

    @Test void searchRemovesSelectionAndShrinksOrEmptiesContentWithValidViewport() throws Exception {
        open("Gamma",1); screen.resize(1280,720);
        setField("searchActive",true);
        for (char c : "Alpha".toCharArray()) processor.keyTyped(c);
        selected(0,0,"Alpha-easy.png"); assertEquals(2,carousel().rows().size());
        assertTrue(carousel().scrollOffset() <= carousel().maxScroll()); settle();
        assertEquals(0,carousel().scrollOffset(),.01);
        processor.keyTyped('x'); assertTrue(carousel().rows().isEmpty());
        assertEquals(0,carousel().scrollOffset()); assertEquals(0,carousel().scrollTarget());
        key(Input.Keys.BACKSPACE); settle(); assertEquals(2,carousel().rows().size());
        assertTrue(carousel().scrollOffset() <= carousel().maxScroll());
    }

    private Object rowValue(Object row, String name) throws Exception {
        var method = row.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(row);
    }

    @Test void animatedDrawBoundsAreClickableAndSelectedRowWinsDuringExpansionOverlap() throws Exception {
        open("Beta",0); screen.resize(1280,720); settle();
        var hit = SongSelectScreen.class.getDeclaredMethod("hitRow",float.class,float.class); hit.setAccessible(true);
        key(Input.Keys.PAGE_DOWN); // Expansion inherits the collapsed Set bounds, overlapping children.
        for (int frame = 0; frame < 40; frame++) {
            if (frame < 8) carousel().scrollBy(frame < 4 ? 80 : -80);
            carousel().advance(1f/60,"Gamma#0");
            // resize refreshes the production draw snapshot without advancing motion.
            screen.resize(1280,720);
            for (Object snapshot : (List<?>) field("visibleRows")) {
                int set = (int)rowValue(snapshot,"setIndex"), diff = (int)rowValue(snapshot,"difficultyIndex");
                var modelRow = carousel().rows().stream().filter(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == diff).findFirst().orElseThrow();
                assertEquals(carousel().renderX(modelRow,1280),(float)rowValue(snapshot,"x"),.001);
                assertEquals(carousel().renderY(modelRow,658),(float)rowValue(snapshot,"y"),.001);
                if ((boolean)rowValue(snapshot,"selected")) {
                    float x = (float)rowValue(snapshot,"x") + 150;
                    float y = (float)rowValue(snapshot,"y") + (float)rowValue(snapshot,"height") / 2;
                    if (y > 84 && y < 658) assertSame(snapshot,hit.invoke(screen,x,y),"Selected row must win over overlapping animated siblings");
                }
            }
        }
        assertFalse(((UiNavigation)field("outgoing")).pending());
    }

    @Test void shiftF2WalksBackDuringSearchAndGrouping() throws Exception {
        open("Alpha",1);
        key(Input.Keys.F2); screen.browserMode(SongBrowserModel.Sort.ARTIST,SongBrowserModel.Group.ARTIST);
        shift = true; key(Input.Keys.F2); selected(0,1,"Alpha-hard.png");
        shift = false;
        for (char c : "Artist".toCharArray()) processor.keyTyped(c);
        int before = (int)field("selectedSetIndex"); key(Input.Keys.F2); assertNotEquals(before,field("selectedSetIndex"));
        shift = true; key(Input.Keys.F2); assertEquals(before,field("selectedSetIndex"));
        assertTrue((boolean)field("searchActive"));
    }
    @Test void unicodeBackspaceRemovesWholeCodePointAndSearchClearRestoresSelection() throws Exception {
        open("Gamma",1);
        for(char c : "Alpha".toCharArray()) processor.keyTyped(c);
        selected(0,0,"Alpha-easy.png");
        for(int i=0;i<5;i++)key(Input.Keys.BACKSPACE);
        selected(2,1,"Gamma-hard.png");
        for(char c : "𠮷".toCharArray())processor.keyTyped(c);
        key(Input.Keys.BACKSPACE); assertEquals("",field("search"));
    }
    @Test void controlsPreserveDifficultyAndHeadersAreNeverClickable() throws Exception {
        open("Beta",1); screen.resize(1280,720);
        screen.browserMode(SongBrowserModel.Sort.BPM,SongBrowserModel.Group.CREATOR);
        selected(1,1,"Beta-hard.png"); settle(); screen.resize(1280,720);
        var hit = SongSelectScreen.class.getDeclaredMethod("rowHit",Class.forName("dev.osujava.ui.SongSelectScreen$Row"),float.class,float.class); hit.setAccessible(true);
        for(Object row : (List<?>)field("visibleRows")) if ((int)rowValue(row,"setIndex") < 0)
            assertFalse((boolean)hit.invoke(screen,row,(float)rowValue(row,"x")+20,(float)rowValue(row,"y")+40));
        key(Input.Keys.ENTER); assertTrue(((UiNavigation)field("outgoing")).pending());
        selected(1,1,"Beta-hard.png");
    }
    @Test void zeroResultsHaveNoCookieOrMetadataPlayTarget() throws Exception {
        open("Beta",1);
        for(char c : "missing".toCharArray())processor.keyTyped(c);
        var selected = SongSelectScreen.class.getDeclaredMethod("selectedDifficulty"); selected.setAccessible(true);
        assertNull(selected.invoke(screen)); key(Input.Keys.ESCAPE); key(Input.Keys.ENTER);
        assertFalse(((UiNavigation)field("outgoing")).pending());
    }

    @Test void modeChangeDrawSnapshotMatchesCarouselImmediately() throws Exception {
        var alpha = library.all().getFirst();
        library.add(new BeatmapSet(alpha.id(),alpha.title(),"Zulu",alpha.creator(),alpha.audioPath(),alpha.backgroundPath(),alpha.difficulties(),alpha.assets()));
        open("Beta",1); screen.resize(1280,720); settle();
        screen.browserMode(SongBrowserModel.Sort.BPM,SongBrowserModel.Group.ARTIST);
        var motion = carousel();
        for(Object snapshot : (List<?>)field("visibleRows")) {
            int set = (int)rowValue(snapshot,"setIndex"), diff = (int)rowValue(snapshot,"difficultyIndex");
            float x = (float)rowValue(snapshot,"x"), y = (float)rowValue(snapshot,"y");
            assertTrue(motion.rows().stream().anyMatch(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == diff
                    && Math.abs(motion.renderX(r,1280)-x) < .001f && Math.abs(motion.renderY(r,658)-y) < .001f));
        }
    }

}
