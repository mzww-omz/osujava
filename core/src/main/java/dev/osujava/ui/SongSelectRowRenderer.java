package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.ruleset.osu.OsuGrade;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiView;
import java.util.List;

/** Draw-only row pass. Receives immutable presentation and resident textures, never a browser or Library. */
final class SongSelectRowRenderer {
    record Presentation(SongSelectRow row, SongSelectRowPresentation.Content content, boolean played,
                        OsuGrade grade, Texture thumbnail, SongSelectLayout.RowGeometry geometry,
                        boolean groupContainsSelection, int backgroundRgba, SongSelectStarAnimation.Snapshot stars,
                        SongSelectForegroundAnimation.Snapshot foreground) {
        Presentation(SongSelectRow row, SongSelectRowPresentation.Content content, boolean played,
                     OsuGrade grade, Texture thumbnail, float thumbnailOpacity, SongSelectLayout.RowGeometry geometry,
                     boolean groupContainsSelection, int backgroundRgba, SongSelectStarAnimation.Snapshot stars) {
            this(row, content, played, grade, thumbnail, geometry, groupContainsSelection,
                    backgroundRgba, stars, new SongSelectForegroundAnimation.Snapshot(1, 1, thumbnailOpacity, 255));
        }
        Presentation(SongSelectRow row, SongSelectRowPresentation.Content content, boolean played,
                     OsuGrade grade, Texture thumbnail, float thumbnailOpacity, SongSelectLayout.RowGeometry geometry,
                     boolean groupContainsSelection, int backgroundRgba) {
            this(row, content, played, grade, thumbnail, thumbnailOpacity, geometry, groupContainsSelection,
                    backgroundRgba, SongSelectStarAnimation.Snapshot.EMPTY);
        }
    }
    record RetiringStars(SongSelectRow row, SongSelectLayout.RowGeometry geometry,
                         SongSelectStarAnimation.Snapshot stars, int tintRgba) { }
    record Style(float width, SongSelectSkinAssets skin, Texture fill, Color activeText,
                 Color inactiveText, boolean thumbnails) { }
    private final SpriteBatch batch;
    private final UiView view;
    private Style style;
    private final Color rowTint = new Color(), thumbnailTint = new Color();
    private final Color primaryTint = new Color(), secondaryTint = new Color(), detailTint = new Color(), starTint = new Color();
    private static final Color THUMB_FALLBACK = new Color(.23f, .20f, .31f, 1f);

    SongSelectRowRenderer(SpriteBatch batch, UiView view) { this.batch = batch; this.view = view; }

    void draw(List<Presentation> rows, Style style, UiLayout layout, float bottom, float top) {
        draw(rows, List.of(), style, layout, bottom, top);
    }

