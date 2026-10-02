package dev.osujava.library;

import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.support.MutableWallClock;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BeatmapAddedAtTest {
    @TempDir Path root;
    private final MutableWallClock clock=new MutableWallClock("2026-10-01T12:00:00Z",ZoneOffset.UTC);
    private BeatmapSet chart(String id, String filename, int x) throws Exception {
        var path=root.resolve(filename); Files.createDirectories(path.getParent());
        Files.writeString(path,"osu file format v14\n[Metadata]\nTitle:Local\nArtist:A\nCreator:C\nVersion:D\n[HitObjects]\n"+x+",100,1000,1,0\n");
        var diff=new BeatmapFileParser().parse(path).difficulty().withAssets(null,null,path);
        return new BeatmapSet(id,diff.title(),diff.artist(),diff.creator(),null,null,List.of(diff),List.of());
    }
    private BeatmapContentKey key(BeatmapSet set) { return BeatmapContentKey.of(set.difficulties().getFirst()); }
    @Test void importDateSurvivesRestartReimportAndIdenticalContentAtAnotherLocation() throws Exception {
        var library=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        var first=chart("a","maps/a.osu",100); library.add(first); long original=clock.millis();
        var index=root.resolve("index/a.properties"); byte[] before=Files.readAllBytes(index);
        clock.set("2026-10-02T12:00:00Z"); library=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        assertEquals(original,library.addedAt(key(first))); assertArrayEquals(before,Files.readAllBytes(index));
        library.add(first); var clone=chart("b","maps/b.osu",100); library.add(clone);
        assertEquals(original,library.addedAt(key(clone))); assertEquals(2,library.size());
        assertEquals(original,new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock).addedAt(key(first)));
        var edited=chart("a","maps/a.osu",101); library.add(edited);
        assertEquals(clock.millis(),library.addedAt(key(edited))); assertEquals(original,library.addedAt(key(clone)));
    }
    @Test void oldSchemaDatesStayUnknownOnReadAndReimportButNewContentGetsAnImportDate() throws Exception {
        for(int schema:List.of(1,2)) {
            var first=chart("a","maps/a.osu",100); var storage=new PropertiesBeatmapLibraryStorage(root); storage.save(first);
            var index=root.resolve("index/a.properties"); var p=new Properties();
            try(var in=Files.newInputStream(index)) { p.load(in); }
            p.setProperty("schemaVersion",Integer.toString(schema));
            try(var out=Files.newOutputStream(index)) { p.store(out,"legacy fixture"); }
            byte[] before=Files.readAllBytes(index); var library=new BeatmapLibrary(storage,clock);
            assertNull(library.addedAt(key(first))); assertArrayEquals(before,Files.readAllBytes(index));
            library.add(first); assertNull(library.addedAt(key(first)));
            var changed=chart("a","maps/a.osu",101); library.add(changed); assertEquals(clock.millis(),library.addedAt(key(changed)));
        }
    }
    @Test void externalContentChangesAndDamagedDateCannotInheritAnOldDate() throws Exception {
        var library=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        var first=chart("a","maps/a.osu",100); library.add(first);
        var edited=chart("a","maps/a.osu",101);
        var reopened=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        assertEquals(1,reopened.size()); assertNull(reopened.addedAt(key(edited)));
        var index=root.resolve("index/a.properties"); Files.writeString(index,Files.readString(index).replace("addedAt=", "badAddedAt="));
        first=chart("a","maps/a.osu",100); reopened=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        assertEquals(1,reopened.size()); assertNull(reopened.addedAt(key(first)));
    }
    @Test void failedIndexSavePublishesNeitherDateNorRevision() throws Exception {
        boolean[] fail={true};
        var library=new BeatmapLibrary(new BeatmapLibraryStorage() {
            public List<BeatmapSet> load() { return List.of(); }
            public void save(BeatmapSet set) throws java.io.IOException { if(fail[0]) throw new java.io.IOException("full"); }
        },clock);
        var set=chart("a","map.osu",100);
        assertThrows(LibraryStorageException.class,() -> library.add(set)); assertNull(library.addedAt(key(set))); assertEquals(0,library.revision());
        clock.set("2026-10-02T12:00:00Z"); fail[0]=false; library.add(set); assertEquals(clock.millis(),library.addedAt(key(set)));
    }
    @Test void futureIndexIsPreservedEvenWhenSameSetIsReimported() throws Exception {
        var library=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        var set=chart("a","maps/a.osu",100); library.add(set);
        var index=root.resolve("index/a.properties"); Files.writeString(index,Files.readString(index).replace("schemaVersion=3","schemaVersion=4"));
        byte[] bytes=Files.readAllBytes(index); var reopened=new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(root),clock);
        assertEquals(0,reopened.size()); assertArrayEquals(bytes,Files.readAllBytes(index));
        assertThrows(LibraryStorageException.class,() -> reopened.add(set));
        assertNull(reopened.addedAt(key(set))); assertEquals(0,reopened.revision()); assertArrayEquals(bytes,Files.readAllBytes(index));
    }
}
