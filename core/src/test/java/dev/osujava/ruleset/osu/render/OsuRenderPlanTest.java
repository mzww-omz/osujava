package dev.osujava.ruleset.osu.render;

import dev.osujava.beatmap.*;
import dev.osujava.gameplay.*;
import dev.osujava.ruleset.osu.OsuGameplaySession;
import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.osujava.ruleset.osu.render.OsuRenderPlan.Layer.*;
import static dev.osujava.ruleset.osu.render.OsuRenderPlan.Piece.*;
import static org.junit.jupiter.api.Assertions.*;

class OsuRenderPlanTest {
    @Test void animatingHitCircleStaysInObjectLocalOrderWithNeighbouringSlider() {
        long[] now = {1000};
        var session = new OsuGameplaySession(map(List.of(circle(1000, 256), slider(1100, 256, 2))),
                () -> now[0], new JudgementWindows(50, 100, 150));
        session.click(256, 192);
        for (long t : new long[]{1000, 1120, 1240}) {
            now[0] = t;
            var state = session.update();
            assertContiguous(objects(state, true));
            assertContiguous(objects(state, false));
            assertEquals(Judgement.HIT300, state.circles().getFirst().judgement());
            assertEquals(List.of(CIRCLE_BASE, NUMBER, CIRCLE_OVERLAY), objects(state, true).stream()
                    .filter(c -> c.object() instanceof HitCircleVisual).map(OsuRenderPlan.Command::piece).toList());
        }
    }

    @Test void samePositionCirclesKeepBaseNumberOverlayContiguous() {
        var state = snapshot(List.of(circle(1000, 256), circle(1100, 256)), 900);
        var commands = objects(state, true);
        assertEquals(List.of(1, 1, 1, 0, 0, 0), commands.stream().map(c -> c.object().beatmapIndex()).toList());
        assertEquals(List.of(CIRCLE_BASE, NUMBER, CIRCLE_OVERLAY, CIRCLE_BASE, NUMBER, CIRCLE_OVERLAY),
                commands.stream().map(OsuRenderPlan.Command::piece).toList());
    }

    @Test void startTimeTakesPriorityOverObjectTypeAndBeatmapIndex() {
        var state = snapshot(List.of(circle(1100, 256), circle(1000, 256)), 900);
        assertEquals(List.of(0, 1), state.drawOrder().stream().map(HitObjectVisual::beatmapIndex).toList());
    }

    @Test void exactTiesUseReverseOriginalBeatmapIndexIncludingAcrossTypes() {
        var state = snapshot(List.of(circle(1000, 256), slider(1000, 256, 1), circle(1000, 256)), 900);
        assertEquals(List.of(2, 1, 0), state.drawOrder().stream().map(HitObjectVisual::beatmapIndex).toList());
        assertContiguous(objects(state, true));
    }

    @Test void oneMillisecondDifferenceIsNotTreatedAsATie() {
        var state = snapshot(List.of(circle(1001, 256), circle(1000, 256)), 900);
        assertEquals(List.of(0, 1), state.drawOrder().stream().map(HitObjectVisual::beatmapIndex).toList());
    }

    @Test void headTailAndRepeatOverlapUseParentSliderDepth() {
        for (int repeats : new int[]{1, 3}) {
            for (double x : new double[]{156, 256}) {
                for (long circleTime : new long[]{990, 1010}) {
                    var state = snapshot(List.of(slider(1000, 156, repeats), circle(circleTime, x)), 900);
                    var commands = objects(state, true);
                    assertContiguous(commands);
                    int sliderStart = index(commands, SLIDER_BODY);
                    int circleStart = index(commands, CIRCLE_BASE);
                    assertEquals(circleTime < 1000, sliderStart < circleStart);
                    assertTrue(index(commands, TAIL_OVERLAY) < index(commands, TICKS));
                    assertTrue(index(commands, TICKS) < index(commands, REPEATS));
                    assertTrue(index(commands, HEAD_BASE) < index(commands, REVERSE_ARROWS));
                    assertTrue(index(commands, REVERSE_ARROWS) < index(commands, HEAD_NUMBER));
                    assertTrue(index(commands, HEAD_NUMBER) < index(commands, HEAD_OVERLAY));
                    assertTrue(index(commands, HEAD_OVERLAY) < index(commands, BALL_AND_FOLLOW));
                }
            }
        }
    }

