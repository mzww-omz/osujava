package dev.osujava.ui;

import dev.osujava.audio.AudioVolumes;
import dev.osujava.audio.AudioVolumes.Channel;

/** Pure overlay state; advances from UI frame delta, independently of Gameplay's GameClock. */
public final class VolumeHud {
    public static final float HOLD_SECONDS = 1.5f;
    public static final float FADE_IN_SECONDS = .12f;
    public static final float FADE_OUT_SECONDS = .22f;
    public static final float STEP = .05f;
    private static final Channel[] CHANNELS = Channel.values();
    private final AudioVolumes volumes;
    private final float[] displayed = new float[CHANNELS.length];
    private Channel selected = Channel.MASTER;
    private float remaining, alpha, emphasis;

    public VolumeHud(AudioVolumes volumes) {
        this.volumes = volumes;
        for (Channel channel : CHANNELS) displayed[channel.ordinal()] = volumes.get(channel);
    }
    public void open() {
        selected = Channel.MASTER;
        for (Channel channel : CHANNELS) displayed[channel.ordinal()] = volumes.get(channel);
        refresh();
    }
    public void close() { remaining = 0; }
    public boolean active() { return remaining > 0; }
    public float alpha() { return alpha; }
    public float scale() { return .94f + .06f * alpha; }
    public Channel selected() { return selected; }
    public float emphasis() { return emphasis; }
    public float displayed(Channel channel) { return displayed[channel.ordinal()]; }
    public int percent(Channel channel) { return Math.round(volumes.get(channel) * 100); }
    public void select(int direction) {
        if (!active()) open();
        selected = CHANNELS[Math.floorMod(selected.ordinal() + direction, CHANNELS.length)];
        refresh();
    }
    public void adjust(float steps) {
        if (!Float.isFinite(steps) || steps == 0) return;
        if (!active()) open();
        // Round to hundredths to avoid accumulated float error near 0% and 100%.
        volumes.set(selected, Math.round((volumes.get(selected) + steps * STEP) * 100) / 100f);
        refresh();
    }
    private void refresh() { remaining = HOLD_SECONDS; emphasis = 1; }
    public void advance(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0) return;
        float hold = Math.min(remaining, seconds);
        remaining = Math.max(0, remaining - seconds);
        alpha = Math.min(1, alpha + hold / FADE_IN_SECONDS);
        alpha = Math.max(0, alpha - (seconds - hold) / FADE_OUT_SECONDS);
        emphasis = Math.max(0, emphasis - seconds / .25f);
        float follow = 1 - (float) Math.exp(-seconds / .09f);
        for (Channel channel : CHANNELS) {
            int index = channel.ordinal();
            displayed[index] += (volumes.get(channel) - displayed[index]) * follow;
            if (Math.abs(displayed[index] - volumes.get(channel)) < .0001f) displayed[index] = volumes.get(channel);
        }
    }
}
