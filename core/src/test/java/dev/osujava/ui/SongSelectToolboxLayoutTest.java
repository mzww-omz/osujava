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
    @Test void selectionSpacingKeepsFractionalCoordinatesFromThe480HighCanvas() {
        var layout = new SongSelectToolboxLayout(1366, 768, true, new EnumMap<>(Image.class), new EnumMap<>(Image.class));
        assertEquals(224, layout.control(Selection.MODE).anchorX(), .0001);
        assertEquals(316.16, layout.control(Selection.MODS).anchorX(), .0001);
        assertEquals(392.96, layout.control(Selection.RANDOM).anchorX(), .0001);
        assertEquals(469.76, layout.control(Selection.OPTIONS).anchorX(), .0001);
        assertEquals(86.4, layout.control(Selection.MODE).anchorY(), .0001);
    }

    @Test void tinyBottomRetainsNativeArtworkWhileReservationAndInputStayUsable() {
        for (float height : new float[]{720,1080}) for (int density : new int[]{1,2}) {
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            images.put(Image.BOTTOM,texture(1,1,density));
            images.put(Image.MODE,texture(1150*density,540*density,density));
            var layout = new SongSelectToolboxLayout(1280,height,false,images,new EnumMap<>(Image.class));
            assertEquals(height/768/density,layout.bottomImage.height(),.001);
            assertEquals(SongSelectChrome.bottomHeight(height),layout.chrome.height(),.001);
            assertTrue(layout.importAction.y() > layout.chrome.height());
            assertTrue(layout.back.height() >= 90*height/768);
            assertFalse(layout.control(Selection.MODE).interaction().empty());
        }
    }

    @Test void oversizedHoverAndMismatchedDensityDoNotRescaleNormalOrExpandInput() {
        var images = new EnumMap<Image,SkinTexture>(Image.class);
        images.put(Image.MODE,texture(184,180,2));
        images.put(Image.MODE_OVER,texture(1200,700,2));
        var layout = new SongSelectToolboxLayout(1280,720,false,images,new EnumMap<>(Image.class));
        var control = layout.control(Selection.MODE);
        assertEquals(92*720f/768,control.normal().image().width());
        assertEquals(600*720f/768,control.hover().image().width());
        assertEquals(control.slot().x(), control.interaction().x(), .0001);
        assertEquals(control.slot().y(), control.interaction().y(), .0001);
        assertEquals(control.slot().width(), control.interaction().width(), .0001);
        assertEquals(control.slot().height(), control.interaction().height(), .0001);
        assertFalse(control.interaction().contains(800,300));
    }
    @Test void commonCanvasesPreserveBaselineAspectAndAuxiliaryPriorityAtEveryResolution() {
        for (int[] size : new int[][]{{1280,720},{1920,1080},{2560,1440},{960,720}}) for (int density : new int[]{1,2}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            images.put(Image.BOTTOM,texture(1366*density,90*density,density));
            for (var action : Selection.values()) images.put(action.normal,texture((int)action.logicalWidth*density,90*density,density));
            var l = new SongSelectToolboxLayout(ui.width(),ui.height(),false,images,new EnumMap<>(Image.class));
            float end = l.back.width();
            for (var action : Selection.values()) {
                var c = l.control(action);
                assertEquals(l.baseline,c.slot().y()); assertEquals(l.controlHeight,c.slot().height());
                assertEquals(end,c.slot().x(),.001);
                assertEquals((int)action.logicalWidth/90f,c.normal().image().width()/c.normal().image().height(),.001);
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
            var l = new SongSelectToolboxLayout(1280,720,false,images,metrics);
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
            assertTrue(l.importAction.y() > l.chrome.height(),"Local Import must clear composite decoration");
        }
    }
    @Test void transparentNormalUsesHoverBodyOnlyForInputAndPreservesBothRawOrigins() {
        var images = new EnumMap<Image,SkinTexture>(Image.class);
        var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
        images.put(Image.RANDOM,texture(1,1,1));
        images.put(Image.RANDOM_OVER,texture(132,180,2));
        metrics.put(Image.RANDOM,new SelectionAssetBounds(SelectionAssetBounds.Rect.EMPTY,SelectionAssetBounds.Rect.EMPTY));
        var body = new SelectionAssetBounds.Rect(14,50,51,40);
        metrics.put(Image.RANDOM_OVER,new SelectionAssetBounds(body,body));
        var l = new SongSelectToolboxLayout(1280,720,false,images,metrics);
        var c = l.control(Selection.RANDOM);
        assertEquals(c.hover().content(),c.interaction());
        assertEquals(c.slot().x(),c.hover().image().x());
        assertEquals(0,c.hover().image().y());
        assertEquals(66*720f/768,c.hover().image().width());
        assertEquals(90*720f/768,c.hover().image().height());
        assertEquals(1*720f/768,c.normal().image().width());
        assertNull(SongSelectAction.bottom(c.slot().x()+2,10,l));
        assertEquals(SongSelectAction.RANDOM,SongSelectAction.bottom(c.interaction().x()+1,c.interaction().y()+1,l));
    }
    @Test void legacyTopLeftAnchorAndBackBodyPreserveTransparentOvershoot() {
        var images = new EnumMap<Image,SkinTexture>(Image.class);
        images.put(Image.MODE,texture(92,85,1)); images.put(Image.BACK,texture(174,90,1));
        var body = new SongSelectBodyBounds(.05f,.03f,.9f,.83f);
        var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
        var backRect = new SelectionAssetBounds.Rect(body.left()*174,body.bottom()*90,body.width()*174,body.height()*90);
        metrics.put(Image.BACK,new SelectionAssetBounds(backRect,backRect));
        var l = new SongSelectToolboxLayout(1280,720,true,images,metrics);
        var c = l.control(Selection.MODE);
        assertEquals(86.4f*720f/768,c.anchorY());
        assertEquals(c.anchorY(),c.normal().image().y()+c.normal().image().height());
        assertEquals(l.baseline,l.backImage.y());
        assertEquals(174*720f/768,l.backImage.width());
        assertEquals(90*720f/768,l.backImage.height());
        assertEquals(l.baseline+l.backImage.height()*body.bottom(),l.backInteraction.y());
        assertEquals(l.backInteraction.width(),l.backImage.width()*body.width(),.001);
    }

    @Test void nativeMarginsAndUnusualAspectRatiosNeverChangeDrawOriginsOrScale() {
        for (boolean legacy : new boolean[]{true,false}) for (int[] size : new int[][]{{7,300},{900,12},{143,133}}) {
            SongSelectToolboxLayout previous = null;
            for (int density : new int[]{1,2}) {
                var images = new EnumMap<Image,SkinTexture>(Image.class);
                var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
                for (var action : Selection.values()) {
                    images.put(action.normal,texture(size[0]*density,size[1]*density,density));
                    metrics.put(action.normal,SelectionAssetBounds.detect(size[0]*density,size[1]*density,density,
                            action.logicalWidth,legacy ? 86.4f : 90,legacy,
                            (x,y) -> x >= 3*density && x < 6*density && y >= 5*density && y < 10*density ? 255 : 0));
                }
                var l = new SongSelectToolboxLayout(1280,720,legacy,images,metrics);
                for (var action : Selection.values()) {
                    var c = l.control(action);
                    assertEquals(c.anchorX(),c.normal().image().x());
                    assertEquals(legacy ? c.anchorY()-size[1]*720f/768 : c.anchorY(),c.normal().image().y(),.001);
                    assertEquals(size[0]*720f/768,c.normal().image().width(),.001);
                    assertEquals(size[1]*720f/768,c.normal().image().height(),.001);
                    if (previous != null) assertEquals(previous.control(action),c,"SD/HD geometry must agree, including alpha mapping");
                }
                previous = l;
            }
        }
    }

    @Test void fixedNavigationOriginIsIndependentOfBackArtworkAndProvider() {
        for (float width : new float[]{960,1280}) for (int[] size : new int[][]{{1,1},{174,90},{272,91},{900,200}}) {
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            images.put(Image.BACK,texture(size[0],size[1],1));
            var l = new SongSelectToolboxLayout(width,720,false,images,new EnumMap<>(Image.class));
            assertEquals((width == 960 ? 192 : 224)*720f/768,l.control(Selection.MODE).anchorX());
            assertEquals(0,l.backImage.x()); assertEquals(0,l.backImage.y());
            assertEquals(size[0]*720f/768,l.backImage.width());
            assertTrue(l.backInteraction.x()+l.backInteraction.width() <= l.control(Selection.MODE).anchorX());
        }
    }

    @Test void fullyTransparentValidAssetsRetainRawArtworkButHaveNoInteraction() {
        var images = new EnumMap<Image,SkinTexture>(Image.class);
        var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
        for (var image : new Image[]{Image.MODE,Image.MODE_OVER,Image.BACK}) {
            images.put(image,texture(200,120,1));
            metrics.put(image,SelectionAssetBounds.detect(200,120,1,224,90,false,(x,y)->0));
        }
        var l = new SongSelectToolboxLayout(1280,720,false,images,metrics);
        assertFalse(l.control(Selection.MODE).normal().image().empty());
        assertTrue(l.control(Selection.MODE).interaction().empty());
        assertFalse(l.backImage.empty()); assertTrue(l.backInteraction.empty());
    }

    @Test void backDecorationAndAsymmetricMarginsAreDrawnWithoutMovingTheButtonBody() {
        for (int density : new int[]{1,2}) {
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
            images.put(Image.BACK,texture(400*density,150*density,density));
            metrics.put(Image.BACK,SelectionAssetBounds.detect(400*density,150*density,density,224,90,false,
                    (x,y) -> x >= 20*density && x < 120*density && y >= 80*density && y < 130*density
                            || x >= 300*density && y < 20*density ? 255 : 0));
            var l = new SongSelectToolboxLayout(1280,720,false,images,metrics);
            assertEquals(new SongSelectToolboxLayout.Bounds(0,0,375,140.625f),l.backImage);
            assertEquals(new SongSelectToolboxLayout.Bounds(18.75f,18.75f,93.75f,46.875f),l.backInteraction);
            assertNull(SongSelectAction.bottom(310,130,l));
            assertEquals(SongSelectAction.BACK,SongSelectAction.bottom(30,30,l));
        }
    }

    @Test void realSkinDimensionsShareOneLayoutAndPreserveIntentionalRelativeY() {
        for (int profile=0;profile<3;profile++) {
            var images = new EnumMap<Image,SkinTexture>(Image.class);
            int[][] sizes = profile == 0 ? new int[][]{{1143,930},{1,1},{1,1},{1,1}}
                    : profile == 1 ? new int[][]{{1150,540},{78,90},{78,90},{78,90}}
                    : new int[][]{{92,85},{77,86},{77,86},{77,86}};
            int i=0;
            for (var action : Selection.values()) {
                images.put(action.normal,texture(sizes[i][0],sizes[i][1],1)); i++;
            }
            images.put(Image.MODE_OVER,texture(profile == 0 ? 82 : 93,profile == 0 ? 110 : 90,1));
            var l = new SongSelectToolboxLayout(1280,720,profile == 2,images,new EnumMap<>(Image.class));
            assertEquals(210,l.control(Selection.MODE).anchorX());
            if (profile == 0) {
                assertEquals(103.125f,l.control(Selection.MODE).hover().image().height());
                assertEquals(0,l.control(Selection.MODE).hover().image().y());
                assertEquals(.9375f,l.control(Selection.MODS).normal().image().height());
            }
            if (profile == 2) {
                assertEquals(1.3125f,l.control(Selection.MODE).normal().image().y());
                assertEquals(.375f,l.control(Selection.MODS).normal().image().y());
            }
        }
    }
}
