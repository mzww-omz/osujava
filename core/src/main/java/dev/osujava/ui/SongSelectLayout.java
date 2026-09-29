package dev.osujava.ui;

import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.ui.theme.UiLayout;

/** Pure logical geometry. Window/framebuffer density and PNG dimensions never define row hitboxes. */
final class SongSelectLayout {
    record Rect(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return width > 0 && height > 0 && px >= x && px < x + width && py >= y && py < y + height;
        }
        Rect intersect(Rect other) {
            float left = Math.max(x, other.x), bottom = Math.max(y, other.y);
            return new Rect(left, bottom, Math.max(0, Math.min(x + width, other.x + other.width) - left),
                    Math.max(0, Math.min(y + height, other.y + other.height) - bottom));
        }
    }
    record RowGeometry(int logicalIndex, boolean selected, boolean hovered,
                       float targetX, float targetY, Rect body, Rect thumbnail,
                       Rect title, Rect metadata, Rect clip, Rect hit, float alpha, int zOrder,
                       SongSelectRowPresentation.Geometry text) { }
    record Snapshot(SongSelectChrome.Content chrome, SongSelectToolboxLayout toolbox,
                    Rect viewport, Rect metadata, Rect search, ScoreBrowserBounds scores) { }

    static Snapshot create(UiLayout ui, SongSelectSkinAssets skin) {
        var chrome = SongSelectChrome.content(ui.width(), ui.height(), skin);
        var toolbox = SongSelectToolboxLayout.create(ui.width(), ui.height(), skin);
        float left = SongSelectMetrics.wheelLeft(ui.width(), ui.height());
        return new Snapshot(chrome, toolbox,
                new Rect(left, chrome.bottom(), ui.width() - left, chrome.carouselTop() - chrome.bottom()),
                new Rect(18, ui.height() - 112, ui.width() * .52f - 28, 112),
                new Rect(ui.width() * .64f - 16, ui.height() - 80, ui.width() * .36f, 25),
                new ScoreBrowserBounds(18, chrome.bottom() + 48, ScoreBrowserBounds.columnWidth(ui), chrome.rankingHeaderTop() - 64));
    }

    static RowGeometry row(SongSelectRow row, int index, float targetX, float targetY,
                           float width, float bottom, float top, boolean thumbnails, boolean grade) {
        return row(row, index, targetX, targetY, width, bottom, top, thumbnails, grade, false, false);
    }

    static RowGeometry row(SongSelectRow row, int index, float targetX, float targetY,
                           float width, float bottom, float top, boolean thumbnails, boolean grade, boolean mode, boolean cropped) {
        var body = new Rect(row.x(), row.y(), row.width(), row.height());
        var clip = body.intersect(new Rect(0, bottom, width, top - bottom));
        var text = SongSelectRowPresentation.geometry(row.width(), row.height(), width - row.x(), thumbnails,
                row.difficultyIndex() >= 0, cropped, mode, grade);
        var thumbnail = new Rect(row.x() + text.thumbnailX(), row.y() + text.thumbnailY(), text.thumbnailWidth(), text.thumbnailHeight());
        return new RowGeometry(index, row.selected(), row.hoverAmount() > 0, targetX, targetY, body, thumbnail,
                new Rect(row.x() + text.textX(), row.y() + text.titleY(), text.textWidth(), 16),
                new Rect(row.x() + text.textX(), row.y() + text.starsY(), text.textWidth(), text.bylineY() - text.starsY() + 16),
                clip, row.interactive() && row.revealAmount() >= .05f ? clip : new Rect(0, 0, 0, 0),
                row.revealAmount(), row.selected() ? Integer.MAX_VALUE : index, text);
    }
}
