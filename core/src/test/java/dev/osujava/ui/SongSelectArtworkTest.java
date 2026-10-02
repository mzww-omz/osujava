package dev.osujava.ui;

import com.badlogic.gdx.graphics.Texture;
import dev.osujava.skin.SkinAssetResolver;
import dev.osujava.skin.SongSelectSkinAssets.SkinTexture;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectArtworkTest {
    private SkinTexture texture(int width, int height, int density) {
        return new SkinTexture(new Texture() {
            @Override public int getWidth() { return width; }
            @Override public int getHeight() { return height; }
        }, new SkinAssetResolver.AssetFile(Path.of("fixture.png"), density));
    }

    @Test void seoulCanvasKeepsItsNativeScaleAndCentreLeftAnchor() {
        // Recorded Seoul canvas dimensions; no third-party artwork is embedded in this test.
        var sd = SongSelectArtwork.card(500, 360, 720, 1, texture(699, 103, 1));
        var hd = SongSelectArtwork.card(500, 360, 720, 1, texture(1398, 206, 2));
        assertEquals(sd, hd);
        assertEquals(500, sd.x());
        assertEquals(360, sd.y() + sd.height() / 2);
        assertEquals(655.3125, sd.width());
        assertEquals(96.5625, sd.height());
        var score = SongSelectArtwork.card(18, 400, 720, .55f, texture(699, 103, 1));
        assertEquals(sd.width() * .55f, score.width(), .001);
        assertEquals(sd.height() * .55f, score.height(), .001);
        assertEquals(400, score.y() + score.height() / 2, .001);
    }

    @Test void paddingAndUnusualCanvasesNeverStretchToTheNavigationRectangle() {
        for (int[] size : new int[][]{{1,1}, {700,117}, {1000,200}, {80,300}}) {
            for (float height : new float[]{720, 768, 1080}) {
                var image = texture(size[0], size[1], 1);
                var bounds = SongSelectArtwork.card(100, 200, height, 1, image);
                assertEquals(size[0] / (float) size[1], bounds.width() / bounds.height(), .001);
                assertEquals(size[0] * height / 768, bounds.width(), .001);
                assertEquals(size[1] * height / 768, bounds.height(), .001);
            }
        }
    }

    @Test void rowHitUsesNativeCanvasInsteadOfNavigationPitchAtEveryScaleAndDensity() {
        for (float scale : new float[]{1, 768f / 720, 800f / 720, 1.5f}) {
            var sd = SongSelectArtwork.row(100, 200, 900, 72 * scale, texture(700, 117, 1));
            var hd = SongSelectArtwork.row(100, 200, 900, 72 * scale, texture(1401, 235, 2));
            assertEquals(sd, hd); // Odd HD pixels are cropped by integer logical dimensions.
            var row = new SongSelectRow(0, 0, "", true, false, 100, 200, 900, 72 * scale,
                    0, 1, 0, 100, 200, "row", false, 0, sd);
            assertTrue(sd.height() > row.height());
            assertTrue(row.contains(sd.x(), sd.y() + sd.height()), "Native top-left edge is included");
            assertFalse(row.contains(sd.x(), sd.y()), "Native bottom edge is excluded");
            assertFalse(row.contains(sd.x() + sd.width(), 236 * scale));
            assertTrue(row.contains(150, sd.y() + 1), "Visible artwork outside navigation body is clickable");
            var geometry = SongSelectLayout.row(row, 0, 100, 200, 1000, 210, 260, false, false);
            assertEquals(Math.min(sd.width(), 900), geometry.hit().width(), .001);
            assertEquals(210, geometry.hit().y());
            assertEquals(50, geometry.hit().height());
            assertNull(SongSelectRow.hit(java.util.List.of(row), 150, 209, 210, 260));
        }
    }

    @Test void narrowAndMissingRowArtKeepTheirActualBoundsIncludingZeroLogicalHdSize() {
        var small = SongSelectArtwork.row(100, 200, 900, 72, texture(80, 300, 1));
        assertEquals(75, small.width());
        assertEquals(281.25f, small.height());
        var missing = SongSelectArtwork.row(100, 200, 900, 72, null);
        assertEquals(new SongSelectChrome.Bounds(100, 200, 900, 72), missing);
        var zero = SongSelectArtwork.row(100, 200, 900, 72, texture(1, 1, 2));
        var row = new SongSelectRow(0, 0, "", true, false, 100, 200, 900, 72,
                0, 1, 0, 100, 200, "row", false, 0, zero);
        assertFalse(row.contains(100, 236));
    }
}
