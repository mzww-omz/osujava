package dev.osujava.ui;

import com.badlogic.gdx.graphics.Texture;
import dev.osujava.skin.*;
import dev.osujava.skin.SongSelectSkinAssets.*;
import dev.osujava.ui.theme.UiLayout;
import java.nio.file.Path;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectToolboxLayoutTest {
    private SkinTexture texture(int width, int height, int density) {
        return new SkinTexture(new Texture() {
            @Override public int getWidth() { return width; }
            @Override public int getHeight() { return height; }
        },new SkinAssetResolver.AssetFile(Path.of("fixture.png"),density));
    }
    @Test void commonCanvasesPreserveBaselineAspectAndAuxiliaryPriorityAtEveryResolution() {
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440},{960,720}}) for (int density : new int[]{1,2}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            images.put(Image.BOTTOM,texture(1366*density,90*density,density));
            for (var action : Selection.values()) images.put(action.normal,texture((int)action.logicalWidth*density,90*density,density));
            var l = new SongSelectToolboxLayout(ui.width(),ui.height(),false,images,new EnumMap<>(Image.class),SongSelectBodyBounds.FULL);
            float end = l.back.width();
            for (var action : Selection.values()) {
                var c = l.control(action);
                assertEquals(l.baseline,c.slot().y()); assertEquals(l.controlHeight,c.slot().height());
                assertEquals(end,c.slot().x(),.001);
                assertEquals(action.logicalWidth/90,c.normal().image().width()/c.normal().image().height(),.001);
                assertTrue(c.interaction().width() <= c.slot().width());
                assertTrue(c.slot().x()+c.slot().width() < l.cookie.x());
                end += c.slot().width();
            }
            assertEquals(90*ui.height()/768,l.chrome.height(),.001);
            assertTrue(l.importAction.height() < l.controlHeight*.5f);
            assertTrue(l.importAction.x() >= end); assertTrue(l.importAction.x()+l.importAction.width() < l.cookie.x());
            assertTrue(l.cookie.y() < 0); assertTrue(l.cookie.y()+l.cookie.height() > l.chrome.height());
        }
    }
    @Test void compositeNormalAndIndependentHoverKeepNativeSizeWithoutHugeHitbox() {
        for (int density : new int[]{1,2}) {
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
            images.put(Image.MODE,texture(1143*density,930*density,density));
            images.put(Image.MODE_OVER,texture(82*density,110*density,density));
            metrics.put(Image.MODE,new SelectionAssetBounds(new SelectionAssetBounds.Rect(0,0,1143,845),SelectionAssetBounds.Rect.EMPTY));
            metrics.put(Image.MODE_OVER,new SelectionAssetBounds(new SelectionAssetBounds.Rect(47,45,35,50),new SelectionAssetBounds.Rect(47,45,35,45)));
            var l = new SongSelectToolboxLayout(1280,720,false,images,metrics,SongSelectBodyBounds.FULL);
            var c = l.control(Selection.MODE);
            assertEquals(1143*720f/768,c.normal().image().width());
            assertEquals(930*720f/768,c.normal().image().height());
            assertEquals(c.normal().image().x(),c.hover().image().x()); assertEquals(0,c.anchorY());
            assertEquals(35*720f/768,c.interaction().width());
            assertFalse(c.interaction().contains(900,400));
            assertEquals(SongSelectAction.MODE,SongSelectAction.bottom(c.interaction().x()+1,c.interaction().y()+1,l));
            assertTrue(l.transparentOvershoot > 700);
            assertTrue(l.status.y() > l.chrome.height(),"Auxiliary text must clear authored composite status artwork");
            assertTrue(l.debug.y() > l.chrome.height());
        }
    }
    @Test void shadowPaddingAndTransparentNormalsUseSameVisibleBodyForDrawingAndInput() {
        var images = new EnumMap<Image,SkinTexture>(Image.class);
        var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
        images.put(Image.RANDOM,texture(1,1,1));
        images.put(Image.RANDOM_OVER,texture(132,180,2));
        metrics.put(Image.RANDOM,new SelectionAssetBounds(SelectionAssetBounds.Rect.EMPTY,SelectionAssetBounds.Rect.EMPTY));
        var body = new SelectionAssetBounds.Rect(14,50,51,40);
        metrics.put(Image.RANDOM_OVER,new SelectionAssetBounds(body,body));
        var l = new SongSelectToolboxLayout(1280,720,false,images,metrics,SongSelectBodyBounds.FULL);
        var c = l.control(Selection.RANDOM);
        assertEquals(c.hover().content(),c.interaction());
        assertNull(SongSelectAction.bottom(c.slot().x()+2,10,l));
        assertEquals(SongSelectAction.RANDOM,SongSelectAction.bottom(c.interaction().x()+1,c.interaction().y()+1,l));
    }
    @Test void legacyTopLeftAnchorAndBackBodyPreserveTransparentOvershoot() {
        var images = new EnumMap<Image,SkinTexture>(Image.class);
        images.put(Image.MODE,texture(92,85,1)); images.put(Image.BACK,texture(174,90,1));
        var body = new SongSelectBodyBounds(.05f,.03f,.9f,.83f);
        var l = new SongSelectToolboxLayout(1280,720,true,images,new EnumMap<>(Image.class),body);
        var c = l.control(Selection.MODE);
        assertEquals(87*720f/768,c.anchorY());
        assertEquals(c.anchorY(),c.normal().image().y()+c.normal().image().height());
        assertTrue(l.backImage.y() < l.baseline);
        assertEquals(l.baseline,l.backInteraction.y());
        assertEquals(l.backInteraction.width(),l.backImage.width()*body.width(),.001);
    }
}
