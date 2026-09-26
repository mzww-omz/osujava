package dev.osujava.gameplay;

import java.util.List;

/** One-shot local sample request emitted by gameplay, separate from rendering. */
public record GameplayAudioCue(long timeMs, List<String> sampleNames, float volume) {
    public GameplayAudioCue {
        sampleNames = List.copyOf(sampleNames);
        volume = Math.max(0, Math.min(1, volume));
    }
}
