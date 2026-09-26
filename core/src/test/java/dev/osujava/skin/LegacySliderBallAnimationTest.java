package dev.osujava.skin;

import dev.osujava.gameplay.OsuObjectGeometry;
import dev.osujava.ui.PlayfieldViewport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacySliderBallAnimationTest {
    @Test
    void velocityControlsFrameDurationWithSixtyFpsMinimum() {
        assertEquals(1000 / 30.0, LegacySliderBallAnimation.frameDurationMs(0.075), 1e-10);
        assertEquals(1000 / 60.0, LegacySliderBallAnimation.frameDurationMs(0.15), 1e-10);
        assertEquals(1000 / 60.0, LegacySliderBallAnimation.frameDurationMs(1.5), 1e-10);
        assertEquals(1000 / 60.0, LegacySliderBallAnimation.frameDurationMs(Double.NaN));
    }

    @Test
    void framesAdvanceInOrderAndLoopFromAbsoluteTimeWithoutDrift() {
        // 0.05 px/ms gives exactly 50 ms/frame. The animation origin is start - preempt.
        for (int frame = 0; frame < 30; frame++) {
            assertEquals(frame % 3, LegacySliderBallAnimation.frameIndex(3, 400 + frame * 50, 1000, 600, 0.05));
        }
        assertEquals(0, LegacySliderBallAnimation.frameIndex(3, 449.999, 1000, 600, 0.05));
        assertEquals(1, LegacySliderBallAnimation.frameIndex(3, 450, 1000, 600, 0.05));
        assertEquals(2, LegacySliderBallAnimation.frameIndex(3, 500, 1000, 600, 0.05));
        // A seek back returns the same frame; no update/delta history is involved.
        assertEquals(1, LegacySliderBallAnimation.frameIndex(3, 450, 1000, 600, 0.05));
        assertEquals(0, LegacySliderBallAnimation.frameIndex(3, 0, 1000, 600, 0.05));
    }

    @Test
    void fasterSlidersAnimateFasterButMissingAndStaticFramesRemainSafe() {
        assertEquals(0, LegacySliderBallAnimation.frameIndex(10, 420, 1000, 600, 0.05));
        assertEquals(1, LegacySliderBallAnimation.frameIndex(10, 420, 1000, 600, 0.15));
        assertEquals(0, LegacySliderBallAnimation.frameIndex(1, 999999, 1000, 600, 0.05));
        assertEquals(-1, LegacySliderBallAnimation.frameIndex(0, 1000, 1000, 600, 0.15));
    }

    @Test
    void nativeSizeDensityCircleSizeAndViewportAreAppliedWithoutDistortion() {
        for (double cs : new double[]{2, 5, 8}) {
            for (var viewport : new PlayfieldViewport[]{PlayfieldViewport.fit(512, 384), PlayfieldViewport.fit(1100, 720)}) {
                double radius = OsuObjectGeometry.radius(cs);
                var normal = LegacySliderBallAnimation.spriteSize(100, 60, 1, radius, viewport.scale());
                var retina = LegacySliderBallAnimation.spriteSize(200, 120, 2, radius, viewport.scale());
                assertEquals(normal, retina);
                assertEquals(100 * radius / 64 * viewport.scale(), normal.width(), 1e-5);
                assertEquals(100 / 60.0, normal.width() / normal.height(), 1e-6);
            }
        }
    }

    @Test
    void oversizedImagesAreCentreCroppedPerAxisLikeLazer() {
        var size = LegacySliderBallAnimation.spriteSize(1000, 200, 2, 64, 1);
        assertEquals(384, size.width());
        assertEquals(100, size.height());
        assertEquals(0.116, size.u(), 1e-6);
        assertEquals(0.884, size.u2(), 1e-6);
        assertEquals(1, size.v());
        assertEquals(0, size.v2());
    }
}
