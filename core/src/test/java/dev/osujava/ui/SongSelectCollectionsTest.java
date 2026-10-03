package dev.osujava.ui;

import com.badlogic.gdx.Input;
import dev.osujava.beatmap.*;
import dev.osujava.collection.LocalCollectionStore;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.ui.theme.UiLayout;
import static dev.osujava.support.CollectionTestMaps.set;
import static dev.osujava.ui.SongSelectCollections.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectCollectionsTest {
    @Test void optionsShowsNativeCommandSlotsWhileOnlyLocalSupportedActionsCanRun() throws Exception {
        var set=set("Map",1);library.add(set);manager.options(set,set.difficulties().getFirst());
        var before=manager.snapshot();assertEquals(6,buttons(before).size());
        var initialLayout=UiLayout.fromPixels(1280,720);
        var invisible=manager.interaction(initialLayout,Button.MANAGE);
        manager.click(initialLayout,invisible.x()+invisible.width()/2,invisible.y()+invisible.height()/2);
        assertEquals(before,manager.snapshot(),"Invisible entrance button must not capture a command");
        assertTrue(enabled(before,Button.MANAGE));assertTrue(enabled(before,Button.CLOSE));
        for(var button:List.of(Button.DELETE_BEATMAP,Button.MARK_PLAYED,Button.CLEAR_SCORES,Button.EDIT_BEATMAP)) {
            assertFalse(enabled(before,button));manager.action(button);assertEquals(before,manager.snapshot());
        }
        for(int key: new int[]{Input.Keys.NUM_2,Input.Keys.NUM_3,Input.Keys.NUM_4,Input.Keys.NUM_5}) manager.key(key,false);
        assertEquals(before,manager.snapshot());assertEquals(1,library.size());
        var layout=UiLayout.fromPixels(1024,768);manager.animation.advance(2);
        var close=manager.interaction(layout,Button.CLOSE);
        assertEquals(new SongSelectSelectorLayout(layout).option(5),close);
        manager.click(layout,close.x()+close.width()/2,close.y()+close.height()/2);
        assertFalse(manager.open());assertEquals(Mode.OPTIONS,manager.presentation().mode());
        manager.animation.advance(.12f);assertEquals(Mode.CLOSED,manager.presentation().mode());
        manager.options(set,set.difficulties().getFirst());manager.key(Input.Keys.NUM_1,false);
        assertEquals(Mode.MANAGE,manager.snapshot().mode());
    }
    private final LocalCollectionStore store=new LocalCollectionStore();
    private final BeatmapLibrary library=new BeatmapLibrary();
    private final SongSelectCollections manager=new SongSelectCollections(store,library);
    private void type(String s) { for(char ch:s.toCharArray()) manager.typed(ch); }
    private void click(UiLayout layout,Button b) { var box=bounds(layout,b); manager.click(layout,box.x()+1,box.y()+1); }
    @Test void realDialogCommandsCreateRenameRegisterAndRemoveDifficultyAndSet() throws Exception {
        var set=set("Map",3); library.add(set); manager.options(set,set.difficulties().getFirst()); manager.key(Input.Keys.NUM_1,false);
        var layout=UiLayout.fromPixels(1024,768); click(layout,Button.CREATE); type("夜の星 🌟"); click(layout,Button.SAVE);
        var id=manager.snapshot().selected(); assertEquals("夜の星 🌟",store.find(id).name());
        click(layout,Button.DIFFICULTY); assertTrue(manager.snapshot().difficultyIncluded()); assertFalse(manager.snapshot().setIncluded());
        click(layout,Button.SET); assertEquals(3,store.find(id).members().size()); assertTrue(manager.snapshot().setIncluded());
        click(layout,Button.DIFFICULTY); assertEquals(2,store.find(id).members().size());
        click(layout,Button.SET); assertEquals(3,store.find(id).members().size()); click(layout,Button.SET); assertTrue(store.find(id).members().isEmpty());
        click(layout,Button.RENAME); manager.key(Input.Keys.A,true); type("Renamed"); manager.key(Input.Keys.ENTER,false); assertEquals("Renamed",store.find(id).name());
        click(layout,Button.DELETE); assertNotNull(store.find(id)); click(layout,Button.CANCEL); assertNotNull(store.find(id));
        click(layout,Button.DELETE); click(layout,Button.CONFIRM); assertNull(store.find(id)); assertEquals(1,library.size());
    }
    @Test void emptySyntheticAndInvalidNamesDoNotInventMembersAndPagingUsesSharedHitBounds() throws Exception {
        manager.options(null,null); manager.action(Button.MANAGE); assertFalse(manager.snapshot().hasDifficulty());
        manager.action(Button.CREATE); manager.action(Button.SAVE); assertTrue(store.all().isEmpty()); assertFalse(manager.snapshot().error().isEmpty());
        manager.key(Input.Keys.ESCAPE,false); assertEquals(Edit.NONE,manager.snapshot().edit());
        for(int i=0;i<20;i++) store.create("Collection "+String.format("%02d",i)); manager.refresh();
        manager.scroll(1); assertEquals(1,manager.snapshot().page()); var l=UiLayout.fromPixels(1280,800); var b=rowBounds(l,2);
        manager.click(l,b.x()+1,b.y()+1); assertEquals(store.all().get(10).id(),manager.snapshot().selected());
        var cached=manager.snapshot(); manager.refresh(); assertSame(cached,manager.snapshot());
    }
    @Test void missingMembershipIsRetainedUntilExplicitRemovalAndLibraryChangeClosesFrozenTarget() throws Exception {
        var original=set("Map",1); library.add(original); manager.options(original,original.difficulties().getFirst());
        manager.action(Button.MANAGE); manager.action(Button.CREATE); type("C"); manager.action(Button.SAVE); manager.action(Button.SET);
        var id=manager.snapshot().selected(); var edited=set("Changed",1); library.add(new BeatmapSet("Map",edited.title(),edited.artist(),edited.creator(),null,null,edited.difficulties(),List.of()));
        manager.refresh(); assertFalse(manager.open()); assertEquals(1,store.find(id).members().size());
        manager.options(library.all().getFirst(),edited.difficulties().getFirst()); manager.action(Button.MANAGE);
        assertEquals(1,manager.snapshot().missing()); manager.action(Button.MISSING); assertTrue(store.find(id).members().isEmpty());
    }
    @Test void keyboardEditorHandlesSurrogatesBackspaceAndShortcutCharacterSuppression() {
        manager.options(null,null); manager.key(Input.Keys.NUM_1,false); manager.key(Input.Keys.N,false); manager.typed('n'); type("🌟é");
        assertEquals("🌟é",manager.snapshot().draft()); manager.key(Input.Keys.BACKSPACE,false); assertEquals("🌟",manager.snapshot().draft());
        manager.key(Input.Keys.ENTER,false); assertEquals("🌟",store.all().getFirst().name());
        manager.key(Input.Keys.R,false); manager.typed('r'); manager.key(Input.Keys.A,true); manager.typed('\udc00'); assertEquals("",manager.snapshot().draft());
        manager.key(Input.Keys.ESCAPE,false); assertTrue(manager.open()); manager.key(Input.Keys.ESCAPE,false); assertFalse(manager.open());
    }
    @Test void failedPersistenceShowsErrorAndDoesNotClaimMembership(@TempDir Path root) throws Exception {
        var file=root.resolve("collections.properties"); var disk=new LocalCollectionStore(file); var c=disk.create("C");
        var manager=new SongSelectCollections(disk,library); var set=set("Map",1); library.add(set); manager.options(set,set.difficulties().getFirst()); manager.action(Button.MANAGE);
        Files.writeString(file,"schemaVersion=2\nfuture=true\n"); byte[] bytes=Files.readAllBytes(file);
        manager.action(Button.DIFFICULTY); assertFalse(manager.snapshot().difficultyIncluded()); assertTrue(disk.find(c.id()).members().isEmpty());
        assertFalse(manager.snapshot().error().isEmpty()); assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test void resizeKeepsDraftAndUsesNewBoundsForSaveAndActions() {
        manager.options(null,null); manager.action(Button.MANAGE);
        var before=UiLayout.fromPixels(1280,720); click(before,Button.CREATE); type("Draft 夜");
        var after=UiLayout.fromPixels(1024,768); assertEquals("Draft 夜",manager.snapshot().draft()); click(after,Button.SAVE);
        assertEquals("Draft 夜",store.all().getFirst().name()); assertEquals(Edit.NONE,manager.snapshot().edit());
        for(int[] size:new int[][]{{1280,720},{1280,800},{1024,768},{1920,1080}}) {
            var l=UiLayout.fromPixels(size[0],size[1]); var p=panel(l); var buttons=buttons(manager.snapshot());
            for(int i=0;i<buttons.size();i++) {
                var a=bounds(l,buttons.get(i)); assertTrue(a.x()>=p.x() && a.x()+a.width()<=p.x()+p.width());
                assertTrue(a.y()>=p.y() && a.y()+a.height()<=p.y()+p.height());
                for(int j=i+1;j<buttons.size();j++) {
                    var b=bounds(l,buttons.get(j)); assertFalse(a.x()<b.x()+b.width() && a.x()+a.width()>b.x() && a.y()<b.y()+b.height() && a.y()+a.height()>b.y(),"Dialog action hitboxes must not overlap");
                }
            }
        }
    }
}
