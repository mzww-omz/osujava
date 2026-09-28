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
                        OsuGrade grade, Texture thumbnail, float thumbnailOpacity) { }
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
            for (var row : rows) if (!row.row().selected()) drawRow(row);
            for (var row : rows) if (row.row().selected()) drawRow(row);
        } finally { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); }
    }

    private boolean has(Image image) { return style.skin() != null && style.skin().get(image) != null; }
    private void drawRow(Presentation item) {
        var row = item.row();
        if (row.revealAmount() < .01f) return;
        if (row.setIndex() < 0) {
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
        var content = item.content();
        var geometry = SongSelectRowPresentation.geometry(row.width(), row.height(), style.width() - x,
                style.thumbnails());
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
        drawRowLabel(item, geometry);
        view.endText();
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
        if (item.grade() != null) {
            drawGrade(item.grade(), x, row.y() + row.height() / 2 - 17, 44, 34, thumbnailTint.set(1, 1, 1, detail.a), detail);
            x += 52; width = Math.max(0, width - 52);
        }
        view.textSmooth(content.title(), x, row.y() + (child ? geometry.titleY() : row.height() / 2 + 8), width,
                .87f, primary);
        view.textSmooth(content.byline(), x, row.y() + (child ? geometry.bylineY() : row.height() / 2 - 12), width, .65f, secondary);
        if (child) view.textSmoothBold(content.detail(), x, row.y() + geometry.detailY(), width, .90f, detail);
        var stars = content.stars();
        if (stars.present() && width >= stars.width()) drawStars(stars, x,
                row.y() + geometry.starsY(), detail, row.selected());
    }

    private void drawStars(SongSelectRowPresentation.Stars stars, float x, float y, Color tint, boolean selected) {
        var texture = style.skin() == null ? null : style.skin().starTexture();
        if (texture == null) return;
        float scale = Math.min(15f / texture.getWidth(), 15f / texture.getHeight());
        float w = texture.getWidth() * scale, h = texture.getHeight() * scale;
        for (int i = 0; i < stars.slots(); i++) {
            float sx = x + i * 18 + (15 - w) / 2, sy = y + (15 - h) / 2;
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
        view.textSmooth(stars.label(), x + stars.slots() * 18 + 3, y + 3, stars.numericWidth() - 3, .62f, tint);
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
        skinImage(image, x + (w - width) / 2, y, width, height, tint);
    }

}
