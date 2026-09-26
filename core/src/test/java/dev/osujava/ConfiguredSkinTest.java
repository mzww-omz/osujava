package dev.osujava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredSkinTest {
    @TempDir Path temp;

    @Test
    void importsArchiveAtStartupAndUsesLocalSkinDirectory() throws Exception {
        Path archive = temp.resolve("skin.osk");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("hitcircle.png"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
        }
        withProperties(archive.toString(), temp.resolve("fallback").toString(), () -> {
            Path directory = new OsuJavaGame(null).skinDirectory();
            assertEquals(temp.resolve(".osujava/skins"), directory.getParent());
            assertTrue(Files.isRegularFile(directory.resolve("hitcircle.png")));
        });
    }

    @Test
    void damagedSkinDoesNotAbortStartupAndFallsBackToDirectoryOrVectors() throws Exception {
        Path archive = temp.resolve("broken.osk");
        Files.writeString(archive, "not a ZIP");
        Path fallback = temp.resolve("fallback");
        withProperties(archive.toString(), fallback.toString(),
                () -> assertEquals(fallback, new OsuJavaGame(null).skinDirectory()));
        withProperties(archive.toString(), null,
                () -> assertNull(new OsuJavaGame(null).skinDirectory()));
        try (var paths = Files.list(temp.resolve(".osujava/skins"))) {
            assertEquals(0, paths.count());
        }
    }

    private void withProperties(String archive, String directory, Runnable test) {
        String[] keys = {"user.home", "osujava.skinArchive", "osujava.skinDirectory"};
        Map<String, String> previous = new HashMap<>();
        for (String key : keys) previous.put(key, System.getProperty(key));
        try {
            System.setProperty("user.home", temp.toString());
            set("osujava.skinArchive", archive);
            set("osujava.skinDirectory", directory);
            test.run();
        } finally {
            for (String key : keys) set(key, previous.get(key));
        }
    }

    private void set(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
