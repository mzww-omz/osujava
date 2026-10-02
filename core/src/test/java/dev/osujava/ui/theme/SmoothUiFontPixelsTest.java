package dev.osujava.ui.theme;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmoothUiFontPixelsTest {
    @Test void bulkLabelPixelsMatchOriginalArgbConversionIncludingPartialAlpha() {
        var image = new BufferedImage(256, 2, BufferedImage.TYPE_INT_ARGB);
        for (int alpha = 0; alpha < 256; alpha++) {
            image.setRGB(alpha, 0, (alpha << 24) | 0xffffff);
            image.setRGB(alpha, 1, (alpha << 24) | 0x184fe2);
        }
        var pixels = ByteBuffer.allocateDirect(256 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        SmoothUiFont.copyLabelPixels(image, pixels);
        assertEquals(0, pixels.position(), "GL upload must start at the first byte");
        assertEquals(pixels.capacity(), pixels.limit());
        for (int y = 0; y < 2; y++) for (int x = 0; x < 256; x++) {
            int argb = image.getRGB(x, y);
            assertEquals((argb >> 16) & 255, pixels.get() & 255);
            assertEquals((argb >> 8) & 255, pixels.get() & 255);
            assertEquals(argb & 255, pixels.get() & 255);
            assertEquals((argb >>> 24) & 255, pixels.get() & 255);
        }
    }
}
