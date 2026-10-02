package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.ruleset.osu.OsuGrade;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
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
    record Style(float width, SongSelectSkinAssets skin, Texture fill, Color activeText,
                 Color inactiveText, boolean thumbnails) { }
    private final SpriteBatch batch;
    private final UiView view;
    private Style style;
    private final Color rowTint = new Color(), thumbnailTint = new Color();
    private final Color primaryTint = new Color(), secondaryTint = new Color(), detailTint = new Color(), starTint = new Color();
    private static final Color THUMB_FALLBACK = new Color(.23f, .20f, .31f, 1f);

    SongSelectRowRenderer(SpriteBatch batch, UiView view) { this.batch = batch; this.view = view; }

    void draw(List<Presentation> rows, Style style) {
        this.style = style;
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
        // 06003287/2c21: full browser order, preserving each row's sprite insertion order.
        // The caller owns the browser viewport, including chrome reservations.
        // This bounded osujava policy also applies to transparent chrome replacements.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        try {
            for (var row : rows) drawRow(row);
        } finally {
            batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
        }
    }

    private boolean has(Image image) { return style.skin() != null && style.skin().get(image) != null; }
    private void drawRow(Presentation item) {
        var row = item.row();
        if (row.revealAmount() < .01f) return;
        Color.rgba8888ToColor(rowTint, item.backgroundRgba());
        Color color = rowTint;
        color.a *= row.revealAmount() * item.foreground().baseOpacity();
        float x = row.x(), y = row.y();
        if (has(Image.MENU_BUTTON_BACKGROUND)) {
            view.beginText();
            var artwork = row.interaction();
            if (artwork == null) artwork = SongSelectArtwork.row(x, y, row.width(), row.height(),
                    style.skin().get(Image.MENU_BUTTON_BACKGROUND));
            skinImage(Image.MENU_BUTTON_BACKGROUND, artwork.x(), artwork.y(), artwork.width(), artwork.height(), color);
        } else {
            view.beginShapes();
            view.quad(x, y, x + row.width(), y, x + row.width(), y + row.height(), x + 9, y + row.height(), color);
            view.endShapes();
            view.beginText();
        }
        if (row.group()) {
            Color text = row.groupExpanded() ? style.activeText() : style.inactiveText();
            primaryTint.set(text == null ? UiTheme.TEXT : text);
            primaryTint.a *= row.revealAmount() * item.foreground().baseOpacity();
            float inset = 15 * row.height() / SongSelectMetrics.ROW_PITCH;
            view.textCenteredVertically(row.header(), x + inset, y + row.height() / 2,
                    Math.max(0, style.width() - x - inset), 24 * row.height() / (48 * 1.6f * 17), primaryTint, false);
            view.endText();
            return;
        }
        var geometry = item.geometry().text();
        drawBaseLabels(item, geometry);
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
        drawDetails(item, geometry);
        view.endText();
    }



    private Color textColour(SongSelectRow row) {
        return row.selected() ? style.activeText() : style.inactiveText() != null ? style.inactiveText() : UiTheme.TEXT;
    }

    private void drawBaseLabels(Presentation item, SongSelectRowPresentation.Geometry geometry) {
        var row = item.row();
        Color base = textColour(row);
        Color primary = SongSelectRowColours.label(primaryTint, base, row, true);
        Color secondary = SongSelectRowColours.label(secondaryTint, base, row, true);
        primary.a *= item.foreground().baseOpacity();
        secondary.a *= item.foreground().baseOpacity();
        float canvasScale = row.height() / (48 * 1.6f);
        view.textCenteredVertically(item.content().title(), row.x() + geometry.textX(), row.y() + geometry.titleY(),
                geometry.textWidth(), 16 * canvasScale / 17, primary, false);
        float secondaryWidth = Math.max(0, geometry.textWidth() - (geometry.secondaryX() - geometry.textX()));
        view.textCenteredVertically(item.content().byline(), row.x() + geometry.secondaryX(), row.y() + geometry.bylineY(),
                secondaryWidth, 12 * canvasScale / 17, secondary, false);
    }

    private void drawDetails(Presentation item, SongSelectRowPresentation.Geometry geometry) {
        var row = item.row();
        var content = item.content();
        Color base = textColour(row);
        Color detail = SongSelectRowColours.label(detailTint, base, row, false);
        detail.a *= item.foreground().detailOpacity();
        float canvasScale = row.height() / (48 * 1.6f);
        float secondaryWidth = Math.max(0, geometry.textWidth() - (geometry.secondaryX() - geometry.textX()));
        view.textCenteredVertically(content.detail(), row.x() + geometry.secondaryX(), row.y() + geometry.detailY(), secondaryWidth,
                12 * canvasScale / 17, detail, true);
        if (content.mode() > 0) nativeBadge(SongSelectSkinAssets.modeImage(content.mode(), 1),
                row.x() + geometry.modeX(), row.y() + geometry.modeY(), .8f * canvasScale, detail);
        if (item.grade() != null) {
            thumbnailTint.set(1, 1, 1, row.revealAmount() * item.foreground().detailOpacity());
            nativeBadge(gradeImage(item.grade()), row.x() + geometry.gradeX(), row.y() + geometry.gradeY(), canvasScale, thumbnailTint);
        }
        // Stars have their own opacity transforms and must not inherit the detail fade.
        SongSelectRowColours.label(detail, base, row, false);
        drawStars(item.stars(), row, row.x() + geometry.textX(), detail);
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
        if (w <= 0 || h <= 0) return;
        float u2 = asset == null ? 1 : asset.cropU2(), v2 = asset == null ? 1 : asset.cropV2();
        float cy = row.y() + row.height() / 2 - (stars.cropped() ? 15 : 18) * row.height() / 48;
        for (int i = 0; i < stars.glyphs().size(); i++) {
            float cx = x + (i + .5f) * w;
            float backgroundScale = stars.cropped() ? 1 : .35f;
            batch.setColor(starTint.set(1, 1, 1, 30 / 255f * stars.backgroundOpacity() * row.revealAmount()));
            batch.draw(texture, cx - w * backgroundScale / 2, cy - h * backgroundScale / 2,
                    w * backgroundScale, h * backgroundScale,0,v2,u2,0);
        }
        for (int i = 0; i < stars.glyphs().size(); i++) {
            float cx = x + (i + .5f) * w;
            var glyph = stars.glyphs().get(i);
            batch.setColor(starTint.set(tint.r, tint.g, tint.b, tint.a * stars.foregroundOpacity()));
            if (stars.cropped()) {
                if (glyph.crop() > 0) batch.draw(texture, cx - w / 2, cy - h / 2, w * glyph.crop(), h,
                        0, v2, u2 * glyph.crop(), 0);
            } else if (glyph.scale() != 0) batch.draw(texture, cx - w * glyph.scale() / 2, cy - h * glyph.scale() / 2,
                    w * glyph.scale(), h * glyph.scale(),0,v2,u2,0);
        }
        batch.setColor(Color.WHITE);
    }

    void drawGrade(OsuGrade grade, float x, float y, float w, float h, Color tint, Color textTint) {
        Image image = gradeImage(grade);
        if (has(image)) SongSelectSkinDrawing.fit(batch, style.skin(), image, x, y, w, h, tint);
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
        SongSelectSkinDrawing.drawTexture(batch,style.skin().get(image),x,y,w,h);
        batch.setColor(Color.WHITE);
    }

}
