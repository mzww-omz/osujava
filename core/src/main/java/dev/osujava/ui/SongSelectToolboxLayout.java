package dev.osujava.ui;

import dev.osujava.skin.SelectionAssetBounds;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import dev.osujava.skin.SongSelectSkinAssets.Selection;
import dev.osujava.skin.SongSelectSkinAssets.SkinTexture;
import java.util.EnumMap;

/** Shared stable canvas geometry. Artwork may overshoot; input never uses its PNG rectangle.
 * Selection canvases stay adjacent so composite skins retain their authored connections. */
final class SongSelectToolboxLayout {
    record Bounds(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return width > 0 && height > 0 && px >= x && px < x + width && py >= y && py < y + height;
        }
        boolean empty() { return width <= 0 || height <= 0; }
    }
    record Artwork(Bounds image, Bounds opaque, Bounds content) { }
    record Control(Bounds slot, Artwork normal, Artwork hover, Bounds interaction,
                   float anchorX, float anchorY) { }
    private final EnumMap<Selection, Control> controls = new EnumMap<>(Selection.class);
    final Bounds chrome, bottomImage, back, backImage, backInteraction, importAction, cookie, status, debug;
    final float baseline, controlHeight, spacing, transparentOvershoot;

    static SongSelectToolboxLayout create(float width, float height, SongSelectSkinAssets skin) {
        return create(width,height,skin,skin == null ? null : skin.get(Image.BACK));
    }
    static SongSelectToolboxLayout create(float width, float height, SongSelectSkinAssets skin, SkinTexture backFrame) {
        var images = new EnumMap<Image, SkinTexture>(Image.class);
        var metrics = new EnumMap<Image, SelectionAssetBounds>(Image.class);
        if (skin != null) for (var image : Image.values()) {
            if (skin.get(image) != null) images.put(image, skin.get(image));
            if (skin.selectionBounds(image) != null) metrics.put(image, skin.selectionBounds(image));
        }
        if (backFrame != null) {
            images.put(Image.BACK,backFrame);
            metrics.remove(Image.BACK);
            if (skin != null && skin.backBounds(backFrame) != null) metrics.put(Image.BACK,skin.backBounds(backFrame));
        }
        return new SongSelectToolboxLayout(width, height, skin != null && skin.legacySelectionAnchors(),
                images, metrics);
    }

    /** GL-free fixture entry point; production uses load-time metrics from the shared resolver. */
    SongSelectToolboxLayout(float width, float height, boolean legacy, EnumMap<Image, SkinTexture> images,
            EnumMap<Image, SelectionAssetBounds> metrics) {
        float scale = height / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
        baseline = 0; spacing = 0; controlHeight = (legacy ? SongSelectSkinAssets.LEGACY_SELECTION_HEIGHT : 90) * scale;
        var bottom = images.get(Image.BOTTOM);
        // Artwork presence, raw draw height and reservation are independent. Tiny transparent
        // replacements suppress foreign visuals without reducing the browser's navigation area.
        bottomImage = new Bounds(0,0,width,bottom == null ? 0 : bottom.logicalHeight() * scale);
        chrome = new Bounds(0,0,width,SongSelectChrome.bottomReservation(height,bottomImage.height()));
        // Stable reserves a fixed navigation origin, independent of Back's PNG or alpha bounds.
        // The widescreen origin is 224 SD pixels on the 768-high skin canvas (192 at 4:3).
        float selectionX = (width > height * 4 / 3f ? 224 : 192) * scale;
        back = new Bounds(0,baseline,selectionX,90 * scale);
        var backAsset = images.get(Image.BACK);
        if (backAsset == null) { backImage = back; backInteraction = back; }
        else {
            // Back uses the same native scale and bottom-left raw origin as v2 selection artwork.
            // Transparent margins position artwork; alpha metrics only constrain interaction.
            backImage = new Bounds(0,baseline,backAsset.logicalWidth() * scale,backAsset.logicalHeight() * scale);
            var backMetrics = metrics.get(Image.BACK);
            backInteraction = intersect(back,backMetrics == null ? backImage : map(backImage,backMetrics.content(),scale));
        }
        float x = selectionX;
        float overshoot = 0;
        for (var action : Selection.values()) {
            var slot = new Bounds(x,baseline,action.logicalWidth * scale,controlHeight);
            var normal = artwork(slot, images.get(action.normal), metrics.get(action.normal), scale, legacy);
            var hover = artwork(slot, images.get(action.hover), metrics.get(action.hover), scale, legacy);
            Bounds interaction = images.get(action.normal) == null ? slot : union(normal.content(),hover.content());
            interaction = intersect(slot,interaction);
            controls.put(action,new Control(slot,normal,hover,interaction,x,legacy ? baseline + controlHeight : baseline));
            overshoot = Math.max(overshoot, Math.max(normal.image().height(),hover.image().height()) - controlHeight);
            x += slot.width() + spacing;
        }
        transparentOvershoot = Math.max(0,overshoot);
        float auxiliaryHeight = controlHeight * .32f;
        var inlineImport = new Bounds(x + 16 * scale,baseline + controlHeight * .20f,78 * scale,auxiliaryHeight);
        float radius = SongSelectChrome.cookieRadius(height);
        cookie = new Bounds(SongSelectChrome.cookieX(width,radius) - radius,
                SongSelectChrome.cookieY(radius) - radius,radius * 2,radius * 2);
        float statusX = inlineImport.x() + inlineImport.width() + 20 * scale;
        // Large composite canvases commonly bake profile/status artwork into the remaining bottom bar.
        // Keep our small auxiliary labels above that artwork, using geometry rather than skin names.
        boolean composite = java.util.Arrays.stream(Selection.values()).anyMatch(action -> {
            var normal = images.get(action.normal); var hover = images.get(action.hover);
            return composite(normal,action.logicalWidth,controlHeight / scale)
                    || composite(hover,action.logicalWidth,controlHeight / scale);
        });
        if (composite) {
            importAction = new Bounds(inlineImport.x(),Math.max(controlHeight,chrome.height()) + 9 * scale,
                    inlineImport.width(),inlineImport.height());
            status = new Bounds(selectionX,Math.max(controlHeight,chrome.height()) + 26 * scale,180 * scale,14 * scale);
            debug = new Bounds(status.x(),Math.max(controlHeight,chrome.height()) + 9 * scale,status.width(),14 * scale);
        } else {
            importAction = inlineImport;
            status = new Bounds(statusX,baseline + controlHeight * .50f,
                    Math.max(0,Math.min(180 * scale,cookie.x() - statusX - 12 * scale)),controlHeight * .28f);
            debug = new Bounds(statusX,baseline + 9 * scale,status.width(),14 * scale);
        }
    }

    Control control(Selection action) { return controls.get(action); }
    private static boolean composite(SkinTexture asset, float canvasWidth, float canvasHeight) {
        return asset != null && asset.logicalWidth() > canvasWidth * 3 && asset.logicalHeight() > canvasHeight * 2;
    }
    private static Artwork artwork(Bounds slot, SkinTexture asset, SelectionAssetBounds metrics, float scale, boolean legacy) {
        if (asset == null) return new Artwork(empty(),empty(),empty());
        var image = new Bounds(slot.x(),legacy ? slot.y() + slot.height() - asset.logicalHeight() * scale : slot.y(),
                asset.logicalWidth() * scale,asset.logicalHeight() * scale);
        // Metrics absent only in GL-free tests. Keep bounded nominal interaction there.
        return new Artwork(image, metrics == null ? image : map(image,metrics.opaque(),scale),
                metrics == null ? intersect(slot,image) : intersect(slot,map(image,metrics.content(),scale)));
    }
    private static Bounds map(Bounds image, SelectionAssetBounds.Rect rect, float scale) {
        return rect.empty() ? empty() : new Bounds(image.x() + rect.x() * scale,image.y() + rect.y() * scale,
                rect.width() * scale,rect.height() * scale);
    }
    static Bounds intersect(Bounds a, Bounds b) {
        float x = Math.max(a.x(),b.x()), y = Math.max(a.y(),b.y());
        return new Bounds(x,y,Math.max(0, Math.min(Math.min(a.width(), b.width()), Math.min(a.x()+a.width(),b.x()+b.width())-x)),
                Math.max(0, Math.min(Math.min(a.height(), b.height()), Math.min(a.y()+a.height(),b.y()+b.height())-y)));
    }
    private static Bounds union(Bounds a, Bounds b) {
        if (a.empty()) return b; if (b.empty()) return a;
        float x = Math.min(a.x(),b.x()), y = Math.min(a.y(),b.y());
        return new Bounds(x,y,Math.max(a.x()+a.width(),b.x()+b.width())-x,Math.max(a.y()+a.height(),b.y()+b.height())-y);
    }
    private static Bounds empty() { return new Bounds(0,0,0,0); }
}
