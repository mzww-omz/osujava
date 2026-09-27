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
    @Test void bottomStretchesOnlyWidthAndAllActionsShareBaselineAndHeight() {
        for (float height : new float[]{720,1080}) for (int density : new int[]{1,2}) {
            var model = SongSelectChrome.bottom(1280,height,texture(1366*density,90*density,density),
                    texture(174*density,90*density,density),texture(77*density,86*density,density));
            assertEquals(1280, model.skinBounds().width());
            assertEquals(90 * height / 768, model.skinBounds().height(), .001);
            assertEquals(0, model.skinBounds().y());
            for (var slot : new SongSelectChrome.Bounds[]{model.back(),model.random(),model.importAction()}) {
                assertEquals(model.controlBaseline(),slot.y());
                assertEquals(model.actionHeight(),slot.height());
            }
            var back = SongSelectChrome.actionImage(model.back(),texture(174,90,1));
            var random = SongSelectChrome.actionImage(model.random(),texture(154,172,2));
            assertEquals(back.y()+back.height(),random.y()+random.height(),.001);
            assertEquals(174f/90,back.width()/back.height(),.001);
            assertEquals(77f/86,random.width()/random.height(),.001);
            assertTrue(random.width() <= model.random().width());
            assertTrue(model.cookie().y() < 0);
            assertTrue(model.cookie().y()+model.cookie().height() > model.skinBounds().height());
        }
    }
    @Test void transparentPaddingCannotMakeRandomVisuallyTallerThanBack() {
        var back = texture(174,90,1); var random = texture(154,172,2);
        var backBody = new SongSelectBodyBounds(0, .03f, 1, .83f);
        var randomBody = new SongSelectBodyBounds(0, .01f, 1, .91f);
        var model = SongSelectChrome.bottom(1280,720,texture(1366,90,1),back,random,backBody,randomBody);
        var backImage = SongSelectChrome.actionImage(model.back(),back,backBody);
        var randomImage = SongSelectChrome.actionImage(model.random(),random,randomBody);
        assertEquals(model.controlBaseline(),backImage.y() + backBody.bottom()*backImage.height(),.001);
        assertEquals(model.controlBaseline(),randomImage.y() + randomBody.bottom()*randomImage.height(),.001);
        assertEquals(model.actionHeight(),backImage.height()*backBody.height(),.001);
        assertEquals(model.actionHeight(),randomImage.height()*randomBody.height(),.001);
        assertEquals(174f/90,backImage.width()/backImage.height(),.001);
        assertTrue(backImage.width() <= model.back().width() + .001);
        assertTrue(randomImage.width() <= model.random().width() + .001);
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
        assertEquals(658,transparent.carouselTop());
        assertEquals(84,transparent.bottom());
    }
    @Test void tinyChromeStaysAnAssetWithIntentionalActionOverlap() {
        var model = SongSelectChrome.bottom(1280,720,texture(1,1,1),null,null);
        assertEquals(720f/768,model.skinBounds().height());
        assertEquals(model.actionHeight()-model.skinBounds().height(),model.intentionalOverlap());
        assertEquals(SongSelectAction.RANDOM,SongSelectAction.bottom(340,19,model));
        assertNull(SongSelectAction.bottom(340,model.actionHeight(),model));
        assertTrue(SongSelectChrome.procedural(null,Image.TOP));
        assertTrue(SongSelectChrome.procedural(null,Image.BOTTOM));
    }
}
