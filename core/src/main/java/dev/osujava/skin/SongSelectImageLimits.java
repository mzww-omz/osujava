package dev.osujava.skin;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.DataInputStream;
import java.io.IOException;

/** Song Select PNG allocation guard. Runs before native decode and GPU allocation. */
final class SongSelectImageLimits {
    static final int MAX_DIMENSION = 8192;
    static final long MAX_PIXELS = 16L * 1024 * 1024;
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    static void check(FileHandle file) {
        if (file.length() > MAX_FILE_BYTES) throw new GdxRuntimeException("Skin PNG exceeds file budget");
        try (var input = new DataInputStream(file.read())) {
            if (input.readLong() != 0x89504e470d0a1a0aL || input.readInt() != 13 || input.readInt() != 0x49484452)
                throw new IOException("Invalid PNG header");
            int width = input.readInt(), height = input.readInt();
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) throw new IOException("Skin PNG exceeds pixel budget");
        } catch (IOException e) { throw new GdxRuntimeException("Unsafe or malformed skin PNG: " + file.name(), e); }
    }
}
