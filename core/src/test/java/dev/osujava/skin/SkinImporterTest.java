package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkinImporterTest {
    @TempDir Path temp;

    @Test
    void importsSkinAndConnectsToExistingAssetResolver() throws Exception {
        Path archive = zip("skin.OSK", "hitcircle.png", "hitcircle@2x.png", "hitcircleoverlay.png",
                "approachcircle.png", "skin.ini", "extras/", "extras/背景.txt");
        Path directory = importer().importFile(archive);
        assertEquals(temp.resolve("skins"), directory.getParent());
        assertTrue(directory.getFileName().toString().matches("[a-f0-9]{64}"));
        assertEquals("extras/背景.txt", Files.readString(directory.resolve("extras/背景.txt")));
        assertTrue(Files.exists(directory.resolve("skin.ini")));
        SkinAssetResolver resolver = new SkinAssetResolver(directory);
        assertEquals(directory.resolve("hitcircle@2x.png"), resolver.resolve("hitcircle").orElseThrow().path());
        assertEquals(2, resolver.resolve("hitcircle").orElseThrow().density());
        assertEquals(1, resolver.resolve("hitcircleoverlay").orElseThrow().density());
        assertEquals(directory.resolve("approachcircle.png"), resolver.resolve("approachcircle").orElseThrow().path());
        assertOnlySkin(directory);
    }

    @Test
    void contentIdentityReusesRenamedArchivesAndSeparatesSameNameWithDifferentContent() throws Exception {
        Path archive = zip("same.osk", "hitcircle.png");
        Path first = importer().importFile(archive);
        Path renamed = Files.copy(archive, temp.resolve("renamed.osk"));
        assertEquals(first, importer().importFile(renamed));
        zip("same.osk", "approachcircle.png");
        Path second = importer().importFile(archive);
        assertNotEquals(first, second);
        assertTrue(Files.exists(first.resolve("hitcircle.png")));
        try (var children = Files.list(temp.resolve("skins"))) {
            assertEquals(2, children.count());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.txt", "nested/../../outside.txt", "/absolute/path",
            "C:/outside.txt", "C:outside.txt", "C:\\outside.txt", "..\\outside.txt",
            "nested\\..\\outside.txt", "//server/share", "nested/../outside.txt", "./"})
    void rejectsUnsafePathsWithoutWritingOutsideOrKeepingPartialImports(String name) throws Exception {
        Path archive = zip("unsafe.osk", "hitcircle.png", name);
        assertThrows(SkinImportException.class, () -> importer().importFile(archive));
        assertFalse(Files.exists(temp.resolve("outside.txt")));
        assertEmptyStorage();
        try (var paths = Files.walk(temp)) {
            assertEquals(List.of("unsafe.osk"), paths.filter(Files::isRegularFile)
                    .map(path -> temp.relativize(path).toString()).toList());
        }
    }

    @Test
    void rejectsAbsolutePathToARealExternalDestination() throws Exception {
        Path outside = temp.resolve("outside.txt");
        Path archive = zip("absolute.osk", outside.toString());
        assertThrows(SkinImportException.class, () -> importer().importFile(archive));
        assertFalse(Files.exists(outside));
        assertEmptyStorage();
    }

    @Test
    void rejectsDuplicateAndNormalizedDuplicateEntries() throws Exception {
        Path alias = zip("alias.osk", "hitcircle.png", "./hitcircle.png");
        assertThrows(SkinImportException.class, () -> importer().importFile(alias));
        Path exact = zip("duplicate.osk", "one.png", "two.png");
        byte[] bytes = Files.readAllBytes(exact);
        byte[] from = "two.png".getBytes(StandardCharsets.UTF_8);
        byte[] to = "one.png".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - from.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(bytes, i, i + from.length), from)) {
                System.arraycopy(to, 0, bytes, i, to.length);
            }
        }
        Files.write(exact, bytes);
        SkinImportException error = assertThrows(SkinImportException.class, () -> importer().importFile(exact));
        assertTrue(error.getMessage().contains("Duplicate archive path"));
        assertEmptyStorage();
    }

    @Test
    void rejectsNonZipTruncatedZipAndEmptyArchive() throws Exception {
        Path broken = temp.resolve("broken.osk");
        Files.writeString(broken, "not a ZIP");
        assertThrows(SkinImportException.class, () -> importer().importFile(broken));
        Path truncated = zip("truncated.osk", "hitcircle.png");
        byte[] bytes = Files.readAllBytes(truncated);
        Files.write(truncated, Arrays.copyOf(bytes, bytes.length - 22));
        assertThrows(SkinImportException.class, () -> importer().importFile(truncated));
        Path empty = zip("empty.osk", "folder/");
        assertThrows(SkinImportException.class, () -> importer().importFile(empty));
        assertEmptyStorage();
    }

    @Test
    void rejectsOversizedArchiveAndTooManyEntries() throws Exception {
        Path huge = temp.resolve("huge.osk");
        try (RandomAccessFile file = new RandomAccessFile(huge.toFile(), "rw")) {
            file.setLength(256L * 1024 * 1024 + 1);
        }
        SkinImportException sizeError = assertThrows(SkinImportException.class, () -> importer().importFile(huge));
        assertTrue(sizeError.getMessage().contains("byte import limit"));
        String[] names = new String[10_001];
        for (int i = 0; i < names.length; i++) names[i] = "file-" + i;
        Path many = zip("many.osk", names);
        SkinImportException countError = assertThrows(SkinImportException.class, () -> importer().importFile(many));
        assertTrue(countError.getMessage().contains("entry import limit"));
        assertEmptyStorage();
    }

    @Test
    void cleansUpFilesAfterStreamingValidationFailure() throws Exception {
        Path archive = zip("crc.osk", "hitcircle.png", "approachcircle.png");
        byte[] bytes = Files.readAllBytes(archive);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        boolean patched = false;
        for (int i = 0; i < bytes.length - 46; i++) {
            if (buffer.getInt(i) == 0x02014b50) {
                buffer.putInt(i + 16, 0);
                patched = true;
                break;
            }
        }
        assertTrue(patched);
        Files.write(archive, bytes);
        SkinImportException error = assertThrows(SkinImportException.class, () -> importer().importFile(archive));
        assertTrue(error.getMessage().contains("ZIP headers disagree"));
        assertEmptyStorage();
    }

    @Test
    void rejectsMissingAndWrongExtension() throws Exception {
        assertThrows(SkinImportException.class, () -> importer().importFile(temp.resolve("missing.osk")));
        Path wrong = zip("wrong.zip", "hitcircle.png");
        assertThrows(SkinImportException.class, () -> importer().importFile(wrong));
    }

    private SkinImporter importer() { return new SkinImporter(temp.resolve("skins")); }

    private Path zip(String filename, String... names) throws Exception {
        Path archive = temp.resolve(filename);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private void assertEmptyStorage() throws Exception {
        try (var children = Files.list(temp.resolve("skins"))) { assertEquals(0, children.count()); }
    }

    private void assertOnlySkin(Path directory) throws Exception {
        try (var children = Files.list(temp.resolve("skins"))) { assertEquals(List.of(directory), children.toList()); }
    }
}