    void draw(List<Presentation> rows, List<RetiringStars> retiring, Style style, UiLayout layout, float bottom, float top) {
        this.style = style;
        float scaleX = Gdx.graphics.getBackBufferWidth() / layout.width();
        float scaleY = Gdx.graphics.getBackBufferHeight() / layout.height();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, Math.round(bottom * scaleY), Math.round(layout.width() * scaleX),
                Math.max(0, Math.round((top - bottom) * scaleY)));
        try {
            for (var row : rows) if (!row.row().selected()) drawClipped(row, layout, bottom, top, scaleX, scaleY);
            for (var row : rows) if (row.row().selected()) drawClipped(row, layout, bottom, top, scaleX, scaleY);
            for (var item : retiring) {
                var clip = item.geometry().clip();
                Gdx.gl.glScissor(Math.round(clip.x() * scaleX), Math.round(clip.y() * scaleY),
                        Math.max(0, Math.round(clip.width() * scaleX)), Math.max(0, Math.round(clip.height() * scaleY)));
                Color.rgba8888ToColor(detailTint, item.tintRgba());
                detailTint.a *= item.row().revealAmount();
                view.beginText();
                drawStars(item.stars(), item.row(), item.row().x() + item.geometry().text().textX(), detailTint);
                view.endText();
            }
        } finally { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); }
    }

    private void drawClipped(Presentation row, UiLayout layout, float bottom, float top, float scaleX, float scaleY) {
        var clip = row.geometry().clip();
        if (clip.width() <= 0 || clip.height() <= 0) return;
        Gdx.gl.glScissor(0, Math.round(bottom * scaleY), Math.round(layout.width() * scaleX),
                Math.max(0, Math.round((top - bottom) * scaleY)));
        drawRow(row, scaleX, scaleY);
    }

    private void clipContent(Presentation item, float scaleX, float scaleY) {
        var clip = item.geometry().clip();
        Gdx.gl.glScissor(Math.round(clip.x() * scaleX), Math.round(clip.y() * scaleY),
                Math.max(0, Math.round(clip.width() * scaleX)), Math.max(0, Math.round(clip.height() * scaleY)));
    }

    private boolean has(Image image) { return style.skin() != null && style.skin().get(image) != null; }
    private void drawRow(Presentation item, float scaleX, float scaleY) {
        var row = item.row();
        if (row.revealAmount() < .01f) return;
        Color.rgba8888ToColor(rowTint, item.backgroundRgba());
        Color color = rowTint;
        color.a *= row.revealAmount() * item.foreground().baseOpacity();
        float x = row.x(), y = row.y();
        if (has(Image.MENU_BUTTON_BACKGROUND)) {
            view.beginText();
            var artwork = SongSelectArtwork.card(x, y + row.height() / 2,
                    row.height() * SongSelectMetrics.CAROUSEL_HEIGHT / SongSelectMetrics.ROW_PITCH,
                    1, style.skin().get(Image.MENU_BUTTON_BACKGROUND));
            skinImage(Image.MENU_BUTTON_BACKGROUND, artwork.x(), artwork.y(), artwork.width(), artwork.height(), color);
        } else {
            view.beginShapes();
            view.quad(x, y, x + row.width(), y, x + row.width(), y + row.height(), x + 9, y + row.height(), color);
            view.endShapes();
            view.beginText();
        }
        // The bundled default is dark artwork. Keep an authored text colour, but provide
        // a light selected surface when that text is dark. Never wash custom skin artwork.
        var background = has(Image.MENU_BUTTON_BACKGROUND) ? style.skin().get(Image.MENU_BUTTON_BACKGROUND) : null;
        if (fallbackWash(row.selected() || row.group() && row.groupExpanded(), background != null && background.file().classpathResource() != null, style.activeText())) {
            batch.setColor(1,1,1,.86f * row.revealAmount() * item.foreground().baseOpacity());
            batch.draw(style.fill(),x,y,row.width(),row.height());
            batch.setColor(Color.WHITE);
        }
        if (row.group()) {
            batch.flush();
            clipContent(item, scaleX, scaleY);
            Color text = row.groupExpanded() ? style.activeText() : style.inactiveText();
            primaryTint.set(text == null ? UiTheme.TEXT : text);
            primaryTint.a *= row.revealAmount() * item.foreground().baseOpacity();
            float inset = 15 * row.height() / SongSelectMetrics.ROW_PITCH;
            view.textSmooth(row.header(), x + inset, y + row.height() / 2,
                    Math.max(0, style.width() - x - inset), .90f, primaryTint);
            view.endText();
            return;
        }
        var geometry = item.geometry().text();
        float tx = x + geometry.thumbnailX(), ty = y + geometry.thumbnailY();
        if (style.thumbnails()) {
            // Keep body, thumbnail fallback, cover and text in the same sprite batch.
            float opacity = row.revealAmount() * item.foreground().thumbnailOpacity();
            float brightness = item.foreground().thumbnailBrightness() / 255f;
            var texture = item.thumbnail();
            if (texture == null) {
                batch.setColor(thumbnailTint.set(THUMB_FALLBACK.r * brightness, THUMB_FALLBACK.g * brightness,
                        THUMB_FALLBACK.b * brightness, opacity));
                batch.draw(style.fill(), tx, ty, geometry.thumbnailWidth(), geometry.thumbnailHeight());
            }
            batch.setColor(brightness, brightness, brightness, opacity);
            view.imageCover(texture, tx, ty, geometry.thumbnailWidth(), geometry.thumbnailHeight());
            batch.setColor(Color.WHITE);
        }
        // Stable thumbnails are taller than the 48-unit row pitch. Preserve the full image
        // within the carousel viewport; only labels use the body clip.
        batch.flush();
        clipContent(item, scaleX, scaleY);
        drawRowLabel(item, geometry);
        view.endText();
    }

    static boolean fallbackWash(boolean selected, boolean bundled, Color text) {
        return selected && bundled && .2126f * text.r + .7152f * text.g + .0722f * text.b < .35f;
    }

    private void drawRowLabel(Presentation item, SongSelectRowPresentation.Geometry geometry) {
        var row = item.row();
        var content = item.content();
        Color base = row.selected() ? style.activeText() : style.inactiveText() != null ? style.inactiveText() : UiTheme.TEXT;
        Color primary = SongSelectRowColours.label(primaryTint, base, row, true);
        Color secondary = SongSelectRowColours.label(secondaryTint, base, row, true);
        Color detail = SongSelectRowColours.label(detailTint, base, row, false);
        float x = row.x() + geometry.textX(), width = geometry.textWidth();
        primary.a *= item.foreground().baseOpacity();
        secondary.a *= item.foreground().baseOpacity();
        detail.a *= item.foreground().detailOpacity();
        float canvasScale = row.height() / (48 * 1.6f);
        if (content.mode() > 0) nativeBadge(SongSelectSkinAssets.modeImage(content.mode(), 1),
                row.x() + geometry.modeX(), row.y() + geometry.modeY(), .8f * canvasScale, detail);
        if (item.grade() != null) {
            thumbnailTint.set(1, 1, 1, row.revealAmount() * item.foreground().detailOpacity());
            nativeBadge(gradeImage(item.grade()), row.x() + geometry.gradeX(), row.y() + geometry.gradeY(), canvasScale, thumbnailTint);
        }
        view.textCenteredVertically(content.title(), x, row.y() + geometry.titleY(), width, 16 * canvasScale / 17, primary, false);
        float secondaryWidth = Math.max(0, width - (geometry.secondaryX() - geometry.textX()));
        view.textCenteredVertically(content.byline(), row.x() + geometry.secondaryX(), row.y() + geometry.bylineY(), secondaryWidth,
                12 * canvasScale / 17, secondary, false);
        view.textCenteredVertically(content.detail(), row.x() + geometry.secondaryX(), row.y() + geometry.detailY(), secondaryWidth,
                12 * canvasScale / 17, detail, true);
        // Stars have their own opacity transforms and must not inherit the detail fade.
        SongSelectRowColours.label(detail, base, row, false);
        if (width > 0) drawStars(item.stars(), row, x, detail);
    }

    private void nativeBadge(Image image, float x, float centreY, float scale, Color tint) {
        if (!has(image)) return;
        var asset = style.skin().get(image);
        float w = (int) asset.logicalWidth() * scale, h = (int) asset.logicalHeight() * scale;
        skinImage(image, x, centreY - h / 2, w, h, tint);
    }

    /** Native star dimensions are texture-based; clipping never compresses the ten sprite positions. */
    private void drawStars(SongSelectStarAnimation.Snapshot stars, SongSelectRow row, float x, Color tint) {
        var texture = style.skin() == null ? null : style.skin().starTexture();
        if (texture == null) return;
        var asset = style.skin().get(Image.STAR);
        float density = asset == null ? 1 : asset.density();
        float unit = .6f * row.height() / (48 * 1.6f);
        float w = (int) (texture.getWidth() / density) * unit, h = (int) (texture.getHeight() / density) * unit;
        float cy = row.y() + row.height() / 2 - (stars.cropped() ? 15 : 18) * row.height() / 48;
        for (int i = 0; i < stars.glyphs().size(); i++) {
            float cx = x + (i + .5f) * w;
            float backgroundScale = stars.cropped() ? 1 : .35f;
            batch.setColor(starTint.set(1, 1, 1, 30 / 255f * stars.backgroundOpacity() * row.revealAmount()));
            batch.draw(texture, cx - w * backgroundScale / 2, cy - h * backgroundScale / 2,
                    w * backgroundScale, h * backgroundScale);
            var glyph = stars.glyphs().get(i);
            batch.setColor(starTint.set(tint.r, tint.g, tint.b, tint.a * stars.foregroundOpacity()));
            if (stars.cropped()) {
                if (glyph.crop() > 0) batch.draw(texture, cx - w / 2, cy - h / 2, w * glyph.crop(), h,
                        0, 1, glyph.crop(), 0);
            } else if (glyph.scale() != 0) batch.draw(texture, cx - w * glyph.scale() / 2, cy - h * glyph.scale() / 2,
                    w * glyph.scale(), h * glyph.scale());
        }
        batch.setColor(Color.WHITE);
    }

    void drawGrade(OsuGrade grade, float x, float y, float w, float h, Color tint, Color textTint) {
        Image image = gradeImage(grade);
        if (has(image)) skinImageFit(image, x, y, w, h, tint);
        else view.textSmoothBold(grade.name(), x + 3, y + h * .35f, w - 6, 1.35f, textTint);
    }

    static Image gradeImage(OsuGrade grade) {
        return switch (grade) {
            case SS -> Image.GRADE_SS; case S -> Image.GRADE_S; case A -> Image.GRADE_A;
            case B -> Image.GRADE_B; case C -> Image.GRADE_C; case D -> Image.GRADE_D;
        };
    }

    private void skinImage(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        batch.setColor(tint);
        batch.draw(style.skin().get(image).texture(), x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    private void skinImageFit(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        var asset = style.skin().get(image);
        float scale = Math.min(w / asset.logicalWidth(), h / asset.logicalHeight());
        float width = asset.logicalWidth() * scale, height = asset.logicalHeight() * scale;
        skinImage(image, x + (w - width) / 2, y + (h - height) / 2, width, height, tint);
    }

}
