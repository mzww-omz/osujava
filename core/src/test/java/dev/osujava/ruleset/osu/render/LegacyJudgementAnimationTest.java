package dev.osujava.ruleset.osu.render;

import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import org.junit.jupiter.api.Test;
import static dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyJudgementAnimationTest {
    @Test void mapsNormalResultsAndOnlyExplicitTailEvents() {
        assertEquals("hit300", Result.from(Judgement.HIT300).image);
        assertEquals("hit100", Result.from(Judgement.HIT100).image);
        assertEquals("hit50", Result.from(Judgement.HIT50).image);
        assertEquals("hit0", Result.from(Judgement.MISS).image);
        assertEquals(Result.SLIDER_END_MISS, Result.from(visual(Judgement.MISS, JudgementVisual.Kind.SLIDER_TAIL)));
        assertEquals(Result.SLIDER_TAIL_HIT, Result.from(visual(Judgement.HIT300, JudgementVisual.Kind.SLIDER_TAIL)));
        assertEquals(Result.MISS, Result.from(visual(Judgement.MISS, JudgementVisual.Kind.SLIDER_HEAD)));
    }
    @Test void sixtyFpsDoesNotLoopAndHoldsFinalFrameAcrossSeeks() {
        assertEquals(0, frame(-1, 4)); assertEquals(0, frame(16, 4));
        assertEquals(1, frame(FRAME_MS, 4)); assertEquals(2, frame(34, 4));
        assertEquals(3, frame(60, 4)); assertEquals(3, frame(1100, 4));
        assertEquals(0, frame(0, 4)); assertEquals(0, frame(1100, 1));
    }
    @Test void oldHitFadeAndExactScaleSequence() {
        assertEquals(0, alpha(-1)); assertEquals(0, alpha(0)); assertEquals(.5, alpha(60));
        assertEquals(1, alpha(120)); assertEquals(1, alpha(500)); assertEquals(.5, alpha(800));
        assertEquals(0, alpha(1100));
        assertEquals(.6, hitScale(0, 1)); assertEquals(.85, hitScale(48, 1), 1e-12);
        assertEquals(1.1, hitScale(96, 1)); assertEquals(1.1, hitScale(120, 1));
        assertEquals(1, hitScale(132, 1), 1e-12);
        assertEquals(.95, hitScale(144, 1)); assertEquals(.975, hitScale(156, 1));
        assertEquals(1, hitScale(168, 1)); assertEquals(1, hitScale(1100, 1));
        assertEquals(1.05, hitScale(168, 1.05));
    }
    @Test void staticMissUsesQuadraticScaleAndVersionGreaterThanOneMovement() {
        assertEquals(1.6, old(Result.MISS, 0, 1, 1, 4).scale());
        assertEquals(1.45, old(Result.MISS, 50, 1, 1, 4).scale(), 1e-12);
        assertEquals(1, old(Result.MISS, 100, 1, 1, 4).scale());
        assertEquals(0, old(Result.MISS, 800, 1, 1, 4).y());
        assertEquals(-5, old(Result.MISS, 0, 1, 1.1, 4).y());
        assertEquals(15, old(Result.MISS, 550, 1, 2, 4).y());
        assertEquals(75, old(Result.MISS, 1100, 1, 2, 4).y());
        assertEquals(2, old(Result.MISS, 60, 1, 2, 4).rotation());
        assertEquals(4, old(Result.MISS, 120, 1, 2, 4).rotation());
        assertEquals(5, old(Result.MISS, 610, 1, 2, 4).rotation());
        assertEquals(8, old(Result.MISS, 1100, 1, 2, 4).rotation());
    }
    @Test void animatedAssetsSkipEveryOldTransformButTemporaryAlwaysScales() {
        for (Result result : new Result[]{Result.GREAT, Result.MISS, Result.SLIDER_END_MISS})
            for (double age : new double[]{0, 60, 120, 500, 800, 1100}) {
                var t = old(result, age, 2, 2, 8);
                assertEquals(1, t.scale()); assertEquals(0, t.y()); assertEquals(0, t.rotation());
                assertEquals(alpha(age), t.alpha());
            }
        assertEquals(.6, temporary(0).scale()); assertEquals(1.05, temporary(168).scale());
        assertEquals(1, main(60, 2).scale());
    }
    @Test void newMainAndTemporaryOpacityIncludeParentFade() {
        assertEquals(.9, main(0, 1).scale()); assertEquals(.975, main(550, 1).scale(), 1e-12);
        assertEquals(1.05, main(1100, 1).scale());
        assertEquals(0, temporary(0).alpha());
        assertEquals(1d / 6, temporary(40).alpha(), 1e-12);
        assertEquals(.5 * (1 - 80d / 300), temporary(120).alpha(), 1e-12);
        assertEquals(0, temporary(340).alpha()); assertEquals(0, temporary(800).alpha());
    }
    @Test void tailMissAndLegacyTailPointHaveTheirOwnTransforms() {
        assertEquals(1.2, old(Result.SLIDER_END_MISS, 0, 1, 2, 8).scale());
        assertEquals(1.15, old(Result.SLIDER_END_MISS, 50, 1, 2, 8).scale());
        assertEquals(.5, old(Result.SLIDER_END_MISS, 550, 1, 2, 8).alpha());
        assertEquals(0, old(Result.SLIDER_END_MISS, 850, 1, 2, 8).alpha());
        assertEquals(-7.5, sliderPoint(150).y()); assertEquals(-10, sliderPoint(300).y());
        assertEquals(.5, sliderPoint(330).alpha()); assertEquals(0, sliderPoint(360).alpha());
    }
    @Test void randomParametersStayStableForLifetimeAndRecreatedSnapshots() {
        var v = visual(Judgement.MISS, JudgementVisual.Kind.CIRCLE);
        double rotation = missRotation(v);
        assertTrue(rotation >= -8.6 && rotation < 8.6);
        for (int i = 0; i < 100; i++) assertEquals(rotation, missRotation(v));
        assertEquals(rotation, missRotation(visual(Judgement.MISS, JudgementVisual.Kind.CIRCLE)));
        assertEquals(particles(v), particles(v)); assertEquals(PARTICLE_COUNT, particles(v).size());
        for (var p : particles(v)) {
            assertTrue(p.distance() >= 0 && p.distance() < .5);
            assertTrue(p.duration() >= 1600d / 3 && p.duration() < 1600);
            assertTrue(p.direction() >= 0 && p.direction() < Math.PI * 2);
            assertTrue(p.progress(0) > 0); assertEquals(0, p.alpha(1500));
            double x = p.x(60); p.x(800); assertEquals(x, p.x(60));
        }
        assertEquals(.5, baseScale(32)); // Native pixels / density, then this scale and viewport.
    }
    private JudgementVisual visual(Judgement result, JudgementVisual.Kind kind) {
        return new JudgementVisual(256, 192, 32, result, 1000, kind, 0);
    }
}
