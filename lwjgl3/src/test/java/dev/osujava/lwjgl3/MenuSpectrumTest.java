package dev.osujava.lwjgl3;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSpectrumTest {
    @Test void silenceDcAndBinCentredSineHaveKnownHannSpectra() {
        var fft = new MenuSpectrum(); float[] pcm = new float[2048];
        assertArrayEquals(new float[1024], fft.transform(pcm));
        java.util.Arrays.fill(pcm, .25f);
        assertEquals(.25f, fft.transform(pcm)[0], .00001);
        for (int i = 0; i < pcm.length; i++) pcm[i] = (float) (.7 * Math.sin(2 * Math.PI * 37 * i / pcm.length));
        float[] bins = fft.transform(pcm);
        assertEquals(.7, bins[37], .00001); assertEquals(.35, bins[36], .00001);
        assertEquals(.35, bins[38], .00001);
        for (int i = 0; i < bins.length; i++) if (i < 36 || i > 38) assertEquals(0, bins[i], .00001);
    }
}
