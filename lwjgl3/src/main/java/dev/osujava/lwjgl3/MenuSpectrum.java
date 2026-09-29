package dev.osujava.lwjgl3;

/** 2048-sample mono Hann FFT, matching the documented BASS_DATA_FFT2048 input convention. */
final class MenuSpectrum {
    static final int SIZE = 2048;
    private final double[] real = new double[SIZE], imaginary = new double[SIZE];
    float[] transform(float[] samples) {
        java.util.Arrays.fill(imaginary, 0);
        for (int i = 0; i < SIZE; i++) real[i] = samples[i] * (.5 - .5 * Math.cos(2 * Math.PI * i / SIZE));
        for (int i = 1, j = 0; i < SIZE; i++) {
            int bit = SIZE >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double value = real[i]; real[i] = real[j]; real[j] = value; }
        }
        for (int size = 2; size <= SIZE; size <<= 1) {
            double stepR = Math.cos(-2 * Math.PI / size), stepI = Math.sin(-2 * Math.PI / size);
            for (int base = 0; base < SIZE; base += size) {
                double wr = 1, wi = 0;
                for (int j = 0; j < size / 2; j++) {
                    int a = base + j, b = a + size / 2;
                    double r = real[b] * wr - imaginary[b] * wi, im = real[b] * wi + imaginary[b] * wr;
                    real[b] = real[a] - r; imaginary[b] = imaginary[a] - im;
                    real[a] += r; imaginary[a] += im;
                    double next = wr * stepR - wi * stepI;
                    wi = wr * stepI + wi * stepR; wr = next;
                }
            }
        }
        float[] bins = new float[SIZE / 2];
        for (int i = 0; i < bins.length; i++) bins[i] = (float) (Math.hypot(real[i], imaginary[i]) * (i == 0 ? 2 : 4) / SIZE);
        return bins;
    }
}
