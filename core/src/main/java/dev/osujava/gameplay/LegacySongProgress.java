package dev.osujava.gameplay;

/** SongProgress uses playable object bounds, not the audio file duration. */
public record LegacySongProgress(double startTime, double firstHitTime, double lastHitTime) {
    public record Frame(double progress, boolean intro) { }
    /** SongProgress.PopIn: 500ms OutQuint, on the same clock as its progress. */
    public double alphaAt(double now) {
        double p = Math.max(0, Math.min(1, (now - startTime) / 500));
        return 1 - Math.pow(1 - p, 5);
    }
    public Frame at(double now) {
        boolean intro = now < firstHitTime;
        double duration = intro ? firstHitTime - startTime : lastHitTime - firstHitTime;
        double fraction = duration == 0 ? 0 : (now - (intro ? startTime : firstHitTime)) / duration;
        return new Frame(Math.max(0, Math.min(1, fraction)), intro);
    }
}
