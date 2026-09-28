package dev.osujava.skin;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectImageLimitsTest {
    @TempDir Path directory;
    private FileHandle header(int width, int height) throws Exception {
        var path = directory.resolve("image.png");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeLong(0x89504e470d0a1a0aL); out.writeInt(13); out.writeInt(0x49484452);
            out.writeInt(width); out.writeInt(height);
        }
        return new FileHandle(path.toFile());
    }
    @ParameterizedTest @CsvSource({"1,1", "690,85", "1380,170", "8192,1", "1,8192", "4096,4096"})
    void safeDimensionsPassPreflightWithoutImageAllocation(int width, int height) throws Exception {
        assertDoesNotThrow(() -> SongSelectImageLimits.check(header(width, height)));
        // The actual decoder still validates pixels, CRC and truncation after this allocation guard.
    }
    @ParameterizedTest @CsvSource({"0,1", "1,0", "-1,10", "8193,1", "1,8193", "8192,8192", "2147483647,2147483647"})
    void unsafeDimensionsAreRejectedBeforeNativeDecode(int width, int height) throws Exception {
        var file = header(width, height);
        assertThrows(GdxRuntimeException.class, () -> SongSelectImageLimits.check(file));
    }
    @Test void invalidOrTruncatedPngIsRejected() throws Exception {
        for (byte[] data : new byte[][]{new byte[0], "not a png".getBytes(), new byte[24]}) {
            var path = directory.resolve("broken.png"); Files.write(path, data);
            assertThrows(GdxRuntimeException.class, () -> SongSelectImageLimits.check(new FileHandle(path.toFile())));
        }
    }
}
