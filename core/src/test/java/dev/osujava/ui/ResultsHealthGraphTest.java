package dev.osujava.ui;

import dev.osujava.score.ScoreDetails.HealthPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultsHealthGraphTest {
    @Test void revealUsesLengthRatherThanXAndColourUsesDestinationHealth() {
        var graph = new ResultsHealthGraph(List.of(new HealthPoint(0,1),new HealthPoint(50,0),new HealthPoint(100,0)));
        var partial = graph.reveal(.5f);
        assertEquals(1,partial.size()); assertTrue(partial.getFirst().x2()<93);
        assertFalse(partial.getFirst().healthy());
        assertEquals(186,graph.reveal(1).getLast().x2());
    }
    @Test void hundredPointLimitRemovesAlternatingPointsFromTailWithoutRenormalizing() {
        var samples = new ArrayList<HealthPoint>();
        for(int i=0;i<101;i++) samples.add(new HealthPoint(i,1));
        var segments = new ResultsHealthGraph(samples).reveal(1);
        assertEquals(49,segments.size());
        assertEquals(1.86f,segments.getFirst().x1(),.00001);
        assertEquals(184.14f,segments.getLast().x2(),.00002);
        assertEquals(101,samples.size());
    }
    @Test void unknownSingletonAndZeroDurationNeverInventALineOrNan() {
        assertTrue(new ResultsHealthGraph(null).reveal(1).isEmpty());
        assertTrue(new ResultsHealthGraph(List.of(new HealthPoint(1,1))).reveal(1).isEmpty());
        assertTrue(new ResultsHealthGraph(List.of(new HealthPoint(1,1),new HealthPoint(1,0))).reveal(1).isEmpty());
    }
}
