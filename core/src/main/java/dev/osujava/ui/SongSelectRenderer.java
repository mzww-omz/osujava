package dev.osujava.ui;

import dev.osujava.score.LocalScoreStore;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import dev.osujava.skin.SongSelectSkinAssets.Selection;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiView;

import java.util.List;
import java.util.Locale;

/** Draw-only composition. Input and animation have already been sampled into Frame. */
final class SongSelectRenderer {
    private static final Color TOP = new Color(.025f, .022f, .045f, .68f);
    private static final Color LEFT = new Color(.025f, .022f, .045f, .24f);
    private static final Color BOTTOM = new Color(.025f, .022f, .045f, .91f);
    private static final Color DIM = new Color(.025f, .022f, .045f, .18f);
    private static final Color OTHER = new Color(.58f, .30f, .49f, .90f);
    private static final Color SIBLING = new Color(.25f, .54f, .73f, .92f);
    private static final Color SIBLING_HOVER = new Color(.34f, .66f, .84f, .98f);
    private static final Color SELECTED = new Color(.96f, .95f, .98f, .98f);
    private static final Color DARK_TEXT = new Color(.14f, .10f, .18f, 1f);
    private static final Color BACK_PINK = new Color(.83f, .28f, .55f, 1f);

    private final UiView view;
    private final SpriteBatch batch;
    private final SongSelectRowRenderer rowRenderer;
    private final OsuCookie playCookie;
    private final Color actionTint = new Color(Color.WHITE);
    private final java.util.EnumSet<Selection> renderedSelectionProcedural = java.util.EnumSet.noneOf(Selection.class);
    private boolean renderedTopProcedural, renderedBottomProcedural;
    private Frame frame;
    record BrowserView(SongBrowserModel.Sort sort, SongBrowserModel.Group group, int visibleCount) { }
    record ScoreView(List<ScoreBrowserModel.Row> rows, int first, java.util.UUID selected) { }
    record Frame(
        SongSelectSkinAssets skin,
        Color activeText,
        Color inactiveText,
        SongSelectToolboxState toolbox,
        BrowserView browser,
        ScoreView scores,
        SongBrowserControls controls,
        List<BeatmapSet> sets,
        List<SongSelectRow> visibleRows,
        List<SongSelectRowRenderer.Presentation> rowPresentations,
        Texture rowFill,
        SongSelectDetails details,
        SongSelectToolboxLayout bottomLayout,
        SongSelectChrome.Content chromeContent,
        boolean searchActive,
        String search,
        boolean importing,
        String toast,
        Color toastColor,
        float toastSeconds,
        float seconds,
        double previewSeconds,
        float backgroundFade,
        float bottom,
        float top,
        float searchX,
        float searchW,
        float contentWidth,
        float scrollOffset,
        float scrollRange,
        float pointerX,
        float pointerY,
        boolean pointerPressed,
        SongSelectHover.Appearance hoverAppearance,
        float entranceOpacity,
        float outgoingOpacity,
        boolean outgoingPending,
        BeatmapDifficulty selectedDifficulty,
        boolean supportedMode,
        LocalScoreStore.Status scoreStatus,
        Texture background,
        boolean showThumbnails,
        ScoreBrowserBounds scoreBounds) { }
    SongSelectRenderer(UiView view, SpriteBatch batch, SongSelectRowRenderer rowRenderer, OsuCookie playCookie) {
        this.view = view; this.batch = batch; this.rowRenderer = rowRenderer; this.playCookie = playCookie;
    }
    boolean renderedSelectionProcedural(Selection action) { return renderedSelectionProcedural.contains(action); }
    boolean renderedChromeProcedural(Image image) { return image == Image.TOP ? renderedTopProcedural : renderedBottomProcedural; }
    void draw(Frame frame, UiLayout layout) {
        this.frame = frame;
        float px = frame.pointerX, py = frame.pointerY;
        view.clear();
        view.background(frame.background, .72f * frame.backgroundFade);
        view.beginShapes();
        view.box(0, 0, layout.width(), layout.height(), 0, DIM);
        view.endShapes();
        SongSelectDecorations.draw(view,batch,frame.skin,layout,frame.seconds,frame.previewSeconds,frame.selectedDifficulty);
        drawRows(layout);
        view.beginShapes();
        renderedTopProcedural = SongSelectChrome.procedural(frame.skin, Image.TOP);
        renderedBottomProcedural = SongSelectChrome.procedural(frame.skin, Image.BOTTOM);
        if (renderedTopProcedural) view.box(0, layout.height() - 112, layout.width() * .52f, 112, 0, TOP);
        if (renderedTopProcedural) view.box(layout.width() * .55f, frame.top, layout.width() * .45f, layout.height() - frame.top, 0, TOP);

        if (renderedBottomProcedural) view.box(0, 0, layout.width(), frame.bottom, 0, BOTTOM);
        view.endShapes();
        var thumb = SongSelectScrollbar.thumb(layout.width(),frame.bottom,frame.top,frame.scrollOffset,frame.scrollRange);
        if (thumb.height() > 0) {
            view.beginShapes();
            view.box(thumb.x(),frame.bottom,thumb.width(),frame.top-frame.bottom,0,LEFT);
            view.box(thumb.x(),thumb.y(),thumb.width(),thumb.height(),0,UiTheme.TEXT);
            view.endShapes();
        }
        view.beginText();
        drawScoreBackgrounds(layout);
        view.endText();
        // Artwork can exceed the content reservation; retain the authored canvas inside the viewport.
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        try {
            chromeClip(layout, SongSelectChrome.topClip(layout.width(), layout.height()));
            view.beginText();
            drawTopSkin(layout);
            view.endText();
            chromeClip(layout, SongSelectChrome.bottomClip(layout.width(), layout.height()));
            view.beginText();
            skinImage(Image.BOTTOM, frame.bottomLayout.bottomImage, Color.WHITE);
            view.endText();
        } finally { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); }
        view.beginShapes();
        boolean backHover = !frame.toolbox.open() && frame.bottomLayout.backInteraction.contains(px,py);
        boolean backPressed = backHover && frame.pointerPressed;
        if (!has(Image.BACK)) chromeBox(frame.bottomLayout.back,actionTint.set(BACK_PINK).lerp(SELECTED,backPressed ? .22f : backHover ? .10f : 0));
        renderedSelectionProcedural.clear();
        for (var action : Selection.values()) if (!has(action.normal)) {
            renderedSelectionProcedural.add(action);
            var slot = frame.bottomLayout.control(action).slot();
            boolean hover = !frame.toolbox.open() && frame.bottomLayout.control(action).interaction().contains(px,py);
            chromeBox(slot,!selectionEnabled(action) ? LEFT : hover ? SIBLING_HOVER : OTHER);
        }
        frame.controls.drawShapes(view,layout.width(),layout.height(),frame.skin);
        if (frame.searchActive || !frame.search.isEmpty()) view.box(frame.searchX, layout.height() - 80, frame.searchW, 25, 0, LEFT);
        view.box(18, frame.chromeContent.rankingHeaderTop() - 28, frame.scoreBounds.width(), 28, 0, TOP);
        drawScoreShapes(layout, px, py);
        if (frame.toastSeconds > 0) view.box(18, frame.bottom + 12, Math.min(450, layout.width() * .42f), 35, 0, BOTTOM);
        view.endShapes();
        // Stable metadata (.79) and scores precede selection artwork (.95/.96).
        // A selection-mode canvas may include opaque upper chrome, not just buttons.
        view.beginText();
        drawMetadata(layout);
        drawRanking(layout);
        view.endText();
        // Composite artwork may extend above the control reservation. Only the viewport
        // clips decoration; the bounded input geometry remains independent.
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        try {
            chromeClip(layout, new SongSelectChrome.Bounds(0, 0, layout.width(), layout.height()));
            view.beginText();
            for (var action : Selection.values()) drawSelection(action, false);
            for (var action : Selection.values()) drawSelection(action, true);
            view.endText();
        } finally { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); }
        if (frame.selectedDifficulty != null) {
            playCookie.draw(view, (float) SongSelectDecorations.beat(frame.previewSeconds, frame.selectedDifficulty), !frame.toolbox.open() && playCookie.hit(px, py),
                    !frame.toolbox.open() && frame.pointerPressed);
        }
        view.beginText();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        chromeClip(layout, new SongSelectChrome.Bounds(0, 0, layout.width(), layout.height()));
        var backFrame = frame.skin == null ? null : frame.skin.backFrame(frame.seconds);
        if (backFrame != null) {
            float scale = layout.height() / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
            batch.setColor(Color.WHITE);
            batch.draw(backFrame.texture(), 0, frame.bottomLayout.baseline,
                    backFrame.logicalWidth() * scale, backFrame.logicalHeight() * scale);
            int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
            int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
            try {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                batch.setColor(1,1,1,frame.hoverAppearance.back());
                batch.draw(backFrame.texture(), 0, frame.bottomLayout.baseline,
                        backFrame.logicalWidth()*scale, backFrame.logicalHeight()*scale);
            } finally {
                batch.setBlendFunctionSeparate(src,dst,srcAlpha,dstAlpha);
                batch.setColor(Color.WHITE);
            }
        }
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        frame.controls.drawLabels(view,batch,layout.width(),layout.height(),frame.browser,frame.skin,px,py);
        var modeControl = frame.bottomLayout.control(Selection.MODE).slot();
        SongSelectSkinDrawing.additiveFit(batch,frame.skin,SongSelectSkinAssets.modeImage(
                frame.selectedDifficulty == null ? 0 : frame.selectedDifficulty.mode(),1),
                modeControl.x()+(modeControl.width()-30)/2,modeControl.y()+modeControl.height()-32,30,30,Color.WHITE);
        view.textSmooth(frame.search.isEmpty() ? "Search: type to search" : frame.search + (frame.searchActive && (int)(frame.seconds * 2) % 2 == 0 ? "|" : ""),
                frame.searchX + 9, layout.height() - 73, frame.searchW - 18, .75f, frame.search.isEmpty() ? UiTheme.MUTED : UiTheme.TEXT);
        float labelY = frame.bottomLayout.baseline + frame.bottomLayout.controlHeight * .5f;
        if (!has(Image.BACK)) view.textSmooth("‹  back", frame.bottomLayout.back.x() + 22, labelY,
                frame.bottomLayout.back.width() - 30, 1.2f, UiTheme.TEXT);
        for (var action : Selection.values()) if (!has(action.normal)) {
            var slot = frame.bottomLayout.control(action).slot();
            view.textSmooth(action.name().substring(0,1) + action.name().substring(1).toLowerCase(Locale.ROOT),
                    slot.x()+7,labelY,slot.width()-14,.65f,selectionEnabled(action) ? UiTheme.TEXT : UiTheme.MUTED);
        }
        var importBounds = frame.bottomLayout.importAction;
        boolean importHover = !frame.toolbox.open() && importBounds.contains(px,py) && !frame.importing;
        view.textSmooth("I  Import",importBounds.x()+4,importBounds.y()+importBounds.height()*.55f,
                importBounds.width()-8,.65f,importHover ? actionTint.set(UiTheme.TEXT).mul(frame.pointerPressed ? .8f : 1) : UiTheme.MUTED);
        var status = frame.bottomLayout.status;
        var debug = frame.bottomLayout.debug;
        boolean auxiliaryAboveArtwork = status.y() >= frame.bottom;
        boolean unsupported = frame.selectedDifficulty != null && !frame.supportedMode;
        if (!auxiliaryAboveArtwork || frame.toastSeconds <= 0 && !unsupported) {
            view.textSmooth(frame.browser.visibleCount() + " / " + frame.sets.size() + " local sets",status.x(),status.y()+8,status.width(),.60f,UiTheme.MUTED);
            view.textSmooth("F6  DEBUG AUTO",debug.x(),debug.y()+5,debug.width(),.50f,UiTheme.MUTED);
        }
        if (!frame.toolbox.open() && frame.bottomLayout.control(Selection.OPTIONS).interaction().contains(px,py))
            view.textSmooth("Beatmap Options unavailable",frame.bottomLayout.control(Selection.OPTIONS).slot().x(),frame.bottom+14,220,.62f,UiTheme.MUTED);
        if (frame.toastSeconds > 0) view.textSmooth(frame.toast, 27, frame.bottom + 34, Math.min(430, layout.width() * .4f), UiTheme.META, frame.toastColor);
        view.endText();
        frame.controls.drawMenu(view, layout.width(), layout.height(), frame.browser,px,py);
        SongSelectToolboxOverlay.draw(view,batch,layout,frame.toolbox,frame.skin);
        view.cover(frame.entranceOpacity);
        view.cover(frame.outgoingOpacity);
    }

    private boolean has(Image image) { return frame.skin != null && frame.skin.get(image) != null; }

    private void skinImage(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        batch.setColor(tint);
        batch.draw(frame.skin.get(image).texture(), x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    private void skinImage(Image image, SongSelectToolboxLayout.Bounds bounds, Color tint) {
        skinImage(image, bounds.x(), bounds.y(), bounds.width(), bounds.height(), tint);
    }

    private void skinImage(Image image, SongSelectChrome.Bounds bounds, Color tint) {
        skinImage(image, bounds.x(), bounds.y(), bounds.width(), bounds.height(), tint);
    }

    private void chromeBox(SongSelectToolboxLayout.Bounds bounds, Color tint) {
        view.box(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0, tint);
    }

    private void chromeClip(UiLayout layout, SongSelectChrome.Bounds clip) {
        float scaleX = Gdx.graphics.getBackBufferWidth() / layout.width();
        float scaleY = Gdx.graphics.getBackBufferHeight() / layout.height();
        Gdx.gl.glScissor(Math.round(clip.x() * scaleX), Math.round(clip.y() * scaleY),
                Math.round(clip.width() * scaleX), Math.round(clip.height() * scaleY));
    }

    private void drawTopSkin(UiLayout layout) {
        if (!has(Image.TOP)) return;
        var asset = frame.skin.get(Image.TOP);
        var bounds = SongSelectChrome.top(layout.width(), layout.height(), asset);
        var texture = asset.texture();
        // Extend one edge column, not a repeating 20-pixel decoration. Sampling the
        // column centre avoids linear filtering pulling neighbouring artwork into it.
        if (bounds.width() < layout.width()) {
            float u = 1 - .5f / texture.getWidth();
            batch.draw(texture, bounds.width(), bounds.y(), layout.width() - bounds.width(), bounds.height(), u, 1, u, 0);
        }
        skinImage(Image.TOP, bounds, Color.WHITE);
    }

    private boolean selectionEnabled(Selection action) {
        return !frame.importing && !frame.outgoingPending && action != Selection.OPTIONS
                && (action != Selection.RANDOM || frame.browser.visibleCount() != 0);
    }

    private void drawSelection(Selection action, boolean hover) {
        var geometry = frame.bottomLayout.control(action);
        boolean enabled = selectionEnabled(action);
        float brightness = enabled ? 1 : .54f;
        actionTint.set(brightness,brightness,brightness,hover ? frame.hoverAppearance.alpha(action) : 1);
        skinImage(hover ? action.hover : action.normal,
                (hover ? geometry.hover() : geometry.normal()).image(),actionTint);
    }

    private void drawRows(UiLayout layout) {
        rowRenderer.draw(frame.rowPresentations, new SongSelectRowRenderer.Style(frame.contentWidth, frame.skin, frame.rowFill,
                frame.activeText, frame.inactiveText, frame.showThumbnails));
        if (frame.visibleRows.isEmpty()) {
            view.beginShapes();
            view.box(layout.width() * .59f, frame.bottom + 155, layout.width() * .38f, 66, 0, LEFT);
            view.endShapes();
            view.beginText();
            view.textSmooth(frame.sets.isEmpty() ? "Import a beatmap to begin" : "No matching beatmaps",
                    frame.searchX - 220, frame.bottom + 196, 410, UiTheme.BODY, UiTheme.TEXT);
            view.endText();
        }
    }

    private void drawMetadata(UiLayout layout) {
        BeatmapDifficulty diff = frame.selectedDifficulty;
        if (diff == null) {
            view.textSmooth("SONG SELECT", 21, layout.height() - 26, layout.width() * .52f, UiTheme.TITLE, UiTheme.TEXT);
            return;
        }
        float w = layout.width() * .52f - 28;
        view.textSmoothBold(frame.details.title(), 18, layout.height() - 20, w, 1.05f, UiTheme.TEXT);
        view.textSmooth(frame.details.mapper(), 19, layout.height() - 42, w, .74f, UiTheme.TEXT);
        view.textSmooth(frame.details.summary(), 19, layout.height() - 63, w, .70f, UiTheme.TEXT);
        view.textSmooth(frame.details.stats(), 19, layout.height() - 83, w, .64f, UiTheme.MUTED);
        view.textSmooth(frame.details.status(),
                19, layout.height() - 103, w, .63f, UiTheme.MUTED);
    }

    private void drawScoreShapes(UiLayout layout, float px, float py) {
        var bounds = frame.scoreBounds;
        if (frame.scores.rows().isEmpty()) view.box(bounds.x(),bounds.top()-64,bounds.width(),64,0,LEFT);
        for (int slot = 0; slot < bounds.capacity() && frame.scores.first() + slot < frame.scores.rows().size(); slot++) {
            var row = frame.scores.rows().get(frame.scores.first() + slot);
            boolean selected = row.score().playId().equals(frame.scores.selected());
            boolean hover = !frame.toolbox.open() && bounds.slot(px, py) == slot;
            if (!has(Image.MENU_BUTTON_BACKGROUND)) view.box(bounds.x(), bounds.rowY(slot), bounds.width(), ScoreBrowserBounds.HEIGHT, 0,
                    selected ? SIBLING : hover ? TOP : LEFT);
            if (selected) view.box(bounds.x(), bounds.rowY(slot), 3, ScoreBrowserBounds.HEIGHT, 0, UiTheme.ACCENT);
        }
    }

    private void drawScoreBackgrounds(UiLayout layout) {
        if (!has(Image.MENU_BUTTON_BACKGROUND)) return;
        var bounds = frame.scoreBounds;
        for (int slot = 0; slot < bounds.capacity() && frame.scores.first() + slot < frame.scores.rows().size(); slot++) {
            var row = frame.scores.rows().get(frame.scores.first() + slot);
            boolean selected = row.score().playId().equals(frame.scores.selected());
            boolean hovered = !frame.toolbox.open() && bounds.slot(frame.pointerX, frame.pointerY) == slot;
            // Stable 060013b4: CentreLeft origin and scalar .55, independent of PNG alpha.
            var artwork = SongSelectArtwork.card(bounds.x(), bounds.rowY(slot) + ScoreBrowserBounds.HEIGHT / 2,
                    layout.height(), .55f, frame.skin.get(Image.MENU_BUTTON_BACKGROUND));
            skinImage(Image.MENU_BUTTON_BACKGROUND, artwork, actionTint.set(0, 0, 0, selected || hovered ? .6f : .3f));
        }
    }

    private void drawRanking(UiLayout layout) {
        var bounds = frame.scoreBounds;
        view.textSmooth("Local Rankings", 28, frame.chromeContent.rankingHeaderTop() - 17, bounds.width() - 20, .96f, UiTheme.TEXT);
        view.textSmooth(frame.scores.rows().isEmpty() ? "Local records" : "Personal Best  ·  " + frame.scores.rows().getFirst().value()
                + "  ·  " + frame.scores.rows().getFirst().accuracy(), 28,
                frame.chromeContent.rankingHeaderTop() - 46, bounds.width() - 20, .64f, UiTheme.MUTED);
        if (frame.scores.rows().isEmpty()) {
            String message = frame.selectedDifficulty == null ? (frame.sets.isEmpty() ? "Import a beatmap to view rankings" : "Select a matching difficulty")
                    : frame.scoreStatus == LocalScoreStore.Status.UNAVAILABLE
                    ? "Local score storage unavailable" : "No local scores";
            view.textSmooth(message, 28, bounds.top() - 24, bounds.width() - 20, .72f, UiTheme.MUTED);
        }
        for (int slot = 0; slot < bounds.capacity() && frame.scores.first() + slot < frame.scores.rows().size(); slot++) {
            var row = frame.scores.rows().get(frame.scores.first() + slot);
            float y = bounds.rowY(slot), x = bounds.x();
            view.textSmooth(Integer.toString(frame.scores.first()+slot+1),x+5,y+28,24,.65f,UiTheme.TEXT);
            rowRenderer.drawGrade(row.score().grade(), x + 30, y + 13, 44, 38, UiTheme.TEXT, UiTheme.TEXT);
            float textX = x + 82, width = bounds.width() - 92;
            view.textSmoothBold(row.value(), textX, y + 44, width * .62f, .94f, UiTheme.TEXT);
            view.textSmooth(row.accuracy(), textX + width * .64f, y + 44, width * .36f, .82f, UiTheme.TEXT);
            view.textSmooth(row.combo(), textX, y + 25, width, .73f, UiTheme.TEXT);
            view.textSmooth(row.date(), textX, y + 8, width, .59f, UiTheme.MUTED);
        }
        if (frame.scores.rows().size() > bounds.capacity()) view.textSmooth(
                (frame.scores.first() + 1) + "–" + Math.min(frame.scores.rows().size(), frame.scores.first() + bounds.capacity()) + " / " + frame.scores.rows().size(),
                28, bounds.bottom() - 17, bounds.width() - 20, .60f, UiTheme.MUTED);
        if (frame.scoreStatus != LocalScoreStore.Status.READY)
            view.textSmooth(frame.scoreStatus == LocalScoreStore.Status.PARTIAL
                    ? "Some damaged score records were skipped" : "Local score storage unavailable", 28,
                    bounds.bottom() - 33, bounds.width() - 20, .60f, UiTheme.ERROR);
        BeatmapDifficulty diff = frame.selectedDifficulty;
        if (diff != null && !frame.supportedMode)
            view.textSmooth("This mode cannot be played yet", 22, frame.bottom + 25, layout.width() * .31f - 20, UiTheme.META, UiTheme.ERROR);
    }
}
