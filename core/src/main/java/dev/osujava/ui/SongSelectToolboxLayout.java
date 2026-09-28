package dev.osujava.ui;

import dev.osujava.skin.SelectionAssetBounds;
import dev.osujava.skin.SongSelectBodyBounds;
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
    final Bounds chrome, back, backImage, backInteraction, importAction, cookie, status, debug;
    final float baseline, controlHeight, spacing, transparentOvershoot;

    static SongSelectToolboxLayout create(float width, float height, SongSelectSkinAssets skin) {
        var images = new EnumMap<Image, SkinTexture>(Image.class);
        var metrics = new EnumMap<Image, SelectionAssetBounds>(Image.class);
        if (skin != null) for (var image : Image.values()) {
            if (skin.get(image) != null) images.put(image, skin.get(image));
            if (skin.selectionBounds(image) != null) metrics.put(image, skin.selectionBounds(image));
        }
        return new SongSelectToolboxLayout(width, height, skin != null && skin.configuration().legacyVersion() < 2,
                images, metrics, skin == null ? SongSelectBodyBounds.FULL : skin.actionBody(Image.BACK));
    }

    /** GL-free fixture entry point; production uses load-time metrics from the shared resolver. */
    SongSelectToolboxLayout(float width, float height, boolean legacy, EnumMap<Image, SkinTexture> images,
            EnumMap<Image, SelectionAssetBounds> metrics, SongSelectBodyBounds backBody) {
        float scale = height / 768f;
        baseline = 0; spacing = 0; controlHeight = (legacy ? 87 : 90) * scale;
        var bottom = images.get(Image.BOTTOM);
        chrome = new Bounds(0,0,width,bottom == null ? SongSelectChrome.bottomHeight(height) : bottom.logicalHeight() * scale);
        back = new Bounds(0,baseline,154 * height / 720f,controlHeight);
        var backAsset = images.get(Image.BACK);
        if (backAsset == null) { backImage = back; backInteraction = back; }
        else {
            float factor = Math.min(back.width() / (backAsset.logicalWidth() * backBody.width()),
                    controlHeight / (backAsset.logicalHeight() * backBody.height()));
            backImage = new Bounds(-backBody.left() * backAsset.logicalWidth() * factor,
                    baseline - backBody.bottom() * backAsset.logicalHeight() * factor,
                    backAsset.logicalWidth() * factor, backAsset.logicalHeight() * factor);
            backInteraction = new Bounds(0,baseline,backImage.width() * backBody.width(),backImage.height() * backBody.height());
        }
        float x = back.width();
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
        importAction = new Bounds(x + 16 * scale,baseline + controlHeight * .20f,78 * scale,auxiliaryHeight);
        float radius = SongSelectChrome.cookieRadius(height);
        cookie = new Bounds(SongSelectChrome.cookieX(width,radius) - radius,
                SongSelectChrome.cookieY(radius) - radius,radius * 2,radius * 2);
        float statusX = importAction.x() + importAction.width() + 20 * scale;
        status = new Bounds(statusX,baseline + controlHeight * .50f,
                Math.max(0,Math.min(180 * scale,cookie.x() - statusX - 12 * scale)),controlHeight * .28f);
        debug = new Bounds(statusX,baseline + 9 * scale,status.width(),14 * scale);
    }

    Control control(Selection action) { return controls.get(action); }
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
        return new Bounds(x,y,Math.max(0,Math.min(a.x()+a.width(),b.x()+b.width())-x),
                Math.max(0,Math.min(a.y()+a.height(),b.y()+b.height())-y));
    }
    private static Bounds union(Bounds a, Bounds b) {
        if (a.empty()) return b; if (b.empty()) return a;
        float x = Math.min(a.x(),b.x()), y = Math.min(a.y(),b.y());
        return new Bounds(x,y,Math.max(a.x()+a.width(),b.x()+b.width())-x,Math.max(a.y()+a.height(),b.y()+b.height())-y);
    }
    private static Bounds empty() { return new Bounds(0,0,0,0); }
}