    @Test void disablingOverlayAboveNumberChangesOnlyLocalForegroundOrder() {
        var state = snapshot(List.of(circle(1000, 256), slider(1000, 156, 2)), 900);
        var commands = objects(state, false);
        assertContiguous(commands);
        assertTrue(index(commands, CIRCLE_OVERLAY) < index(commands, NUMBER));
        assertTrue(index(commands, HEAD_OVERLAY) < index(commands, HEAD_NUMBER));
        assertTrue(index(commands, HEAD_NUMBER) < index(commands, BALL_AND_FOLLOW));
    }

    @Test void playfieldProxyAndJudgementLayersRemainSeparate() {
        var spinner = new HitObject(256, 192, 1000, HitObject.Type.SPINNER, 8, 0, null, new SpinnerData(2000));
        var state = snapshot(List.of(circle(1000, 256), spinner, slider(1100, 156, 2)), 900);
        var commands = OsuRenderPlan.create(state, true);
        assertEquals(SPINNER_PROXY, commands.getFirst().layer());
        assertEquals(OsuRenderPlan.Layer.HUD, commands.getLast().layer());
        assertTrue(commands.stream().filter(c -> c.layer() == HIT_OBJECT)
                .noneMatch(c -> c.object() instanceof SpinnerVisual));
        assertEquals(List.of(0, 2), commands.stream().filter(c -> c.layer() == APPROACH_PROXY)
                .map(c -> c.object().beatmapIndex()).toList());
        int lastLayer = -1;
        for (var command : commands) {
            assertTrue(command.layer().ordinal() >= lastLayer);
            lastLayer = command.layer().ordinal();
        }
        assertEquals(1, commands.stream().filter(c -> c.layer() == OsuRenderPlan.Layer.JUDGEMENT_BELOW).count());
        assertEquals(1, commands.stream().filter(c -> c.layer() == OsuRenderPlan.Layer.JUDGEMENT_ABOVE).count());
    }

    @Test void visibilityFilteringDoesNotRenumberObjectsOrChangeOrder() {
        var map = map(List.of(circle(1000, 256), circle(1000, 256), circle(3000, 256)));
        long[] now = {900};
        var session = new OsuGameplaySession(map, () -> now[0], new JudgementWindows(50, 100, 150));
        assertEquals(List.of(1, 0), session.state().drawOrder().stream().map(HitObjectVisual::beatmapIndex).toList());
        now[0] = 2800;
        assertEquals(List.of(2), session.update().drawOrder().stream().map(HitObjectVisual::beatmapIndex).toList());
        assertThrows(UnsupportedOperationException.class, () -> session.state().drawOrder().clear());
    }

    private void assertContiguous(List<OsuRenderPlan.Command> commands) {
        var finished = new java.util.HashSet<Integer>();
        int previous = -1;
        for (var command : commands) {
            int index = command.object().beatmapIndex();
            if (index != previous) {
                assertFalse(finished.contains(index), "Object pieces must not interleave");
                finished.add(previous);
                previous = index;
            }
        }
    }
    private int index(List<OsuRenderPlan.Command> commands, OsuRenderPlan.Piece piece) {
        for (int i = 0; i < commands.size(); i++) if (commands.get(i).piece() == piece) return i;
        fail("Missing piece: " + piece);
        return -1;
    }
    private List<OsuRenderPlan.Command> objects(GameplayState state, boolean overlay) {
        return OsuRenderPlan.create(state, overlay).stream().filter(c -> c.layer() == HIT_OBJECT).toList();
    }
    private GameplayState snapshot(List<HitObject> objects, long now) {
        return new OsuGameplaySession(map(objects), () -> now, new JudgementWindows(50, 100, 150)).state();
    }
    private BeatmapDifficulty map(List<HitObject> objects) {
        return new BeatmapDifficulty("Layering", "Test", "Test", "Overlap", 0, "", "",
                new DifficultySettings(5, 5, 5, 5, 1.4, 1), List.of(), objects, null, null);
    }
    private HitObject circle(long time, double x) {
        return new HitObject(x, 192, time, HitObject.Type.CIRCLE, 1, 0);
    }
    private HitObject slider(long time, double x, int spans) {
        return new HitObject(x, 192, time, HitObject.Type.SLIDER, 2, 0,
                new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.LINEAR, 0,
                        List.of(new BeatmapPoint(x, 192), new BeatmapPoint(x + 100, 192)))), spans - 1, 100));
    }
}
