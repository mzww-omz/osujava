package dev.osujava.ui;

import com.badlogic.gdx.graphics.Texture;
import dev.osujava.skin.SkinAssetResolver;
import dev.osujava.skin.SongSelectBodyBounds;
import dev.osujava.skin.SongSelectSkinAssets.SkinTexture;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectChromeTest {
    private SkinTexture texture(int width, int height, int density) {
        return new SkinTexture(new Texture() {
            @Override public int getWidth() { return width; }
            @Override public int getHeight() { return height; }
        }, new SkinAssetResolver.AssetFile(Path.of("fixture.png"), density));
    }
    @Test void topKeepsAspectAndTopLeftOriginWithoutHeaderHeightClamp() {
        for (float height : new float[]{720,1080}) {
            var sd = SongSelectChrome.top(1280, height, texture(1366,149,1));
            var hd = SongSelectChrome.top(1280, height, texture(2732,298,2));
            assertEquals(sd, hd);
            assertEquals(height, sd.y() + sd.height(), .001);
            assertEquals(0, sd.x());
            assertEquals(1366f / 149, sd.width() / sd.height(), .001);
            assertEquals(149 * height / 768, sd.height(), .001);
            assertEquals(height, SongSelectChrome.top(1280,height,texture(1366,768,1)).height());
        }
    }
    @Test void contentStaysOutsideVisibleChromeWhileHeaderControlsKeepTheirOwnArea() {
        var greylooks = SongSelectChrome.content(720,149*720f/768,83*720f/768,90*720f/768);
        assertTrue(greylooks.rankingHeaderTop() < 720-149*720f/768);
        assertTrue(greylooks.carouselTop() < 720-83*720f/768);
        assertEquals(90*720f/768,greylooks.bottom(),.001);
        var tall = SongSelectChrome.content(720,240,160,150);
        assertEquals(472,tall.rankingHeaderTop());
        assertEquals(556,tall.carouselTop());
        assertEquals(150,tall.bottom());
        var transparent = SongSelectChrome.content(720,0,0,0);
        assertEquals(590,transparent.rankingHeaderTop());
        assertEquals(636,transparent.carouselTop());
        assertEquals(84,transparent.bottom());
    }
    @Test void artworkUsesFullViewportIndependentOfContentReservation() {
        for (float height : new float[]{720, 1080, 1440}) {
            var content = SongSelectChrome.content(height, 4096, 4096, 4096);
            var top = SongSelectChrome.topClip(1280, height);
            var bottom = SongSelectChrome.bottomClip(1280, height);
            assertEquals(height, top.y() + top.height(), .001);
            assertEquals(0, top.y(), .001);
            assertEquals(height, bottom.height(), .001);
            assertTrue(content.carouselTop() > content.bottom());
        }
    }
    @Test void oversizedAndInvalidChromeCannotConsumeTheBrowserViewport() {
        for (float height : new float[]{720, 1080, 1440}) {
            for (float depth : new float[]{0, 1, 4096, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY}) {
                var bounds = SongSelectChrome.content(height, depth, depth, depth);
                assertTrue(bounds.carouselTop() - bounds.bottom() >= height * .29f);
                assertTrue(bounds.rankingHeaderTop() > bounds.bottom());
                assertTrue(bounds.bottom() >= SongSelectChrome.bottomHeight(height));
            }
        }
    }

}
