package dev.osujava.ui;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectScoreHoverTest {
    private List<ScoreBrowserModel.Row> rows(int count) {
        return IntStream.range(0, count).mapToObj(i -> new ScoreBrowserModel.Row(null, "", "", "", "")).toList();
    }
    @Test void hoverUsesTheMeasuredTwoHundredMillisecondFadeAtEveryFrameRate() {
        var rows = rows(1000);
        for (int fps : new int[]{30,60,144}) {
            var hover = new SongSelectScoreHover();
            for (int i = 0; i < fps; i++) hover.advance(1f / fps, rows, 0, 8, i < fps / 2 ? 2 : -1);
            assertEquals(.3f, hover.snapshot().alpha(2), .0001f);
            hover.advance(.1f, rows, 0, 8, 2);
            var halfway = hover.snapshot();
            assertEquals(.45f, halfway.alpha(2), .0001f);
            hover.advance(.1f, rows, 0, 8, 2);
            assertEquals(.6f, hover.snapshot().alpha(2), .0001f);
            assertEquals(.45f, halfway.alpha(2), .0001f, "Published snapshot cannot change during the next update");
            assertEquals(8, hover.snapshot().amounts().length);
        }
    }
    @Test void scrollingDifficultyChangesAndResizeCannotTransferHoverToAnotherScore() {
        var rows = rows(1000); var hover = new SongSelectScoreHover();
        hover.advance(.2f, rows, 0, 8, 2);
        hover.advance(0, rows, 10, 8, -1);
        assertEquals(.3f, hover.snapshot().alpha(2));
        hover.advance(.2f, rows, 10, 8, 2);
        hover.advance(0, rows(1000), 10, 8, -1);
        assertEquals(.3f, hover.snapshot().alpha(2));
        hover.advance(0, rows, 998, 20, -1);
        assertEquals(2, hover.snapshot().amounts().length);
        hover.advance(Float.NaN, List.of(), 0, 8, -1);
        assertEquals(0, hover.snapshot().amounts().length);
    }
}
