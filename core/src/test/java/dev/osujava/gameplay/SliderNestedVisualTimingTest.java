package dev.osujava.gameplay;

import dev.osujava.beatmap.BeatmapPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SliderNestedVisualTimingTest {
    @Test
    void firstSpanTicksUseTheirOwnTimeAndSpanStart() {
        SliderNestedVisualTiming earlyTick = SliderNestedVisualTiming.tick(
                1_000, 1_200, 5_000, 0, 1_250);
        assertEquals(333, earlyTick.lifetimeStartTimeMs(), 1e-9);
        assertEquals(0, earlyTick.alphaAt(333));
        assertEquals(1, earlyTick.alphaAt(483));

        SliderNestedVisualTiming lateTick = SliderNestedVisualTiming.tick(
                1_000, 1_200, 5_000, 0, 5_000);
        assertEquals(2_208, lateTick.lifetimeStartTimeMs(), 1e-9);
        assertEquals(0, lateTick.alphaAt(1_000),
                "a late first-span tick must not appear with the slider head");
        assertEquals(0, lateTick.alphaAt(2_207));
        assertEquals(1, lateTick.alphaAt(2_358));
    }

    @Test
    void repeatSpanTickUsesItsSpanStartAndTheLazerRepeatOffset() {
        SliderNestedVisualTiming repeatTick = SliderNestedVisualTiming.tick(
                1_000, 1_200, 5_000, 1, 8_000);

        assertEquals(6_800, repeatTick.lifetimeStartTimeMs(), 1e-9);
        assertEquals(0, repeatTick.alphaAt(1_000));
        assertEquals(0, repeatTick.alphaAt(6_800));
        assertEquals(1, repeatTick.alphaAt(6_950));
    }

    @Test
    void successiveRepeatCirclesAndTailEnterAtTheirOwnLifetimes() {
        SliderNestedVisualTiming firstRepeat = SliderNestedVisualTiming.endCircle(
                1_000, 1_200, 1_000, 2_000, 0, 400, true);
        SliderNestedVisualTiming secondRepeat = SliderNestedVisualTiming.endCircle(
                1_000, 1_200, 1_000, 3_000, 1, 400, true);
        SliderNestedVisualTiming tail = SliderNestedVisualTiming.endCircle(
                1_000, 1_200, 1_000, 4_000, 2, 400, true);

        assertEquals(-200, firstRepeat.lifetimeStartTimeMs(), 1e-9);
        assertEquals(1_000, secondRepeat.lifetimeStartTimeMs(), 1e-9);
        assertEquals(2_000, tail.lifetimeStartTimeMs(), 1e-9);
        assertEquals(0, secondRepeat.alphaAt(999));
        assertEquals(1, secondRepeat.alphaAt(1_000));
        assertEquals(0, tail.alphaAt(1_999));
        assertEquals(1, tail.alphaAt(2_000));

        BeatmapPoint point = new BeatmapPoint(0, 0);
        SliderVisual slider = new SliderVisual(List.of(point), point, point, point,
                List.of(new SliderVisual.RepeatMarker(point, 0, false, 2_000),
                        new SliderVisual.RepeatMarker(point, 1, false, 3_000)),
                50, 200, 0, 1_000, 4_000, false, false, false,
                1_200, 1, Long.MIN_VALUE, 0, List.of());
        assertEquals(2_000, slider.tailVisualTiming().lifetimeStartTimeMs(), 1e-9,
                "the final tail uses its actual index after all repeat markers");
    }

    @Test
    void snakingDelaysTheFirstEndCircleUntilTheBodyFinishesSnaking() {
        SliderNestedVisualTiming tail = SliderNestedVisualTiming.endCircle(
                1_000, 1_200, 5_000, 6_000, 0, 400, true);
        double sliderSnakeEndTime = 1_000 - 1_200 + 1_200 / 3.0;

        assertEquals(-200, tail.lifetimeStartTimeMs(), 1e-9);
        assertEquals(sliderSnakeEndTime, tail.fadeInStartTimeMs(), 1e-9);
        assertEquals(0, tail.alphaAt(sliderSnakeEndTime - 1));
        assertEquals(1, tail.alphaAt(sliderSnakeEndTime + 400));
    }

    @Test
    void reverseArrowHasItsOwnFadeWhileSharingRepeatLifetime() {
        SliderNestedVisualTiming firstRepeatCircle = SliderNestedVisualTiming.endCircle(
                1_000, 1_200, 1_000, 2_000, 0, 400, true);
        SliderNestedVisualTiming firstReverseArrow = SliderNestedVisualTiming.reverseArrow(
                1_000, 1_200, 1_000, 2_000, 0, true);
        SliderNestedVisualTiming laterRepeatCircle = SliderNestedVisualTiming.endCircle(
                1_000, 1_200, 1_000, 3_000, 1, 400, true);
        SliderNestedVisualTiming laterReverseArrow = SliderNestedVisualTiming.reverseArrow(
                1_000, 1_200, 1_000, 3_000, 1, true);

        assertEquals(firstRepeatCircle.lifetimeStartTimeMs(), firstReverseArrow.lifetimeStartTimeMs());
        assertEquals(firstRepeatCircle.fadeInStartTimeMs(), firstReverseArrow.fadeInStartTimeMs());
        assertEquals(150, firstReverseArrow.fadeInDurationMs());
        assertEquals(400, firstRepeatCircle.fadeInDurationMs());
        assertEquals(1_000, laterReverseArrow.lifetimeStartTimeMs(), 1e-9);
        assertEquals(1, laterRepeatCircle.alphaAt(1_000));
        assertEquals(0, laterReverseArrow.alphaAt(1_000));
        assertEquals(1, laterReverseArrow.alphaAt(1_150));
    }
}
