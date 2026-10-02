package dev.osujava.score;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalPlayerProfileTest {
    @TempDir Path root;
    @Test void profileIsOptInAndRenameKeepsIdentityWithoutChangingOldNameSnapshot() throws Exception {
        Path file = root.resolve("data/player.properties");
        assertNull(LocalPlayerProfile.load(file,null)); assertFalse(Files.exists(file));
        var original = LocalPlayerProfile.load(file,"夜空"); assertNotNull(original);
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(original,LocalPlayerProfile.load(file,null)); assertArrayEquals(bytes,Files.readAllBytes(file));
        var renamed = LocalPlayerProfile.load(file,"星空"); assertEquals(original.id(),renamed.id());
        assertEquals("夜空",original.name()); assertEquals("星空",LocalPlayerProfile.load(file,null).name());
    }
    @Test void damagedOrFutureProfileIsNotOverwrittenWithARequestedName() throws Exception {
        Path file = root.resolve("player.properties"); Files.writeString(file,"schemaVersion=2\nplayerId=future\n");
        byte[] bytes = Files.readAllBytes(file);
        assertNull(LocalPlayerProfile.load(file,"Local")); assertArrayEquals(bytes,Files.readAllBytes(file));
        Files.writeString(file,"schemaVersion=1\nplayerId=broken\nplayerName=Local"); bytes = Files.readAllBytes(file);
        assertNull(LocalPlayerProfile.load(file,"New")); assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test void invalidNameAndStorageFailureLeaveExistingProfileUnchanged() throws Exception {
        var file = root.resolve("player.properties"); var original = LocalPlayerProfile.load(file,"Local");
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(original,LocalPlayerProfile.load(file,"bad\nname")); assertArrayEquals(bytes,Files.readAllBytes(file));
        var blocked = root.resolve("blocked"); Files.writeString(blocked,"not a directory");
        assertNull(LocalPlayerProfile.load(blocked.resolve("player.properties"),"Local"));
        assertEquals("not a directory",Files.readString(blocked));
    }
}
