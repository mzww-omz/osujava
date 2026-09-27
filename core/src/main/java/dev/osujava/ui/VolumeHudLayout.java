package dev.osujava.ui;

import dev.osujava.audio.AudioVolumes.Channel;

/** Coordinates in the shared UI's 720-unit baseline. */
public record VolumeHudLayout(float width, float height) {
    public float x(Channel channel) {
        return width - 112 - switch (channel) { case MASTER -> 0; case MUSIC -> 74; case EFFECT -> 126; };
    }
    public float y(Channel channel) {
        return height * .27f + switch (channel) { case MASTER -> 0; case MUSIC -> 134; case EFFECT -> 55; };
    }
    public float radius(Channel channel) { return channel == Channel.MASTER ? 72 : 31; }
}
