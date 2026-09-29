package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.gameplay.Judgement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsuScoreV1Test {
    @Test void comboBonusUsesComboBeforeHitMinusOneAndNeverWeightsAccuracy() {
        var score = new OsuScoreV1(4);
        score.record(Judgement.HIT300, true);
        score.record(Judgement.HIT300, true);
        assertEquals(600, score.snapshot().score());
        score.record(Judgement.HIT100, true);
        assertEquals(716, score.snapshot().score());
        score.record(Judgement.MISS, true);
        assertEquals(0, score.snapshot().combo());
        score.record(Judgement.HIT50, true);
        assertEquals(766, score.snapshot().score());
        assertEquals((double) (750f / 1500f), score.snapshot().accuracy());
    }

    @Test void tailLossPreservesComboButCannotReachPerfectAndSliderIsOneAccuracyObject() {
        var score = new OsuScoreV1(4);
        score.nested(30, true, true); // head
        score.nested(10, true, true); // tick
        score.nested(30, false, false); // tail
        score.record(Judgement.HIT100, false); // 2 of 3 parts
        assertEquals(156, score.snapshot().score());
        assertEquals(2, score.snapshot().maxCombo());
        assertEquals(2, score.snapshot().combo());
        assertEquals(1, score.snapshot().count100());
        assertEquals(0, score.snapshot().misses());
        assertEquals((double) (1f / 3), score.snapshot().accuracy());
    }

    @Test void fullSliderCountsItsHeadAndTailOnlyOnceInCombo() {
        var score = new OsuScoreV1(4);
        score.nested(30, true, true);
        score.nested(10, true, true);
        score.nested(30, true, false);
        score.record(Judgement.HIT300, false);
        assertEquals(466, score.snapshot().score());
        assertEquals(3, score.snapshot().combo());
        score.bonus(1100);
        assertEquals(1566, score.snapshot().score());
        assertEquals(1, score.snapshot().count300());
    }

    @Test void difficultyUsesBreaksAndLastStartRatherThanLastEnd() throws Exception {
        String source = "osu file format v14\n[HitObjects]\n100,100,1000,1,0\n256,192,6000,8,0,60000\n";
        var parser = new BeatmapFileParser();
        assertEquals(2, OsuScoreV1.difficultyMultiplier(parser.parse(source, "score.osu").difficulty()));
        assertEquals(3, OsuScoreV1.difficultyMultiplier(parser.parse(source + "[Events]\n2,2000,5000", "score.osu").difficulty()));
        assertEquals(4, OsuScoreV1.difficultyMultiplier(parser.parse("osu file format v14\n[HitObjects]\n100,100,1000,1,0", "one.osu").difficulty()));
    }

    @Test void comboSetVariantsUseJudgementTimeAndDoNotCountAsExtraObjects() throws Exception {
        var objects = new BeatmapFileParser().parse("osu file format v14\n[HitObjects]\n100,100,1000,5,0\n300,100,1500,1,0\n100,100,2000,5,0", "sets.osu").difficulty().hitObjects();
        var sets = new OsuComboSets(objects);
        assertEquals(OsuComboSets.Variant.NONE, sets.record(objects.get(0), Judgement.HIT100));
        assertEquals(OsuComboSets.Variant.KATU, sets.record(objects.get(1), Judgement.HIT300));
        assertEquals(OsuComboSets.Variant.GEKI, sets.record(objects.get(2), Judgement.HIT300));
        assertEquals(1, sets.geki());
        assertEquals(1, sets.katu());
        var overlapping = new OsuComboSets(objects);
        assertEquals(OsuComboSets.Variant.NORMAL_END, overlapping.record(objects.get(1), Judgement.HIT300));
        assertEquals(0, overlapping.geki(), "An earlier unjudged object suppresses Geki");
    }
}
