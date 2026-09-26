package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;
import dev.osujava.gameplay.GameplayAudioCue;
import dev.osujava.gameplay.JudgementWindows;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsuHitsoundSamplesTest {
    @Test
    void resolvesHitFlagsAndTimingPointBankIndexVolume() {
        TimingPoint timing = new TimingPoint(0, 500, 4, 2, 3, 60, true, 0);
        assertEquals(List.of("soft-hitnormal3", "soft-hitwhistle3", "soft-hitclap3"),
                OsuHitsoundSamples.hit(2 | 8, timing));
        assertEquals(List.of("soft-slidertick3"), OsuHitsoundSamples.sliderTick(timing));
        assertEquals(0.6f, OsuHitsoundSamples.volume(timing), 1e-6);
    }

    @Test
    void gameplayEmitsOneShotCuesThroughSessionInput() {
        HitObject circle = new HitObject(256, 192, 1000, HitObject.Type.CIRCLE, 1, 4);
        BeatmapDifficulty difficulty = new BeatmapDifficulty("Song", "Artist", "Creator", "Normal", 0,
                "", "", DifficultySettings.defaults(),
                List.of(new TimingPoint(0, 500, 4, 3, 0, 80, true, 0)), List.of(circle), null, null);
        OsuGameplaySession session = new OsuGameplaySession(difficulty, () -> 1000,
                JudgementWindows.fromOverallDifficulty(5));
        session.click(256, 192);
        List<GameplayAudioCue> cues = session.drainAudioCues();
        assertEquals(1, cues.size());
        assertEquals(List.of("drum-hitnormal", "drum-hitfinish"), cues.getFirst().sampleNames());
        assertEquals(0.8f, cues.getFirst().volume(), 1e-6);
        assertTrue(session.drainAudioCues().isEmpty());
    }
}
