package dev.osujava.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.JsonReader;
import dev.osujava.skin.SelectionAssetBounds;
import dev.osujava.skin.SkinAssetResolver;
import dev.osujava.skin.SongSelectSkinAssets.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Portable measured real-skin geometry, without redistributing skin artwork or needing OpenGL.
 * The opt-in production harness verifies original PNG hashes and renders those same originals. */
class SongSelectSkinCorpusTest {
    @Test void pinnedRealCanvasesKeepNativeOriginsAndHoverSpriteInteractionAtEveryDisplayProfile() throws Exception {
        var input = getClass().getResourceAsStream("/songselect-skin-geometry.json");
        assertNotNull(input);
        var skins = new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        var ids = new HashSet<String>();
        for (var skin : skins) {
            ids.add(skin.getString("id"));
            boolean legacy = skin.getFloat("effective_version") < 2;
            for (int density : new int[]{1,2}) {
                var images = new EnumMap<Image,SkinTexture>(Image.class);
                var metrics = new EnumMap<Image,SelectionAssetBounds>(Image.class);
                for (var asset : skin.get("assets")) {
                    Image image = null;
                    for (var candidate : Image.values()) if (candidate.basename.equals(asset.getString("asset"))
                            || candidate == Image.BACK && asset.getString("asset").equals("menu-back-0")) image = candidate;
                    if (image == null || asset.getInt("density") > density) continue;
                    if (images.containsKey(image) && images.get(image).density() > asset.getInt("density")) continue;
                    var raw = asset.get("raw");
                    int width = raw.getInt(0), height = raw.getInt(1), d = asset.getInt("density");
                    images.put(image,new SkinTexture(new Texture() {
                        @Override public int getWidth() { return width; }
                        @Override public int getHeight() { return height; }
                    }, new SkinAssetResolver.AssetFile(Path.of(asset.getString("filename")),d)));
                    var alpha = asset.get("alpha_bounds");
                    var opaque = alpha.isNull() ? SelectionAssetBounds.Rect.EMPTY : new SelectionAssetBounds.Rect(
                            alpha.getFloat(0)/d,(height-alpha.getFloat(3))/d,
                            (alpha.getFloat(2)-alpha.getFloat(0))/d,(alpha.getFloat(3)-alpha.getFloat(1))/d);
                    var content = asset.get("interaction_content");
                    if (content != null) metrics.put(image,new SelectionAssetBounds(opaque,new SelectionAssetBounds.Rect(
                            content.getFloat(0),content.getFloat(1),content.getFloat(2),content.getFloat(3))));
                }
                for (float height : new float[]{720,1080,1440}) {
                    float scale = height/768;
                    var layout = new SongSelectToolboxLayout(height*16/9,height,legacy,images,metrics);
                    for (var action : Selection.values()) {
                        var control = layout.control(action);
                        for (var image : new Image[]{action.normal,action.hover}) {
                            var texture = images.get(image);
                            if (texture == null) continue;
                            var artwork = image == action.normal ? control.normal() : control.hover();
                            assertEquals(texture.logicalWidth()*scale,artwork.image().width(),.001);
                            assertEquals(texture.logicalHeight()*scale,artwork.image().height(),.001);
                            assertEquals(control.anchorX(),artwork.image().x());
                            assertEquals(control.anchorY(),legacy ? artwork.image().y()+artwork.image().height() : artwork.image().y(),.001);
                        }
                        var owner = images.containsKey(action.hover) ? control.hover().image()
                                : images.containsKey(action.normal) ? control.normal().image() : control.slot();
                        assertEquals(SongSelectToolboxLayout.intersect(owner,
                                new SongSelectToolboxLayout.Bounds(0,0,height*16/9,height)),control.interaction());
                        assertFalse(control.interaction().contains(height*16/9,height/2),"Input must stop at the viewport edge");
                    }
                    assertTrue(layout.chrome.height() >= SongSelectChrome.bottomHeight(height));
                }
            }
        }
        assertEquals(java.util.Set.of("Greylooks","WhiteCat","Seoul","Default"),ids);
    }
}
