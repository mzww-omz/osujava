package dev.osujava.ui;

import dev.osujava.gameplay.*;
import dev.osujava.score.*;
import dev.osujava.skin.ResultsSkinAssets.Image;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultsPresentationTest {
    private final ScoreState score = new ScoreState(12345, 0, 10, 10, 0, 0, 0, 1);
    @Test void oldScoresHaveUnknownVariantsAndNoInventedPerfectOrUr() {
        var old = new ResultsSnapshot(score, ScoreDetails.LEGACY, 1234, GameplayRunMode.MANUAL, true);
        var result = ResultsPresentation.of(old);
        assertEquals(List.of("10x", "0x", "0x", "-", "-", "0x"), result.counts());
        assertEquals(Image.SS, result.grade());
        assertFalse(result.perfect()); assertNull(result.timing());
    }
    @Test void perfectAndFailAreIndependentFromAccuracyAndSavedBrowsingHasNoLiveUr() {
        var details = new ScoreDetails(ScoreDetails.SCORE_V1, "", "", 2, 1, 11, false, false,
                null, List.of(-10, 0, 10), null);
        var live = new ResultsSnapshot(score, details, 1234, GameplayRunMode.MANUAL, false);
        assertEquals(Image.F, ResultsPresentation.of(live).grade());
        assertFalse(ResultsPresentation.of(live).perfect());
        assertEquals(81.649658, ResultsPresentation.of(live).timing().unstableRate(), .000001);
        var saved = new LocalScore(UUID.randomUUID(), new DifficultyIdentity("map", "map.osu"),1234,score,details);
        assertNull(ResultsPresentation.of(ResultsSnapshot.saved(saved)).timing());
    }
    @Test void browserOpeningPreservesDateWithoutWritingAnotherPlay() {
        var store = new LocalScoreStore(); var identity = new DifficultyIdentity("map", "map.osu");
        store.save(new LocalScore(UUID.randomUUID(), identity, 1234, score), GameplayRunMode.MANUAL);
        long revision = store.revision();
        var browser = new ScoreBrowserModel(store); browser.target(identity);
        assertNull(browser.open(-1)); assertNull(browser.open(1));
        for (int i = 0; i < 3; i++) {
            assertEquals(1234, browser.open(0).playedAt()); assertTrue(browser.open(0).savedScore());
        }
        assertEquals(revision, store.revision()); assertEquals(1, store.query(identity).size());
    }
    @Test void viewportUsesHeightFor480CoordinatesAndKeepsLegacyAndModernAnchors() {
        for (int height : new int[]{720,1080,1440}) {
            var old = ResultsLayout.fit(height * 16 / 9, height,true);
            assertEquals(854,old.width()); assertEquals(480,old.pointer(height),.0001);
            assertEquals(46,old.panelY()); assertEquals(135,old.countY(0)); assertEquals(170,old.gradeY());
            var modern = ResultsLayout.fit(height * 16 / 9,height,false);
            assertEquals(64,modern.panelY()); assertEquals(144,modern.countY(0)); assertEquals(200,modern.gradeY());
        }
        assertEquals(640,ResultsLayout.fit(640,480,false).width());
    }
}
