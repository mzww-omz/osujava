package dev.osujava.ruleset.osu;

import dev.osujava.gameplay.GameplaySession.PointerState;
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.skin.SkinConfiguration.Cursor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCursorVisualTest {
    private LegacyCursorVisual visual(boolean middle) { return new LegacyCursorVisual(Cursor.defaults(), true, middle, 40); }
    private void input(LegacyCursorVisual v, double t, boolean down, boolean press) {
        v.input(t, new PointerState(100, 100, down), press);
    }
    @Test void absoluteClockwiseRotationLoopsAndFlagsDisableTransforms() {
        var v = visual(true);
        assertEquals(0, v.rotation(0)); assertEquals(90, v.rotation(2500));
        assertEquals(180, v.rotation(5000)); assertEquals(0, v.rotation(10000));
        var disabled = new LegacyCursorVisual(new Cursor(false, false, false, false), true, true, 40);
        input(disabled, 0, true, true);
        assertEquals(0, disabled.rotation(2500)); assertEquals(1, disabled.expandedScale(100));
        assertFalse(disabled.cursor(100).centred()); assertFalse(disabled.middle().centred());
        assertEquals(0, disabled.trailRotation(2500));
    }
    @Test void expandAndContractUseHundredMillisecondQuadraticOutAndMiddleStaysSeparate() {
        var v = visual(true);
        input(v, 1000, true, true);
        assertEquals(1, v.expandedScale(1000)); assertEquals(1.225, v.expandedScale(1050), 1e-6);
        assertEquals(1.3, v.expandedScale(1100), 1e-6);
        input(v, 1200, false, false);
        assertEquals(1.3, v.expandedScale(1200), 1e-6);
        assertEquals(1.075, v.expandedScale(1250), 1e-6); assertEquals(1, v.expandedScale(1300));
        input(v, 2000, true, true);
        assertEquals(1, v.middle().scale()); assertEquals(0, v.middle().rotation());
        assertEquals(90, v.cursor(2500).rotation());
    }
    @Test void releaseRetargetsFromCurrentScaleAndOverlappingPressRestartsAtOne() {
        var v = visual(false);
        input(v, 0, true, true); input(v, 50, false, false);
        assertEquals(1.225, v.expandedScale(50), 1e-6);
        assertEquals(1.05625, v.expandedScale(100), 1e-6);
        input(v, 200, true, true); input(v, 250, true, true);
        assertEquals(1, v.expandedScale(250));
        input(v, 350, true, false); // one action up, authoritative snapshot still down
        assertEquals(1.3, v.expandedScale(400), 1e-6);
    }
    @Test void disjointEmitsAtSixtyHzEvenWhenStationaryAndPrunesLinearHundredFiftyFade() {
        var v = visual(false); assertTrue(v.disjoint()); assertEquals(150, v.fadeDuration());
        v.move(0, 100, 100); v.advance(16); assertTrue(v.parts().isEmpty());
        v.advance(50); assertEquals(3, v.parts().size());
        var first = v.parts().getFirst();
        assertEquals(1000.0 / 60, first.createdMs(), 1e-6);
        assertEquals(1, first.alpha(first.createdMs(), 150));
        assertEquals(.5, first.alpha(first.createdMs() + 75, 150));
        assertEquals(0, first.alpha(first.createdMs() + 150, 150));
        v.advance(200); assertTrue(v.parts().size() <= 9);
        assertTrue(v.parts().stream().allMatch(p -> p.alpha(200, 150) > 0));
        var other = visual(false); other.move(0, 100, 100);
        for (int t = 1; t <= 200; t++) other.advance(t);
        assertEquals(v.parts().size(), other.parts().size());
        for (int i = 0; i < v.parts().size(); i++) assertEquals(v.parts().get(i).createdMs(), other.parts().get(i).createdMs(), 1e-6);
    }
    @Test void connectedUsesNativeTextureSpacingClearGapAndFiveHundredLinearFade() {
        var v = visual(true); assertFalse(v.disjoint()); assertEquals(500, v.fadeDuration());
        assertEquals(10, v.interval());
        v.move(0, 0, 0); v.move(100, 100, 0);
        assertEquals(8, v.parts().size());
        assertEquals(10, v.parts().getFirst().x()); assertEquals(80, v.parts().getLast().x());
        assertEquals(.5, v.parts().getFirst().alpha(350, 500));
        v.advance(599); assertFalse(v.parts().isEmpty()); v.advance(600); assertTrue(v.parts().isEmpty());
    }
    @Test void connectedCentersEvenWhenCursorCentreIsFalseAndDisjointUsesTopLeft() {
        var config = new Cursor(false, true, true, true);
        assertTrue(new LegacyCursorVisual(config, true, true, 40).trailCentered());
        assertFalse(new LegacyCursorVisual(config, true, false, 40).trailCentered());
        assertTrue(new LegacyCursorVisual(config, false, true, 40).disjoint()); // no cursor provider
    }
    @Test void partScaleIsCapturedAtBirthWhileRotationRemainsLive() {
        var v = visual(true); input(v, 0, true, true);
        v.move(100, 200, 100); var old = v.parts().getFirst(); assertEquals(1.3, old.scale(), 1e-6);
        input(v, 200, false, false); v.move(300, 300, 100);
        assertEquals(1, v.parts().getLast().scale()); assertEquals(1.3, old.scale(), 1e-6);
        assertEquals(90, v.trailRotation(2500)); assertEquals(180, v.trailRotation(5000));
        var fixed = new LegacyCursorVisual(new Cursor(true, true, true, false), true, true, 40);
        assertEquals(0, fixed.trailRotation(2500));
    }
    @Test void rawInputKeepsSmallCurvesIntegerInputResamplesAndExtremeMovementIsBounded() {
        var v = visual(true); v.move(0, .25, 0);
        v.move(10, 3.25, 0); // initial HD detection uses resampler once before raw bypass
        v.move(20, 25.25, 10); assertFalse(v.parts().isEmpty());
        v.move(30, 1e9, 0); assertEquals(LegacyCursorVisual.MAX_PARTS, v.parts().size());
        for (int t = 31; t < 10000; t++) v.move(t, 1e9 + t * 1000, Math.sin(t) * 200);
        assertTrue(v.parts().size() <= LegacyCursorVisual.MAX_PARTS);
        var slow = visual(true); slow.move(0, 0, 0); slow.move(1, 1, 0); assertTrue(slow.parts().isEmpty());
        slow.move(2, 40, 0); assertFalse(slow.parts().isEmpty());
    }
    @Test void missingAssetsFallbackAndRewoundHistoryIsCleared() {
        var fallback = new LegacyCursorVisual(Cursor.defaults(), false, false, 0);
        input(fallback, 0, true, true); fallback.move(100, 200, 100);
        assertTrue(fallback.parts().isEmpty()); assertEquals(0, fallback.rotation(2500));
        assertEquals(1.3, fallback.cursor(100).scale(), 1e-6);
        var v = visual(true); input(v, 0, true, true); v.move(100, 200, 100); v.advance(20);
        assertTrue(v.parts().isEmpty()); assertFalse(v.positioned()); assertEquals(1, v.expandedScale(20));
    }
}
