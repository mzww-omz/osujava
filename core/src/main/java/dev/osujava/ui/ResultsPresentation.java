package dev.osujava.ui;

import dev.osujava.ruleset.osu.OsuGrade;
import dev.osujava.score.ResultStatistics;
import dev.osujava.score.ResultsSnapshot;
import dev.osujava.score.ScoreDetails;
import dev.osujava.skin.LegacyHudLayout;
import dev.osujava.skin.ResultsSkinAssets.Image;
import java.util.List;

/** Display values built once; null details cannot become invented zero counts or Perfect. */
public record ResultsPresentation(String score, String combo, String accuracy, List<String> counts,
                                  Image grade, boolean perfect, ResultStatistics timing, String provenance) {
    public static ResultsPresentation of(ResultsSnapshot snapshot) {
        var s = snapshot.score(); var d = snapshot.details();
        var grade = ScoreDetails.SCORE_V1.equals(d.scoringVersion())
                ? OsuGrade.calculateStable(s.count300(), s.count100(), s.count50(), s.misses())
                : OsuGrade.calculate(s.count300(), s.count100(), s.count50(), s.misses());
        return new ResultsPresentation(LegacyHudLayout.scoreText(s.score()), s.maxCombo() + "x",
                LegacyHudLayout.accuracyText(s.accuracy()),
                List.of(count(s.count300()), count(s.count100()), count(s.count50()), count(d.geki()), count(d.katu()), count(s.misses())),
                Boolean.FALSE.equals(d.passed()) ? Image.F : Image.valueOf(grade.name()),
                Boolean.TRUE.equals(d.perfect()), snapshot.savedScore() ? null : ResultStatistics.of(d.hitErrors()),
                ScoreContextPresentation.of(snapshot.context(),d));
    }
    private static String count(Integer value) { return value == null ? "-" : value + "x"; }
}
