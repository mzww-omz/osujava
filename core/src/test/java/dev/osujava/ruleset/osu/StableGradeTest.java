package dev.osujava.ruleset.osu;

import dev.osujava.gameplay.*;
import dev.osujava.score.*;
import dev.osujava.ui.ResultsPresentation;
import dev.osujava.skin.ResultsSkinAssets.Image;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class StableGradeTest {
    @ParameterizedTest @CsvSource({
            "100,0,0,0,SS", "91,8,1,0,S", "90,10,0,0,A", "80,20,0,0,A",
            "70,30,0,0,C", "60,40,0,0,C", "90,0,0,10,B", "80,0,0,20,B",
            "60,0,0,40,C", "59,0,0,41,D", "0,0,0,0,D"})
    void singleRatioIsPromotedBeforeComparingDoubleThresholds(int n300,int n100,int n50,int miss,OsuGrade expected) {
        assertEquals(expected,OsuGrade.calculateStable(n300,n100,n50,miss));
    }
    @Test void oldGradesStayUnchangedWhileNewRecordAndResultUseStablePrecision() {
        var result=new ScoreState(1000,0,80,80,20,0,0,.8666667);
        var old=new LocalScore(UUID.randomUUID(),new DifficultyIdentity("set","map.osu"),0,result);
        var details=new ScoreDetails(ScoreDetails.SCORE_V1,"","",null,null,null,null,null,null,null,null);
        var current=new LocalScore(UUID.randomUUID(),old.difficulty(),0,result,details);
        assertEquals(OsuGrade.B,old.grade()); assertEquals(OsuGrade.A,current.grade());
        assertEquals(Image.A,ResultsPresentation.of(ResultsSnapshot.saved(current)).grade());
        assertEquals(Image.B,ResultsPresentation.of(ResultsSnapshot.saved(old)).grade());
    }
}
