package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectCarouselMotionTest {
    private List<SongSelectCarousel.Entry> collapsed(int count) {
        return IntStream.range(0, count).mapToObj(i -> new SongSelectCarousel.Entry("s" + i + "#-1", i, -1)).toList();
    }
    private List<SongSelectCarousel.Entry> expanded(int count, int set, int difficulties) {
        var result = new ArrayList<SongSelectCarousel.Entry>();
        for (var entry : collapsed(count)) {
            if (entry.setIndex() != set) result.add(entry);
            else for (int i = 0; i < difficulties; i++) result.add(new SongSelectCarousel.Entry("s" + set + "#" + i, set, i));
        }
        return result;
    }
    private SongSelectCarousel model() {
        var model = new SongSelectCarousel();
        model.content(collapsed(30), 620, 76, 72, "s0#-1");
        return model;
    }
    private SongSelectCarousel.Row row(SongSelectCarousel model, String key) {
        return model.rows().stream().filter(r -> r.entry.key().equals(key)).findFirst().orElseThrow();
    }
    private void settle(SongSelectCarousel model) { for (int i = 0; i < 180; i++) model.advance(1f / 60, null); }

    @Test void wheelImpulseIsBoundedDecaysAndReleasesOnReversal() {
        var model = model();
        model.scrollBy(Float.MAX_VALUE);
        assertEquals(model.rowHeight() * 36, model.scrollVelocity());
        assertEquals(model.maxScroll(), model.scrollTarget());
        float velocity = model.scrollVelocity();
        model.advance(.1f, null);
        assertTrue(model.scrollVelocity() > 0 && model.scrollVelocity() < velocity);
        assertTrue(model.velocityInfluence() > 0 && model.velocityInfluence() <= 1);
        model.scrollBy(-.25f);
        assertTrue(model.scrollVelocity() < 0, "Reversal must not retain the previous positive impulse");
        settle(model);
        assertEquals(0, model.scrollVelocity(), .01);
        assertEquals(0, model.velocityInfluence(), .001);
    }

    @Test void springHasSoftStartAndSettlesWithoutOvershootForAnIsolatedInput() {
        var model = model(); model.scrollBy(76);
        model.advance(1f / 120, null);
        assertTrue(model.scrollOffset() > 0 && model.scrollOffset() < 2);
        float previous = model.scrollOffset();
        for (int i = 0; i < 180; i++) {
            model.advance(1f / 60, null);
            assertTrue(model.scrollOffset() >= previous);
            assertTrue(model.scrollOffset() <= 76);
            previous = model.scrollOffset();
        }
        assertEquals(76, model.scrollOffset(), .001);
    }

    @Test void repeatedWheelTimelineAndVisualResponseAgreeAtThirtySixtyAndOneTwentyHz() {
        var models = new ArrayList<SongSelectCarousel>();
        for (int fps : new int[]{30, 60, 120}) {
            var model = model();
            for (int frame = 0; frame < fps; frame++) {
                if (frame % (fps / 5) == 0) model.scrollBy(frame < fps / 2 ? 140 : -40);
                model.advance(1f / fps, null);
            }
            models.add(model);
        }
        for (var model : models) {
            assertEquals(models.getFirst().scrollOffset(), model.scrollOffset(), .02);
            assertEquals(models.getFirst().scrollVelocity(), model.scrollVelocity(), .02);
            assertEquals(models.getFirst().velocityInfluence(), model.velocityInfluence(), .003);
        }
    }

    @Test void tinyDeltasRemainContinuousAndBoundariesDoNotBuildPhantomVelocity() {
        var model = model(); model.scrollBy(-76);
        assertEquals(0, model.scrollVelocity());
        model.scrollBy(.001f); assertEquals(.001f, model.scrollTarget());
        assertTrue(model.scrollVelocity() > 0);
        model.scrollBy(.001f); assertEquals(.002f, model.scrollTarget());
        model.scrollBy(Float.MAX_VALUE); settle(model);
        model.scrollBy(76); assertEquals(0, model.scrollVelocity(), .01);
    }

    @Test void wheelBrowsingIsNotRecenteredByRepeatedSelectionOrResize() {
        var model = model(); model.select("s4#-1"); settle(model);
        model.scrollBy(200); float target = model.scrollTarget();
        model.select("s4#-1"); assertEquals(target, model.scrollTarget());
        model.content(collapsed(30), 420, 90, 93, "s4#-1");
        assertEquals(target * 93 / 72, model.scrollTarget(), .001);
        model.select("s5#-1"); assertEquals(5 * 93, model.scrollTarget());
    }

    @Test void selectionAndHoverEaseIndependentlyWhileWheelOnlyChangesViewport() {
        var model = model(); model.select("s3#-1");
        assertEquals(0, row(model, "s3#-1").selectedAmount);
        model.advance(.05f, "s2#-1");
        assertTrue(row(model, "s3#-1").selectedAmount > 0);
        assertEquals(0, row(model, "s3#-1").hoverAmount);
        assertEquals(0, row(model, "s2#-1").selectedAmount);
        assertTrue(row(model, "s2#-1").hoverAmount > 0);
        model.scrollBy(100); settle(model);
        assertEquals(1, row(model, "s3#-1").selectedAmount, .001);
        assertEquals(3 * 72 + 100, model.scrollTarget());
    }

    @Test void fastScrollingWeakensHoverAndItRecoversWithoutChangingSelection() {
        var slow = model(); var fast = model();
        for (int i = 0; i < 12; i++) {
            slow.scrollBy(.01f); fast.scrollBy(140);
            slow.advance(1f / 60, "s2#-1"); fast.advance(1f / 60, "s2#-1");
        }
        assertTrue(row(fast, "s2#-1").hoverAmount < row(slow, "s2#-1").hoverAmount * .65f);
        for (int i = 0; i < 180; i++) fast.advance(1f / 60, "s2#-1");
        assertEquals(1, row(fast, "s2#-1").hoverAmount, .001);
        assertEquals(1, row(fast, "s0#-1").selectedAmount, .001);
    }

    @Test void velocityDeformationIsSmallBoundedAndReturnsToTheOriginalCurve() {
        var model = model(); model.scrollBy(2000);
        for (int i = 0; i < 12; i++) {
            model.advance(1f / 60, null);
            for (var row : model.rows()) {
                float center = model.renderY(row, 658) + model.rowHeight() / 2;
                float distance = (658 - center - 310) / 310;
                float baseline = SongSelectCarousel.curveX(distance, 1280) - 24 * row.selectedAmount;
                assertTrue(Math.abs(model.renderX(row, 1280) - baseline) <= 18.01);
                assertTrue(model.renderX(row, 1280) >= 1280 * .52f);
                assertTrue(model.renderX(row, 1280) <= 1280 * .74f);
            }
        }
        settle(model);
        var row = row(model, "s20#-1");
        float center = model.renderY(row, 658) + model.rowHeight() / 2;
        assertEquals(SongSelectCarousel.curveX((658 - center - 310) / 310, 1280), model.renderX(row, 1280), .01);
    }

    @Test void expansionAndCollapsePreserveEverySurvivingRowIncludingInterruptedMotion() {
        for (int count : new int[]{1, 4, 16}) for (int selected : new int[]{0, count - 1}) {
            var model = model(); model.select("s4#-1"); settle(model);
            model.advance(.1f, "s5#-1");
            float aboveY = model.renderY(row(model, "s3#-1"), 658);
            float belowY = model.renderY(row(model, "s5#-1"), 658);
            float parentY = model.renderY(row(model, "s4#-1"), 658);
            float parentX = model.renderX(row(model, "s4#-1"), 1280);
            model.content(expanded(30, 4, count), 620, 76, 72, "s4#" + selected);
            assertEquals(aboveY, model.renderY(row(model, "s3#-1"), 658), .001);
            assertEquals(belowY, model.renderY(row(model, "s5#-1"), 658), .001);
            assertEquals(parentY, model.renderY(row(model, "s4#" + selected), 658), .001);
            assertEquals(parentX, model.renderX(row(model, "s4#" + selected), 1280), .001);
            model.advance(.05f, null);
            belowY = model.renderY(row(model, "s5#-1"), 658);
            parentY = model.renderY(row(model, "s4#" + selected), 658);
            model.content(collapsed(30), 620, 76, 72, "s4#-1");
            assertEquals(belowY, model.renderY(row(model, "s5#-1"), 658), .001);
            assertEquals(parentY, model.renderY(row(model, "s4#-1"), 658), .001);
            settle(model);
            assertEquals(348, model.renderY(row(model, "s4#-1"), 658) + 38, .01);
        }
    }

    @Test void expandingFirstOrLastSetCentersFirstAndLastDifficultyEvenWhenAnchorClamps() {
        for (int set : new int[]{0, 29}) for (int difficulty : new int[]{0, 15}) {
            var model = model(); model.select("s" + set + "#-1"); settle(model);
            float y = model.renderY(row(model, "s" + set + "#-1"), 658);
            model.content(expanded(30, set, 16), 620, 76, 72, "s" + set + "#" + difficulty);
            var row = row(model, "s" + set + "#" + difficulty);
            assertEquals(y, model.renderY(row, 658), .001);
            settle(model); assertEquals(348, model.renderY(row, 658) + 38, .01);
            assertTrue(model.scrollOffset() >= 0 && model.scrollOffset() <= model.maxScroll());
        }
    }

    @Test void invalidInputsCannotPoisonMotionAndEmptyContentClearsVelocity() {
        var model = model(); model.scrollBy(150);
        float target = model.scrollTarget(), velocity = model.scrollVelocity();
        for (float bad : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            model.scrollBy(bad); model.advance(bad, null);
            assertEquals(target, model.scrollTarget()); assertEquals(velocity, model.scrollVelocity());
        }
        model.content(collapsed(30), Float.NaN, Float.POSITIVE_INFINITY, -1, "s0#-1");
        model.advance(Float.MAX_VALUE, null);
        for (var row : model.rows()) {
            assertTrue(Float.isFinite(model.renderX(row, 1280)));
            assertTrue(Float.isFinite(model.renderY(row, 658)));
        }
        model.content(List.of(), 620, 76, 72, null);
        assertEquals(0, model.scrollVelocity()); assertEquals(0, model.velocityInfluence());
    }

    @Test void thousandSetScrollingRetainsContentIdentitiesAndSettlesAllTransientMotion() {
        var model = new SongSelectCarousel(); var entries = collapsed(1000);
        model.content(entries, 620, 76, 72, "s0#-1"); var rows = model.rows();
        for (int frame = 0; frame < 600; frame++) {
            model.scrollBy(frame < 300 ? 150 : -150); model.advance(1f / 60, null);
            assertSame(rows, model.rows());
        }
        settle(model);
        assertEquals(model.scrollTarget(), model.scrollOffset(), .01);
        assertEquals(0, model.velocityInfluence(), .001);
    }
    @Test void expansionAndViewportSpringMoveChosenDifficultyMonotonicallyTowardCenter() {
        for (int difficulty : new int[]{0, 15}) {
            var model = model(); model.select("s3#-1"); settle(model);
            float start = model.renderY(row(model,"s4#-1"),658) + 38;
            model.content(expanded(30,4,16),620,76,72,"s4#" + difficulty);
            var selected = row(model,"s4#" + difficulty);
            float previous = start;
            for (int frame = 0; frame < 120; frame++) {
                model.advance(1f/60,null);
                float center = model.renderY(selected,658) + 38;
                assertTrue(center >= previous - .001f, "Expansion must not initially move away from the snap destination");
                assertTrue(center <= 348.01f, "Opposing expansion/viewport rates must not overshoot the center");
                previous = center;
            }
            assertEquals(348,previous,.01);
        }
    }

    @Test void contentFilteringKeepsSelectedAnchorAndRelativeWheelBrowsingTarget() {
        var model = model(); model.select("s4#-1"); settle(model);
        var filtered = collapsed(30).subList(3,30);
        model.content(filtered,620,76,72,"s4#-1");
        assertEquals(72,model.scrollTarget());
        model.scrollBy(200);
        model.content(collapsed(30),620,76,72,"s4#-1");
        assertEquals(4 * 72 + 200,model.scrollTarget());
        model.content(List.of(),620,76,72,"s4#-1");
        model.content(filtered,620,76,72,"s4#-1");
        assertEquals(72,model.scrollTarget());
        settle(model); assertEquals(348,model.renderY(row(model,"s4#-1"),658) + 38,.01);
    }

}
