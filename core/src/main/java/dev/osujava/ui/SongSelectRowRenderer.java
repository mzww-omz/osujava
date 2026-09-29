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
                        OsuGrade grade, Texture thumbnail, float thumbnailOpacity, SongSelectLayout.RowGeometry geometry) { }
    record Style(float width, SongSelectSkinAssets skin, Texture fill, Color activeText,
                 Color inactiveText, boolean thumbnails) { }
    private final SpriteBatch batch;
    private final UiView view;
    private Style style;
    private final Color rowTint = new Color(), thumbnailTint = new Color();
    private final Color primaryTint = new Color(), secondaryTint = new Color(), detailTint = new Color(), starTint = new Color();
    private static final Color TOP = new Color(.025f, .022f, .045f, .68f);
    private static final Color OTHER = new Color(.58f, .30f, .49f, .90f);
    private static final Color OTHER_HOVER = new Color(.73f, .38f, .59f, .96f);
    private static final Color PLAYED = new Color(.79f, .46f, .23f, .92f);
    private static final Color PLAYED_HOVER = new Color(.90f, .57f, .30f, .98f);
    private static final Color SIBLING = new Color(.25f, .54f, .73f, .92f);
    private static final Color SIBLING_HOVER = new Color(.34f, .66f, .84f, .98f);
    private static final Color SELECTED = new Color(.96f, .95f, .98f, .98f);
    private static final Color THUMB_FALLBACK = new Color(.23f, .20f, .31f, 1f);

    SongSelectRowRenderer(SpriteBatch batch, UiView view) { this.batch = batch; this.view = view; }

    void draw(List<Presentation> rows, Style style, UiLayout layout, float bottom, float top) {
        this.style = style;
        float scaleX = Gdx.graphics.getBackBufferWidth() / layout.width();
        float scaleY = Gdx.graphics.getBackBufferHeight() / layout.height();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, Math.round(bottom * scaleY), Math.round(layout.width() * scaleX),
                Math.max(0, Math.round((top - bottom) * scaleY)));
        try {
            for (var row : rows) if (!row.row().selected()) drawClipped(row, layout, bottom, top, scaleX, scaleY);
            for (var row : rows) if (row.row().selected()) drawClipped(row, layout, bottom, top, scaleX, scaleY);
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
        if (row.setIndex() < 0) {
            clipContent(item, scaleX, scaleY);
            // Compact divider at the row center; no beatmap body, thumbnail, hover or play target.
            view.beginShapes();
            view.box(row.x() + 12, row.y() + row.height() / 2 - 13, row.width(), 26, 0, TOP);
            view.endShapes();
            view.beginText();
            view.textSmoothBold(row.header(), row.x() + 22,
                    row.y() + row.height() / 2 + 2, style.width() - row.x() - 30, .74f, UiTheme.TEXT);
            view.endText();
            return;
        }
        boolean played = item.played();
        var tone = SongSelectRowPresentation.tone(row.selected(), row.sibling(), played);
        Color color = rowTint.set(switch (tone) {
            case SELECTED -> SELECTED; case SIBLING -> SIBLING; case PLAYED -> PLAYED; case UNPLAYED -> OTHER;
        });
        if (!row.selected()) color.lerp(row.sibling() ? SIBLING_HOVER : played ? PLAYED_HOVER : OTHER_HOVER,row.hoverAmount());
        color.a *= row.revealAmount();
        float x = row.x(), y = row.y();
        if (has(Image.MENU_BUTTON_BACKGROUND)) {
            view.beginText();
            var body = style.skin().rowBody();
            float imageWidth = row.width() / body.width(), imageHeight = row.height() / body.height();
            skinImage(Image.MENU_BUTTON_BACKGROUND, x - body.left() * imageWidth,
                    y - body.bottom() * imageHeight, imageWidth, imageHeight, color);
        } else {
            view.beginShapes();
            view.quad(x, y, x + row.width(), y, x + row.width(), y + row.height(), x + 9, y + row.height(), color);
            view.endShapes();
            view.beginText();
        }
        // The bundled default is dark artwork. Keep an authored text colour, but provide
        // a light selected surface when that text is dark. Never wash custom skin artwork.
        var background = has(Image.MENU_BUTTON_BACKGROUND) ? style.skin().get(Image.MENU_BUTTON_BACKGROUND) : null;
        if (fallbackWash(row.selected(), background != null && background.file().classpathResource() != null, style.activeText())) {
            batch.setColor(1,1,1,.86f * row.revealAmount());
            batch.draw(style.fill(),x,y,row.width(),row.height());
            batch.setColor(Color.WHITE);
        }
        var content = item.content();
        var geometry = item.geometry().text();
        float tx = x + geometry.thumbnailX(), ty = y + geometry.thumbnailY();
        if (style.thumbnails()) {
            // Keep body, thumbnail fallback, cover and text in the same sprite batch.
            batch.setColor(thumbnailTint.set(THUMB_FALLBACK.r, THUMB_FALLBACK.g, THUMB_FALLBACK.b, row.revealAmount()));
            batch.draw(style.fill(), tx, ty, geometry.thumbnailWidth(), geometry.thumbnailHeight());
            var texture = item.thumbnail();
            float brightness = row.selected() ? 1 : .78f + row.hoverAmount() * .12f;
            batch.setColor(brightness, brightness, brightness, row.revealAmount() * item.thumbnailOpacity());
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
        Color primary = primaryTint.set(base);
        primary.a *= row.revealAmount() * (row.sibling() ? .24f : 1);
        Color secondary = secondaryTint.set(base);
        secondary.a *= row.revealAmount() * (row.sibling() ? .20f : .80f);
        Color detail = detailTint.set(base);
        detail.a *= row.revealAmount() * (row.difficultyIndex() >= 0 ? 1 : .72f);
        float x = row.x() + geometry.textX(), width = geometry.textWidth();
        boolean child = row.difficultyIndex() >= 0;
        float badgeScale = row.height() / 72;
        float modeSize = 24 * badgeScale;
        float column = (item.grade() == null ? 32 : 52) * badgeScale;
        float modeY = item.grade() == null ? row.y() + (row.height() - modeSize) / 2
                : row.y() + row.height() - modeSize - 2 * badgeScale;
        if (content.mode() >= 0) SongSelectSkinDrawing.fit(batch, style.skin(), SongSelectSkinAssets.modeImage(content.mode(), 1),
                x - column + (column - modeSize) / 2, modeY, modeSize, modeSize, detail);
        if (item.grade() != null)
            drawGrade(item.grade(), x - 48 * badgeScale, row.y() + 2 * badgeScale, 40 * badgeScale, 30 * badgeScale, thumbnailTint.set(1, 1, 1, detail.a), detail);
        view.textSmooth(content.title(), x, row.y() + (child ? geometry.titleY() : row.height() / 2 + 8), width,
                .87f, primary);
        view.textSmooth(content.byline(), x, row.y() + (child ? geometry.bylineY() : row.height() / 2 - 12), width, .65f, secondary);
        if (child) view.textSmoothBold(content.detail(), x, row.y() + geometry.detailY(), width, .90f, detail);
        var stars = content.stars();
        if (stars.present() && width > 0) drawStars(stars, x,
                row.y() + geometry.starsY(), detail, row.selected(), width);
    }

    private void drawStars(SongSelectRowPresentation.Stars stars, float x, float y, Color tint, boolean selected, float availableWidth) {
        var texture = style.skin() == null ? null : style.skin().starTexture();
        var band = stars.layout(availableWidth);
        float scale = texture == null ? 0 : Math.min(band.size() / texture.getWidth(), band.size() / texture.getHeight());
        float w = texture == null ? 0 : texture.getWidth() * scale, h = texture == null ? 0 : texture.getHeight() * scale;
        for (int i = 0; texture != null && i < band.icons(); i++) {
            float sx = x + i * band.step() + (band.size() - w) / 2, sy = y + (15 - h) / 2;
            batch.setColor(starTint.set(tint.r, tint.g, tint.b, tint.a * .24f));
            batch.draw(texture, sx, sy, w, h);
            float fill = stars.fill(i);
            batch.setColor(starTint.set(tint.r, tint.g, tint.b, tint.a * (selected ? 1 : .88f)));
            if (fill > 0) {
                if (style.skin().configuration().legacyVersion() >= 2.2)
                    batch.draw(texture, sx + w * (1 - fill) / 2, sy + h * (1 - fill) / 2, w * fill, h * fill);
                else batch.draw(texture, sx, sy, w * fill, h, 0, 1, fill, 0);
            }
        }
        batch.setColor(Color.WHITE);
        view.textSmooth(stars.label(), x + band.numberX(), y + 3, band.numberWidth(), .62f, tint);
    }

    void drawGrade(OsuGrade grade, float x, float y, float w, float h, Color tint, Color textTint) {
        Image image = switch (grade) {
            case SS -> Image.GRADE_SS; case S -> Image.GRADE_S; case A -> Image.GRADE_A;
            case B -> Image.GRADE_B; case C -> Image.GRADE_C; case D -> Image.GRADE_D;
        };
        if (has(image)) skinImageFit(image, x, y, w, h, tint);
        else view.textSmoothBold(grade.name(), x + 3, y + h * .35f, w - 6, 1.35f, textTint);
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
