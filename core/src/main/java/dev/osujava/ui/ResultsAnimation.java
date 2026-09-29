package dev.osujava.ui;

import java.util.random.RandomGenerator;

/** Ranking presentation clock only; never changes the frozen score or gameplay clock. */
public final class ResultsAnimation {
    private double timeMs, baseMs = 300;
    private boolean skipped;
    public void advance(float seconds) {
        if (Float.isFinite(seconds)) timeMs += Math.max(0, seconds) * 1000;
    }
    /** Stable 06001a36: the first early activation only completes the presentation. */
    public boolean skip() {
        if (!skipped && timeMs - baseMs < 5000) {
            baseMs = timeMs - 7000; skipped = true; return false;
        }
        return true;
    }
    public float item(int index, boolean number) {
        float t = progress(300 + index * 300 + (number ? 200 : 0), 300);
        // 06002b54: easing 1 = -t*(t-2), easing 2 = t*t.
        return number ? -t * (t - 2) : t * t;
    }
    public float grade(boolean perfect) {
        float t = progress(perfect ? 3000 : 2700, 1000); return t * t;
    }
    public float gradeGlow(boolean perfect) {
        double start = perfect ? 4000 : 3700;
        if (timeMs - baseMs < start) return 0;
        float t = progress(start, 2400); return (1 - t) * (1 - t);
    }
    public float graph() { return progress(0, 4000); }
    private float progress(double start, double duration) {
        return (float) Math.max(0, Math.min(1, (timeMs - baseMs - start) / duration));
    }
    public String score(String finalText, RandomGenerator random) {
        int fixed = Math.max(0, (int) ((timeMs - baseMs) / 500));
        if (fixed >= finalText.length()) return finalText;
        StringBuilder value = new StringBuilder(finalText);
        for (int i = fixed; i < value.length(); i++) value.setCharAt(i, (char) ('0' + random.nextInt(9)));
        return value.toString();
    }
}
