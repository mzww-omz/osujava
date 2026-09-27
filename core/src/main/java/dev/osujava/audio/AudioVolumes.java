package dev.osujava.audio;

import com.badlogic.gdx.audio.Music;
import java.util.IdentityHashMap;
import java.util.Map;

/** Application-owned gains. Streams remain owned and disposed by their screens. */
public final class AudioVolumes {
    public enum Channel { MASTER, MUSIC, EFFECT }
    private final float[] values = {1, 1, 1};
    private final Map<Music, Float> musicStreams = new IdentityHashMap<>();

    public float get(Channel channel) { return values[channel.ordinal()]; }
    public void set(Channel channel, float value) {
        values[channel.ordinal()] = clamp(value);
        if (channel != Channel.EFFECT) {
            var streams = musicStreams.entrySet().iterator();
            while (streams.hasNext()) {
                var stream = streams.next();
                try { apply(stream.getKey(), stream.getValue()); }
                catch (RuntimeException ignored) {
                    // A lost audio device must not make a global UI input crash the game.
                    // The screen still owns this stream and its disposal/fallback.
                    streams.remove();
                }
            }
        }
    }
    public float musicOutput() { return get(Channel.MASTER) * get(Channel.MUSIC); }
    public float effectOutput() { return get(Channel.MASTER) * get(Channel.EFFECT); }
    public static float clamp(float value) { return Float.isNaN(value) ? 0 : Math.max(0, Math.min(1, value)); }

    /** Also used for the menu's existing intrinsic gain and navigation fade. */
    public void musicGain(Music music, float gain) {
        musicStreams.put(music, clamp(gain));
        apply(music, clamp(gain));
    }
    public void removeMusic(Music music) { musicStreams.remove(music); }
    private void apply(Music music, float gain) { music.setVolume(musicOutput() * gain); }
}
