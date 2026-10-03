package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.gameplay.*;
import dev.osujava.score.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreContextPresentationTest {
    @Test void browserAndSavedResultsDisplayCollectedContextAndNeverInferLegacyNoMod() {
        var content = new BeatmapContentKey("a".repeat(64),0);
        var details = new ScoreDetails(ScoreDetails.SCORE_V1,content.sha256(),"",null,null,null,null,null,null,null,null);
        var context = new PlayContext(content,0,"osu","osu-java-standard-1",ScoreDetails.SCORE_V1,List.of(),
                new LocalPlayer(UUID.randomUUID(),"星の旅人"),GameplayRunMode.MANUAL);
        var location = new DifficultyIdentity("set","map.osu");
        var score = new LocalScore(UUID.randomUUID(),location,1234,new ScoreState(100,0,1,1,0,0,0,1),details,context);
        var store = new LocalScoreStore(); store.save(score,GameplayRunMode.MANUAL);
        var browser = new ScoreBrowserModel(store); browser.target(location,content);
        var row = browser.rows().getFirst();
        assertEquals("NM · ScoreV1 · 星の旅人",row.provenance());
        assertEquals(row.provenance(),ResultsPresentation.of(browser.open(0)).provenance());
        var legacy = new LocalScore(UUID.randomUUID(),location,1,score.result());
        store.save(legacy,GameplayRunMode.MANUAL); browser.target(location,content);
        assertFalse(browser.rows().get(1).verified()); assertTrue(browser.rows().get(1).provenance().contains("unknown"));
        assertFalse(browser.rows().get(1).provenance().contains("NM"));
        assertFalse(ResultsPresentation.of(ResultsSnapshot.saved(legacy)).provenance().contains("NM"));
    }

    @Test void failedOutcomeIsExplicitWithoutChangingJudgementRatioGrade() {
        var details = new ScoreDetails(ScoreDetails.SCORE_V1,"a".repeat(64),"",null,null,null,null,false,null,null,null);
        var score = new LocalScore(UUID.randomUUID(),new DifficultyIdentity("set","map.osu"),1,
                new ScoreState(300,1,1,1,0,0,0,1),details);
        var store = new LocalScoreStore(); store.save(score,GameplayRunMode.MANUAL);
        var browser = new ScoreBrowserModel(store); browser.target(score.difficulty(),new BeatmapContentKey(details.beatmapSha256(),0));
        assertTrue(browser.rows().getFirst().provenance().startsWith("Failed · "));
        assertEquals(dev.osujava.ruleset.osu.OsuGrade.SS,score.grade());
        assertEquals(dev.osujava.skin.ResultsSkinAssets.Image.F,ResultsPresentation.of(browser.open(0)).grade());
        assertFalse(ScoreContextPresentation.of(null,ScoreDetails.LEGACY).contains("Failed"));
    }
}
