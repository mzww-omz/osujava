package dev.osujava.ruleset.osu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
class OsuGradeTest {
    @ParameterizedTest @CsvSource({
        "100,0,0,0,SS", "91,9,0,0,S", "90,10,0,0,A", "98,1,1,0,S", "97,1,2,0,A",
        "91,8,0,1,A", "81,19,0,0,A", "80,20,0,0,B", "81,18,0,1,B",
        "71,29,0,0,B", "70,30,0,0,C", "71,28,0,1,C", "61,39,0,0,C",
        "60,40,0,0,D", "60,0,0,40,D", "0,0,0,0,D", "-1,0,0,0,D",
        "1,-1,0,0,D", "1,0,-1,0,D", "1,0,0,-1,D"})
    void judgementBoundaries(int a,int b,int c,int d,OsuGrade grade) {
        assertEquals(grade, OsuGrade.calculate(a,b,c,d));
    }
    @Test void avoidsIntegerOverflow() { assertEquals(OsuGrade.D,OsuGrade.calculate(Integer.MAX_VALUE,Integer.MAX_VALUE,0,0)); }
}
