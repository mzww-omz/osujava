package dev.osujava.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SafeArchiveExtractorTest {
    @TempDir Path temp;

    @Test
    void boundsDeclaredAndActualExpandedBytes() throws Exception {
        Path archive = archive();
        IOException declared = assertThrows(IOException.class,
                () -> SafeArchiveExtractor.extract(archive, staging(), 2048, 10));
        assertTrue(declared.getMessage().contains("expands beyond"));
        // A lying central-directory size must not bypass the streaming byte limit.
        patchCentralField(archive, 24, 1);
        IOException actual = assertThrows(IOException.class,
                () -> SafeArchiveExtractor.extract(archive, staging(), 2048, 10));
        assertTrue(actual.getMessage().contains("expands beyond"));
    }

    @Test
    void rejectsCentralDirectoryCrcMismatch() throws Exception {
        Path archive = archive();
        patchCentralField(archive, 16, 0);
        assertThrows(IOException.class, () -> SafeArchiveExtractor.extract(archive, staging(), 16_384, 10));
    }

    @Test
    void rejectsTruncatedCompressedData() throws Exception {
        Path archive = archive();
        byte[] bytes = Files.readAllBytes(archive);
        int dataStart = 30 + "image.png".length();
        bytes[dataStart] ^= (byte) 0xff;
        Files.write(archive, bytes);
        assertThrows(IOException.class, () -> SafeArchiveExtractor.extract(archive, staging(), 16_384, 10));
    }

    private Path staging() throws IOException { return Files.createTempDirectory(temp, "staging-"); }

    private Path archive() throws IOException {
        Path archive = temp.resolve("test.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("image.png"));
            zip.write(new byte[8192]);
            zip.closeEntry();
        }
        return archive;
    }

    private void patchCentralField(Path archive, int offset, int value) throws IOException {
        byte[] bytes = Files.readAllBytes(archive);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < bytes.length - 46; i++) {
            if (buffer.getInt(i) == 0x02014b50) {
                buffer.putInt(i + offset, value);
                Files.write(archive, bytes);
                return;
            }
        }
        fail("Missing central directory fixture");
    }
}
