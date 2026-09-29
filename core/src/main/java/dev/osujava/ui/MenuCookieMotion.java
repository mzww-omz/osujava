package dev.osujava.ui;

import java.util.ArrayList;
import java.util.List;

/** Independent reconstruction of stable b20230727.9's cookie update, not a gameplay clock. */
final class MenuCookieMotion {
    record Ripple(double bornMs, float scale, float alpha) { }
    private final List<Ripple> ripples = new ArrayList<>();
    private double timeMs;
    private long lastBeat = Long.MIN_VALUE;
    private double lastOrigin = Double.NaN;
    private float pulse, averageLevel, hover, intensity = .5f, beatIntensity = .5f;
    private boolean hovered, kiai;

    void pointer(boolean over) { hovered = over; }
    void click() { hover -= .08f; }
    void resetTrack() {
        lastBeat = Long.MIN_VALUE; lastOrigin = Double.NaN;
        pulse = averageLevel = 0; intensity = beatIntensity = .5f; ripples.clear();
    }
    void advance(double ms, MenuBeatTiming.Beat beat, MenuAudioAnalysis analysis) {
        double frames = Math.max(0, ms) * .06;
        timeMs += Math.max(0, ms);
        hover = hovered || hover < 0 ? Math.min(.1f, hover + (float) (frames * .012))
                : Math.max(0, hover - (float) (frames * .012));
        float level = analysis.available() ? MainMenuMotion.normalized(analysis.maximumAmplitude()) : .5f;
        if (ms > 0) averageLevel = .9f * averageLevel + .1f * level;
        kiai = beat.kiai();
        float strength = kiai ? 1 : .6f + .4f * MainMenuMotion.clamp((level * 65536 - 30000) / 35536);
        if (ms > 0) intensity = .8f * intensity + .2f * strength;
        long index = beat.index();
        if (index != lastBeat || beat.originMs() != lastOrigin) {
            if (lastBeat != Long.MIN_VALUE && index > lastBeat && beat.originMs() == lastOrigin)
                ripples.add(new Ripple(timeMs, scale(), .1f * intensity));
            lastBeat = index; lastOrigin = beat.originMs(); beatIntensity = intensity;
        }
        float target = MainMenuMotion.clamp(.5 * (1 - beat.phase()) + level - averageLevel);
        pulse = blend(pulse, target, .5, frames);
        ripples.removeIf(r -> timeMs - r.bornMs >= 1000);
    }
    private static float blend(float current, float target, double retention, double frames) {
        return target + (current - target) * (float) Math.pow(retention, frames);
    }
    private static float out(float from, float to, float t) { return from + (to - from) * (1 - (1 - t) * (1 - t)); }
    float scale() { return out(1.05f + hover, 1 + hover, pulse); }
    float echoScale() { return out(1.05f + hover, 1.08f + hover, pulse); }
    float echoAlpha() { return out(kiai ? .1f : .4f, 0, pulse) * beatIntensity; }
    boolean echoAdditive() { return kiai; }
    float spectrumScale() { return 1.05f + hover + .03f * (1 - pulse) * (1 - pulse); }
    float spectrumAlpha(float reveal) { return (1 - reveal * .7f) * (kiai ? 1 : .7f); }
    List<Ripple> ripples() { return List.copyOf(ripples); }
    float rippleScale(Ripple r) { return r.scale * (1 + .4f * (1 - (float) Math.pow(1 - rippleAge(r), 2))); }
    float rippleAlpha(Ripple r) { return r.alpha * (1 - rippleAge(r)); }
    private float rippleAge(Ripple r) { return MainMenuMotion.clamp((timeMs - r.bornMs) / 1000); }
}
