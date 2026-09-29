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
    private boolean shift, control, alt, pointerPressed, pointerClicked;
    private int pointerX, pointerY;

    @BeforeEach void setup() {
        oldInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor;
            case "getX" -> pointerX;
            case "getY" -> pointerY;
            case "isButtonPressed" -> pointerPressed;
            case "isButtonJustPressed" -> pointerClicked;
            case "isKeyPressed" -> control && (int)a[0] == Input.Keys.CONTROL_LEFT || alt && (int)a[0] == Input.Keys.ALT_LEFT || shift && ((int)a[0] == Input.Keys.SHIFT_LEFT || (int)a[0] == Input.Keys.SHIFT_RIGHT);
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

    @Test void supplementarySearchCommitsOnlyCompleteCodePointsAndHonoursLimit() throws Exception {
        open("Alpha", 0);
        char[] pair = "𠮷".toCharArray();
        processor.keyTyped(pair[0]); assertEquals("", field("search"));
        processor.keyTyped(pair[1]); assertEquals("𠮷", field("search"));
        key(Input.Keys.BACKSPACE); assertEquals("", field("search"));
        processor.keyTyped(pair[1]); assertEquals("", field("search"));
        setField("search", "a".repeat(79));
        processor.keyTyped(pair[0]); processor.keyTyped(pair[1]);
        assertEquals("a".repeat(79) + "𠮷", field("search"));
        processor.keyTyped('b'); assertEquals("a".repeat(79) + "𠮷", field("search"));
    }

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
        key(Input.Keys.UP); selected(1,0,"Beta-easy.png");
        var browser = (SongBrowserModel) field("browser");
        assertEquals("Alpha", browser.row(browser.focusKey()).set.id());
        key(Input.Keys.DOWN); selected(1,0,"Beta-easy.png"); assertNull(browser.focusKey());
        key(Input.Keys.DOWN); selected(1,1,"Beta-hard.png");
        key(Input.Keys.UP); selected(1,0,"Beta-easy.png");
        key(Input.Keys.RIGHT); selected(2,0,"Gamma-easy.png");
        key(Input.Keys.LEFT); selected(1,0,"Beta-easy.png");
    }

    @Test void enterConfirmsFocusedSetBeforeItCanStartGameplay() throws Exception {
        open("Beta", 1); screen.resize(1280,720);
        key(Input.Keys.DOWN);
        var browser = (SongBrowserModel) field("browser");
        assertEquals("Gamma", browser.row(browser.focusKey()).set.id());
        selected(1,1,"Beta-hard.png");
        key(Input.Keys.ENTER);
        selected(2,0,"Gamma-easy.png"); assertNull(browser.focusKey());
        assertFalse(((UiNavigation) field("outgoing")).pending());
        key(Input.Keys.ENTER);
        assertTrue(((UiNavigation) field("outgoing")).pending());
    }
    @Test void horizontalArrowConfirmsFocusAndMetadataStaysOnSelectionUntilThen() throws Exception {
        open("Beta", 0); screen.resize(1280,720);
        key(Input.Keys.UP); selected(1,0,"Beta-easy.png");
        var browser = (SongBrowserModel) field("browser");
        assertEquals("Alpha", browser.row(browser.focusKey()).set.id());
        key(Input.Keys.RIGHT);
        selected(0,0,"Alpha-easy.png"); assertNull(browser.focusKey());
        assertFalse(((UiNavigation) field("outgoing")).pending());
    }
    @Test void shiftArrowsAndEnterOperateGroupsWhileControlShiftEnterKeepsConfirmRouting() throws Exception {
        open("Beta", 1); screen.resize(1280,720);
        screen.browserMode(SongBrowserModel.Sort.TITLE, SongBrowserModel.Group.CREATOR);
        var browser = (SongBrowserModel) field("browser");
        var selection = browser.selection();
        shift = true; key(Input.Keys.ENTER);
        assertFalse(browser.row(browser.groupTargetKey()).expanded);
        key(Input.Keys.LEFT); assertTrue(browser.row(browser.groupTargetKey()).expanded);
        key(Input.Keys.RIGHT); assertFalse(browser.row(browser.groupTargetKey()).expanded);
        key(Input.Keys.ENTER); assertTrue(browser.row(browser.groupTargetKey()).expanded);
        assertEquals(selection, browser.selection());
        assertFalse(((UiNavigation) field("outgoing")).pending());
        control = true; key(Input.Keys.ENTER);
        assertTrue(((UiNavigation) field("outgoing")).pending());
    }
    @Test void filteringAndOverlaysCannotAccidentallyConfirmAStaleFocus() throws Exception {
        open("Beta", 1); key(Input.Keys.DOWN);
        var browser = (SongBrowserModel) field("browser"); var focus = browser.focusKey();
        key(Input.Keys.F1); key(Input.Keys.ENTER); key(Input.Keys.DOWN);
        assertEquals(focus, browser.focusKey()); assertFalse(((UiNavigation) field("outgoing")).pending());
        key(Input.Keys.ESCAPE);
        processor.keyTyped('A');
        assertNull(browser.focusKey());
        key(Input.Keys.ENTER); // Search consumes this key.
        assertFalse(((UiNavigation) field("outgoing")).pending());
    }

    @Test void pendingGameplayAndImportPreventFocusConfirmationOrNavigation() throws Exception {
        open("Beta", 1); key(Input.Keys.DOWN);
        var browser = (SongBrowserModel) field("browser");
        var selection = browser.selection(); var focus = browser.focusKey();
        setField("importing", true);
        for (int k : new int[]{Input.Keys.ENTER, Input.Keys.UP, Input.Keys.RIGHT, Input.Keys.PAGE_DOWN}) key(k);
        assertEquals(selection, browser.selection()); assertEquals(focus, browser.focusKey());
        setField("importing", false);
        key(Input.Keys.SPACE); // Direct play action starts the current playable selection.
        assertTrue(((UiNavigation) field("outgoing")).pending());
        for (int k : new int[]{Input.Keys.ENTER, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.PAGE_UP}) key(k);
        shift = true; key(Input.Keys.ENTER); key(Input.Keys.LEFT);
        assertEquals(selection, browser.selection()); assertEquals(focus, browser.focusKey());
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

    @Test void importKeyTypedDoesNotFilterOrChangeSelection() throws Exception {
        open("Beta", 1);
        key(Input.Keys.I);
        assertTrue(processor.keyTyped('i'));
        processor.keyUp(Input.Keys.I);
        assertEquals(1, importRequests);
        assertEquals("", field("search"));
        assertEquals(false, field("searchActive"));
        selected(1, 1, "Beta-hard.png");
        // The same character remains available when explicitly editing a query.
        processor.keyTyped('B');
        assertFalse(processor.keyDown(Input.Keys.I));
        processor.keyTyped('i');
        assertEquals("Bi", field("search"));
    }

    @Test void printablePlayShortcutDoesNotStartSearch() throws Exception {
        open("Beta", 1);
        key(Input.Keys.SPACE);
        processor.keyTyped(' ');
        assertEquals("", field("search"));
        assertEquals(false, field("searchActive"));
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
        key(Input.Keys.F1); key(Input.Keys.NUM_2); assertFalse(toolbox.open());
        assertTrue(processor.keyTyped('2')); assertEquals("",field("search"));
        screen.browserMode(SongBrowserModel.Sort.BPM,SongBrowserModel.Group.ARTIST);
        key(Input.Keys.F1); key(Input.Keys.F1); assertFalse(toolbox.open());
        key(Input.Keys.UP); selected(1,0,"Beta-easy.png");
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
        method.invoke(screen,carousel().renderX(row,1280) + 150,carousel().renderY(row,636) + carousel().rowHeight()/2);
    }

    @Test void selectionMovesViewportTargetWhileLogicalContentRemainsStable() throws Exception {
        open("Beta",0); screen.resize(1280,720);
        var rows = carousel().rows(); float offset = carousel().scrollOffset();
        key(Input.Keys.DOWN);
        assertSame(rows,carousel().rows()); assertEquals(offset,carousel().scrollOffset());
        assertTrue(carousel().scrollTarget() > offset); settle();
        var selected = carousel().rows().stream().filter(r -> r.entry.setIndex() == 1 && r.entry.difficultyIndex() == 1).findFirst().orElseThrow();
        assertEquals(390,carousel().renderY(selected,636) + carousel().rowHeight()/2,.01);
    }

    @Test void expansionIncludesEveryDifficultyInLibraryOrderAndCollapsesPreviousSet() throws Exception {
        var diffs = java.util.stream.IntStream.range(0,16).mapToObj(i -> new BeatmapDifficulty("Many","Artist","Creator","Diff " + i,0,"","",DifficultySettings.defaults(),List.of(),List.of(),null,null)).toList();
        library.add(new BeatmapSet("Many","Many","Artist","Creator",null,null,diffs,List.of()));
        open("Many",8); screen.resize(1280,720);
        assertEquals(16,carousel().rows().stream().filter(r -> r.entry.setIndex() == 3).count());
        assertEquals(java.util.stream.IntStream.range(0,16).boxed().toList(),carousel().rows().stream().filter(r -> r.entry.setIndex() == 3).map(r -> r.entry.difficultyIndex()).toList());
        key(Input.Keys.LEFT);
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
        var row = carousel().rows().stream().filter(r -> r.entry.setIndex() == 4).findFirst().orElseThrow();
        float center = carousel().renderY(row,636) + carousel().rowHeight()/2;
        carousel().dragBy(115 - center); screen.resize(1280,720);
        float x = 1245, y = carousel().renderY(row,636) + carousel().rowHeight()/2;
        assertTrue(y > 84 && y < 140, "Row overlaps the logo artwork");
        assertFalse(((OsuCookie)field("playCookie")).hit(x,y));
        var method = SongSelectScreen.class.getDeclaredMethod("handleRowClick",float.class,float.class);
        method.setAccessible(true); method.invoke(screen,x,y);
        selected(4,0,"Gamma-easy.png");
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
            assertEquals((int)field("selectedSetIndex") == 0 ? 420 : 390,carousel().renderY(row,636) + carousel().rowHeight()/2,.01);
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
        key(Input.Keys.RIGHT); // Expansion inherits the collapsed Set bounds, overlapping children.
        for (int frame = 0; frame < 40; frame++) {
            if (frame < 8) carousel().scrollBy(frame < 4 ? 80 : -80);
            carousel().advance(1f/60,"Gamma#0");
            // resize refreshes the production draw snapshot without advancing motion.
            screen.resize(1280,720);
            for (Object snapshot : (List<?>) field("visibleRows")) {
                int set = (int)rowValue(snapshot,"setIndex"), diff = (int)rowValue(snapshot,"difficultyIndex");
                var modelRow = carousel().rows().stream().filter(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == diff).findFirst().orElseThrow();
                assertEquals(carousel().renderX(modelRow,1280),(float)rowValue(snapshot,"x"),.001);
                assertEquals(carousel().renderY(modelRow,636),(float)rowValue(snapshot,"y"),.001);
                if ((boolean)rowValue(snapshot,"selected")) {
                    float x = (float)rowValue(snapshot,"x") + 150;
                    float y = (float)rowValue(snapshot,"y") + (float)rowValue(snapshot,"height") / 2;
                    if (y > 84 && y < 636) assertSame(snapshot,hit.invoke(screen,x,y),"Selected row must win over overlapping animated siblings");
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
    @Test void controlsPreserveDifficultyAndGroupCardsAreClickable() throws Exception {
        open("Beta",1); screen.resize(1280,720);
        screen.browserMode(SongBrowserModel.Sort.BPM,SongBrowserModel.Group.CREATOR);
        selected(1,1,"Beta-hard.png"); settle(); screen.resize(1280,720);
        for (Object snapshot : (List<?>) field("visibleRows")) {
            var row = (SongSelectRow) snapshot;
            if (row.group()) assertTrue(row.contains(row.x() + 20, row.y() + 40));
        }
        key(Input.Keys.ENTER); assertTrue(((UiNavigation)field("outgoing")).pending());
        selected(1,1,"Beta-hard.png");
    }
    @Test void groupClicksToggleWithoutChangingThePlayableDifficulty() throws Exception {
        open("Beta", 1); screen.resize(1280,720);
        screen.browserMode(SongBrowserModel.Sort.TITLE, SongBrowserModel.Group.CREATOR);
        settle();
        var browser = (SongBrowserModel) field("browser");
        var selection = browser.selection();
        pointerX = 1100; pointerY = 400; screen.resize(1280,720);
        carousel().scrollBy(-carousel().maxScroll()); settle();
        click(-1, -2);
        assertEquals(selection, browser.selection());
        assertTrue(browser.entries().stream().allMatch(e -> e.kind() == SongBrowserModel.Kind.GROUP_HEADER));
        assertFalse(((UiNavigation) field("outgoing")).pending());
        settle(); click(-1, -2);
        assertEquals(selection, browser.selection());
        assertTrue(browser.entries().stream().anyMatch(e -> e.kind() == SongBrowserModel.Kind.DIFFICULTY));
        assertFalse(((UiNavigation) field("outgoing")).pending());
        key(Input.Keys.UP);
        assertNull(browser.groupTargetKey());
        assertEquals(SongBrowserModel.RowState.SELECTED, browser.row(browser.selectedKey()).state);
        assertTrue(carousel().rows().stream().anyMatch(r -> r.entry.key().equals(browser.selectedKey())));
        key(Input.Keys.ENTER);
        assertTrue(((UiNavigation) field("outgoing")).pending());
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
                    && Math.abs(motion.renderX(r,1280)-x) < .001f && Math.abs(motion.renderY(r,636)-y) < .001f));
        }
    }

    @Test void shortPageTraversalReturnsToOriginAndArrowKeysChooseSets() throws Exception {
        open("Beta", 1); screen.resize(1280, 720);
        var browser = (SongBrowserModel) field("browser");
        var selection = browser.selection();
        key(Input.Keys.PAGE_DOWN); assertEquals(selection, browser.selection());
        key(Input.Keys.PAGE_UP); assertEquals(selection, browser.selection());
        key(Input.Keys.RIGHT); selected(2, 0, "Gamma-easy.png");
        key(Input.Keys.LEFT); selected(1, 0, "Beta-easy.png");
    }

    @Test void productionPointerSelectsOnlyAfterReleaseOverThePressedRow() throws Exception {
        open("Beta", 0); screen.resize(1280, 720); settle();
        var row = ((List<?>) field("visibleRows")).stream().map(SongSelectRow.class::cast)
                .filter(r -> r.setIndex() == 2 && r.difficultyIndex() == -1).findFirst().orElseThrow();
        pointerX = Math.round(row.x() + 120); pointerY = 720 - Math.round(row.y() + row.height() / 2);
        var update = SongSelectScreen.class.getDeclaredMethod("update", dev.osujava.ui.theme.UiLayout.class, float.class);
        update.setAccessible(true);
        var layout = dev.osujava.ui.theme.UiLayout.fromPixels(1280, 720);
        pointerClicked = true; pointerPressed = true; update.invoke(screen, layout, 0f);
        selected(1, 0, "Beta-easy.png");
        pointerClicked = false; update.invoke(screen, layout, 0f);
        selected(1, 0, "Beta-easy.png");
        pointerPressed = false; update.invoke(screen, layout, 0f);
        selected(2, 0, "Gamma-easy.png");
        assertFalse(((UiNavigation) field("outgoing")).pending());
    }

    @Test void modifiedTextNeverEntersSearchAndOrdinaryUnicodeStillDoes() throws Exception {
        open("Beta", 0);
        control = true; processor.keyTyped('c'); control = false;
        alt = true; processor.keyTyped('i'); alt = false;
        assertEquals("", field("search"));
        processor.keyTyped('星'); assertEquals("星", field("search"));
    }
    @Test void unrelatedKeyDownDoesNotReleaseConsumedImportCharacter() throws Exception {
        open("Beta", 0);
        processor.keyDown(Input.Keys.I);
        processor.keyDown(Input.Keys.SHIFT_LEFT);
        processor.keyTyped('i');
        assertEquals("", field("search"));
        assertEquals(1, importRequests);
    }

    @Test void productionDragPublishesReleaseGeometryWithoutChangingSelection() throws Exception {
        open("Beta", 0); screen.resize(1280, 720); settle();
        var row = ((List<?>) field("visibleRows")).stream().map(SongSelectRow.class::cast)
                .filter(r -> r.selected()).findFirst().orElseThrow();
        pointerX = Math.round(row.x() + 120); pointerY = 720 - Math.round(row.y() + row.height() / 2);
        var update = SongSelectScreen.class.getDeclaredMethod("update", dev.osujava.ui.theme.UiLayout.class, float.class);
        update.setAccessible(true);
        var layout = dev.osujava.ui.theme.UiLayout.fromPixels(1280, 720);
        float before = carousel().scrollOffset();
        pointerClicked = true; pointerPressed = true; update.invoke(screen, layout, 0f);
        pointerClicked = false; pointerY -= 40; update.invoke(screen, layout, 0f);
        assertEquals(before + 40, carousel().scrollOffset(), .001);
        pointerPressed = false; pointerY -= 20; update.invoke(screen, layout, 0f);
        assertEquals(before + 60, carousel().scrollOffset(), .001);
        selected(1, 0, "Beta-easy.png");
        assertFalse(((UiNavigation) field("outgoing")).pending());
        for (var geometry : screen.rowGeometrySnapshot()) {
            var motion = carousel().allRows().get(geometry.logicalIndex());
            assertEquals(carousel().renderY(motion, 636), geometry.body().y(), .001);
        }
    }

    @Test void productionDragSamplesBeforeViewportIntegrationAndFlicksAfterRelease() throws Exception {
        open("Beta", 0); screen.resize(1280, 720); settle();
        var row = ((List<?>) field("visibleRows")).stream().map(SongSelectRow.class::cast).filter(SongSelectRow::selected).findFirst().orElseThrow();
        pointerX = Math.round(row.x() + 120); pointerY = 720 - Math.round(row.y() + row.height() / 2);
        var update = SongSelectScreen.class.getDeclaredMethod("update", dev.osujava.ui.theme.UiLayout.class, float.class);
        update.setAccessible(true);
        var layout = dev.osujava.ui.theme.UiLayout.fromPixels(1280, 720);
        float before = carousel().scrollOffset();
        pointerClicked = true; pointerPressed = true; update.invoke(screen, layout, .02f);
        pointerClicked = false; pointerY -= 30; update.invoke(screen, layout, .02f);
        assertEquals(before + 30, carousel().scrollOffset(), .001);
        assertEquals(.8784233454094307 * 1500, carousel().scrollVelocity(), .01);
        update.invoke(screen, layout, .1f);
        assertEquals(before + 30, carousel().scrollOffset(), .001);
        pointerPressed = false; update.invoke(screen, layout, 0f);
        assertEquals(.15357002292559258 * 1500, carousel().scrollVelocity(), .01);
        update.invoke(screen, layout, .02f);
        assertTrue(carousel().scrollOffset() > before + 30);
        selected(1, 0, "Beta-easy.png");
        assertFalse(((UiNavigation) field("outgoing")).pending());
        for (var geometry : screen.rowGeometrySnapshot()) {
            var motion = carousel().allRows().get(geometry.logicalIndex());
            assertEquals(carousel().renderY(motion, 636), geometry.body().y(), .001);
        }
    }

    @Test void productionGroupOpeningPublishesSeededChildrenBeforeTimeAdvances() throws Exception {
        open("Beta", 1); screen.resize(1280, 720);
        screen.browserMode(SongBrowserModel.Sort.TITLE, SongBrowserModel.Group.CREATOR);
        var browser = (SongBrowserModel) field("browser");
        var selection = browser.selection();
        shift = true; key(Input.Keys.ENTER); settle();
        var group = carousel().rows().stream().filter(r -> r.entry.header()).findFirst().orElseThrow();
        float y = group.motionY, x = group.motionX;
        key(Input.Keys.ENTER);
        assertEquals(selection, browser.selection());
        var alpha = carousel().rows().stream().filter(r -> r.entry.setIndex() == 0).findFirst().orElseThrow();
        var beta = carousel().rows().stream().filter(r -> r.entry.setIndex() == 1 && r.entry.difficultyIndex() == 0).findFirst().orElseThrow();
        assertEquals(y, alpha.motionY); assertEquals(x, alpha.motionX);
        assertEquals(y + 72, beta.motionY); assertEquals(x, beta.motionX);
        assertTrue(carousel().presents(alpha)); assertTrue(carousel().presents(beta));
        for (var snapshot : ((List<?>) field("visibleRows")).stream().map(SongSelectRow.class::cast).toList()) {
            var row = carousel().allRows().get(snapshot.logicalIndex());
            assertTrue(carousel().presents(row));
            assertEquals(carousel().renderY(row, 636), snapshot.y(), .001);
        }
        assertFalse(((UiNavigation) field("outgoing")).pending());
    }

}
