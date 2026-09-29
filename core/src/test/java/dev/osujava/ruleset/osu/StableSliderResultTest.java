package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.gameplay.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class StableSliderResultTest {
    @ParameterizedTest
    @CsvSource({
            "true, true, true, 466, 1, 0, 0, 0, 3, true",
            "false, true, true, 156, 0, 1, 0, 0, 2, false",
            "true, false, true, 160, 0, 1, 0, 0, 1, false",
            "true, true, false, 156, 0, 1, 0, 0, 2, false",
            "true, false, false, 80, 0, 0, 1, 0, 1, false",
            "false, false, false, 0, 0, 0, 0, 1, 0, false"})
    void headTickAndTailProduceOneFinalAccuracyJudgement(boolean head, boolean tick, boolean tail,
            long points, int n300, int n100, int n50, int misses, int maxCombo, boolean perfect) throws Exception {
        long[] time = {0};
        var difficulty = new BeatmapFileParser().parse("""
                osu file format v14
                [TimingPoints]
                0,500,4,0,0,100,1,0
                [HitObjects]
                100,100,1000,6,0,L|380:100,1,280
                """, "slider.osu").difficulty();
        GameplaySession session = new OsuRuleset().createSession(difficulty, () -> time[0]);
        time[0] = 1000;
        session.input(new GameplayInput(1000, 0, 100, 100, head ? 1 : 0));
        assertEquals(0, session.state().score().count300());
        time[0] = 1150;
        session.update();
        time[0] = 1500;
        session.input(new GameplayInput(1500, 1, 240, 100, tick ? 1 : 0));
        session.update();
        time[0] = 1964;
        session.input(new GameplayInput(1964, 2, 380, 100, tail ? 1 : 0));
        session.update();
        assertFalse(session.state().completed());
        time[0] = 2000;
        var result = session.update().score();
        assertEquals(points, result.score());
        assertEquals(n300, result.count300()); assertEquals(n100, result.count100());
        assertEquals(n50, result.count50()); assertEquals(misses, result.misses());
        assertEquals(maxCombo, result.maxCombo());
        assertEquals(perfect, session.resultDetails().perfect());
        assertEquals(3, session.resultDetails().possibleCombo());
        assertEquals(head ? List.of(0) : List.of(), session.resultDetails().hitErrors());
        var frozen = session.resultDetails();
        time[0] = 2100; session.update();
        assertEquals(result, session.state().score(), "Final judgement must not be applied twice");
        assertEquals(frozen, session.resultDetails());
    }

    @Test void urExcludesEmptyMissAndNotelockedInputAndPreservesSign() throws Exception {
        long[] time = {0};
        var difficulty = new BeatmapFileParser().parse("osu file format v14\n[HitObjects]\n100,100,1000,5,0\n300,100,1200,5,0", "ur.osu").difficulty();
        GameplaySession session = new OsuRuleset().createSession(difficulty, () -> time[0]);
        time[0] = 900; session.input(new GameplayInput(900, 0, 300, 100, 1)); // notelock
        session.input(new GameplayInput(900, 1, 300, 100, 0));
        time[0] = 990; session.input(new GameplayInput(990, 2, 100, 100, 1));
        session.input(new GameplayInput(990, 3, 100, 100, 0));
        time[0] = 1100; session.input(new GameplayInput(1100, 4, 500, 350, 1)); // empty
        time[0] = 1400; session.update(); // second circle expires
        assertEquals(List.of(-10), session.resultDetails().hitErrors());
        assertEquals(1, session.state().score().count300());
        assertEquals(1, session.state().score().misses());
        assertThrows(UnsupportedOperationException.class, () -> session.resultDetails().hitErrors().clear());
    }
}
