package dev.osujava.collection;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.score.DifficultyIdentity;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalCollectionStoreTest {
    @TempDir Path root;
    private LocalCollectionStore.Member member(String hash,String path) {
        return new LocalCollectionStore.Member(new BeatmapContentKey(hash.repeat(64),0),new DifficultyIdentity("set",path));
    }
    @Test void crudKeepsUnicodeUuidContentMembershipAndBytesAcrossRestart() throws Exception {
        var file=root.resolve("collections.properties"); var store=new LocalCollectionStore(file);
        var a=store.create("夜の星 🌟"); var b=store.create("夜の星 🌟"); assertNotEquals(a.id(),b.id());
        var m=member("a","a.osu"); assertTrue(store.add(a.id(),List.of(m,m))); assertTrue(store.add(b.id(),List.of(m)));
        assertTrue(store.rename(a.id(),"Renamed")); assertEquals(List.of(m),store.find(a.id()).members());
        byte[] bytes=Files.readAllBytes(file); var reopened=new LocalCollectionStore(file);
        assertEquals(store.all(),reopened.all()); assertArrayEquals(bytes,Files.readAllBytes(file));
        assertTrue(reopened.remove(a.id(),Set.of(m.content()))); assertTrue(reopened.find(a.id()).members().isEmpty());
        assertEquals(List.of(m),reopened.find(b.id()).members()); assertTrue(reopened.delete(a.id()));
        assertNull(new LocalCollectionStore(file).find(a.id()));
    }
    @Test void wholeSetAdditionIsAtomicAndDuplicateContentKeepsOriginalLocator() {
        var store=new LocalCollectionStore(); var c=store.create("Set"); var a=member("a","a.osu"); var b=member("b","b.osu");
        long revision=store.revision(); assertTrue(store.add(c.id(),List.of(a,b))); assertEquals(revision+1,store.revision());
        var cached=store.all(); assertTrue(store.add(c.id(),List.of(member("a","moved.osu")))); assertSame(cached,store.all());
        assertEquals(List.of(a,b),store.find(c.id()).members()); assertEquals(revision+1,store.revision());
        assertTrue(store.remove(c.id(),Set.of(a.content(),b.content()))); assertEquals(revision+2,store.revision());
    }
    @Test void invalidNamesDoNotWriteOrPublishAndValidNamesNormalize() throws Exception {
        var file=root.resolve("collections.properties"); var store=new LocalCollectionStore(file);
        for(var name:List.of("", " ", "a\nb", "a".repeat(81),"\ud800")) assertNull(store.create(name));
        assertEquals(0,store.revision()); assertFalse(Files.exists(file));
        var c=store.create("  e\u0301  "); assertEquals("é",c.name());
        var cached=store.all(); assertFalse(store.rename(c.id(),"\t")); assertSame(cached,store.all());
    }
    @Test void unreadableFutureAndMalformedFilesArePreservedAndBlockMutations() throws Exception {
        for(var text:List.of("schemaVersion=2\nfuture=true\n", "schemaVersion=1\ncollection.count=10001\n", "schemaVersion=1\ncollection.count=1\n")) {
            var file=root.resolve("collections.properties"); Files.writeString(file,text); byte[] bytes=Files.readAllBytes(file);
            var store=new LocalCollectionStore(file); assertEquals(LocalCollectionStore.Status.UNAVAILABLE,store.status());
            assertNull(store.create("New")); assertEquals(0,store.revision()); assertArrayEquals(bytes,Files.readAllBytes(file));
        }
    }
    @Test void externalEditsAndMissingFileNeverSilentlyReplaceCachedData() throws Exception {
        var file=root.resolve("collections.properties"); var store=new LocalCollectionStore(file); var c=store.create("Original");
        var cached=store.all(); Files.writeString(file,"schemaVersion=99\nexternal=true\n"); byte[] bytes=Files.readAllBytes(file);
        assertFalse(store.delete(c.id())); assertSame(cached,store.all()); assertEquals(1,store.revision()); assertArrayEquals(bytes,Files.readAllBytes(file));
        Files.delete(file); assertFalse(store.rename(c.id(),"New")); assertFalse(Files.exists(file)); assertSame(cached,store.all());
    }
    @Test void failedSaveDoesNotPublishAndCanRetryAfterFilesystemRecovery() throws Exception {
        var parent=root.resolve("blocked"); var store=new LocalCollectionStore(parent.resolve("collections.properties")); Files.writeString(parent,"file");
        assertNull(store.create("New")); assertEquals(0,store.revision()); assertTrue(store.all().isEmpty());
        Files.delete(parent); assertNotNull(store.create("New")); assertEquals(1,store.revision()); assertEquals(LocalCollectionStore.Status.READY,store.status());
    }
}
