package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.library.BeatmapImportException;
import dev.osujava.library.ImportResult;
import dev.osujava.skin.SkinConfiguration;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class SongSelectScreen extends ScreenAdapter {
    private static final Color TOP = new Color(.025f, .022f, .045f, .68f);
    private static final Color LEFT = new Color(.025f, .022f, .045f, .24f);
    private static final Color BOTTOM = new Color(.025f, .022f, .045f, .91f);
    private static final Color DIM = new Color(.025f, .022f, .045f, .18f);
    private static final Color OTHER = new Color(.58f, .30f, .49f, .90f);
    private static final Color OTHER_HOVER = new Color(.73f, .38f, .59f, .96f);
    private static final Color SIBLING = new Color(.25f, .54f, .73f, .92f);
    private static final Color SIBLING_HOVER = new Color(.34f, .66f, .84f, .98f);
    private static final Color SELECTED = new Color(.96f, .95f, .98f, .98f);
    private static final Color DARK_TEXT = new Color(.14f, .10f, .18f, 1f);
    private static final Color THUMB_FALLBACK = new Color(.23f, .20f, .31f, 1f);
    private static final Color RANDOM_DISABLED = new Color(.5f, .5f, .5f, .5f);
    private static final Color BACK_PINK = new Color(.83f, .28f, .55f, 1f);

    private static final class Metrics {
        static final float HEADER_HEIGHT = 62;
        static final float BACK_WIDTH = 154;
        static final float IMPORT_X = 172;
        static final float IMPORT_WIDTH = 110;
        static final float RANDOM_X = 298;
        static final float RANDOM_WIDTH = 82;
    }

    private final OsuJavaGame game;
    private final UiView view;
    private SongSelectSkinAssets skin;
    private Color activeText = DARK_TEXT, inactiveText;
    private float randomFade, randomPulse;
    private final Color randomOverlayTint = new Color(Color.WHITE);
    private final Color rowTint = new Color(), thumbnailTint = new Color();
    private final Color primaryTint = new Color(), secondaryTint = new Color(), detailTint = new Color(), starTint = new Color();
    private final Map<Object, SongSelectRowPresentation.Content> rowContent = new IdentityHashMap<>();
    private final Function<BeatmapDifficulty, OptionalDouble> ratings;
    private BeatmapDifficulty metadataDifficulty;
    private String metadataTitle, metadataMapper, metadataSummary, metadataStats, metadataStatus;
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private Texture rowFill;
    private final BeatmapThumbnails thumbnails = new BeatmapThumbnails();
    private final OsuCookie playCookie = new OsuCookie();
    private final SongSelectCarousel carousel = new SongSelectCarousel();
    private boolean contentDirty = true;
    private float contentWidth, contentViewportHeight, contentRowHeight;
    private boolean legacyThumbnailPreview;
    void legacyThumbnailPreview(boolean enabled) { legacyThumbnailPreview = enabled; }
    private boolean showThumbnails() { return legacyThumbnailPreview || skin == null || skin.thumbnailsEnabled(); }
    private List<BeatmapSet> sets;
    private List<Row> visibleRows = List.of();
    private int selectedSetIndex, selectedDifficultyIndex;
    private boolean importing, closed, searchActive;
    private String search = "";
    private String toast = "";
    private Color toastColor = UiTheme.TEXT;
    private float toastSeconds, seconds, setClickGuard;
    private float backgroundFade;
    private Path backgroundPath;
    private float top, bottom, searchX, searchW, cookieX, cookieY, cookieRadius;

    private record Row(int setIndex, int difficultyIndex, boolean selected, boolean sibling,
                       float x, float y, float width, float height, float hoverAmount, float revealAmount) { }

    public SongSelectScreen(OsuJavaGame game) { this(game, null, 0); }
    public SongSelectScreen(OsuJavaGame game, String preferredSetId, int preferredDifficulty) {
        this(game, preferredSetId, preferredDifficulty, null);
    }

    /** The capture harness can supply a resolver with no bundled fallback. */
    SongSelectScreen(OsuJavaGame game, String preferredSetId, int preferredDifficulty, SongSelectSkinAssets skin) {
        this(game, preferredSetId, preferredDifficulty, skin, difficulty -> OptionalDouble.empty());
    }

    /** Receives trusted, already calculated ratings only. Production has no rating source yet. */
    SongSelectScreen(OsuJavaGame game, String preferredSetId, int preferredDifficulty, SongSelectSkinAssets skin,
                     Function<BeatmapDifficulty, OptionalDouble> ratings) {
        this.ratings = ratings;
        this.game = game;
        this.skin = skin;
        view = new UiView(game);
        sets = sortedSets();
        cacheRowContent();
        if (preferredSetId != null) {
            for (int i = 0; i < sets.size(); i++) if (sets.get(i).id().equals(preferredSetId)) {
                selectedSetIndex = i;
                selectedDifficultyIndex = Math.max(0, Math.min(preferredDifficulty, sets.get(i).difficulties().size() - 1));
                break;
            }
        }
    }

    @Override public void show() {
        // GL is absent in navigation-only unit tests. No textures are loaded in render().
        if (skin == null && Gdx.gl != null) skin = new SongSelectSkinAssets(game.skinDirectory(), game.skinFallbackDirectory());
        if (Gdx.gl != null) {
            playCookie.loadGraphics();
            if (rowFill == null) {
                Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                pixel.setColor(Color.WHITE); pixel.fill();
                rowFill = new Texture(pixel); pixel.dispose();
            }
        }
        if (skin != null) {
            if (Gdx.gl != null) skin.prepareStarFallback();
            activeText = textColor(skin.configuration().songSelect().activeText(),
                    has(Image.MENU_BUTTON_BACKGROUND) ? UiTheme.TEXT : DARK_TEXT);
            inactiveText = textColor(skin.configuration().songSelect().inactiveText(), null);
        }
        contentDirty = true;
        selectBackground();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (searchActive) {
                    if (key == Input.Keys.ESCAPE || key == Input.Keys.ENTER) { searchActive = false; return true; }
                    if (key == Input.Keys.BACKSPACE && !search.isEmpty()) {
                        search = search.substring(0, search.length() - 1);
                        ensureVisibleSelection();
                        return true;
                    }
                    return false;
                }
                if (key == Input.Keys.ESCAPE) { goBack(); return true; }
                if (key == Input.Keys.I) { requestImport(); return true; }
                if (key == Input.Keys.F2) { randomize(); return true; }
                if (key == Input.Keys.F6) { startSelectedPlay(GameplayRunMode.DEBUG_AUTO); return true; }
                if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) { playSelected(); return true; }
                if (key == Input.Keys.UP) { advance(-1); return true; }
                if (key == Input.Keys.DOWN) { advance(1); return true; }
                if (key == Input.Keys.PAGE_UP) { advanceSet(-1); return true; }
                if (key == Input.Keys.PAGE_DOWN) { advanceSet(1); return true; }
                if (key == Input.Keys.LEFT || key == Input.Keys.RIGHT) {
                    selectDifficulty(selectedDifficultyIndex + (key == Input.Keys.RIGHT ? 1 : -1));
                    return true;
                }
                return false;
            }
            @Override public boolean keyTyped(char character) {
                if (Character.isISOControl(character)) return false;
                searchActive = true;
                if (search.length() < 80) {
                    search += character;
                    ensureVisibleSelection();
                }
                return true;
            }
            @Override public boolean scrolled(float amountX, float amountY) {
                if (!Float.isFinite(amountY) || amountY == 0
                        || !usesMouseWheelAt(Gdx.input.getX(), Gdx.input.getY())) return false;
                if (!importing) carousel.scrollBy(Math.max(-10000, Math.min(10000, amountY)) * carousel.rowHeight());
                return true;
            }
        });
    }

    @Override public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        UiLayout layout = UiLayout.fromPixels(width, height);
        calculateLayout(layout);
        visibleRows = layoutRows(layout, 0);
    }

    /** Reserve the whole carousel viewport, including row gaps and empty results. */
    public boolean usesMouseWheelAt(int screenX, int screenY) {
        if (closed || outgoing.pending()) return false;
        if (Gdx.graphics.getWidth() <= 0 || Gdx.graphics.getHeight() <= 0) return false;
        UiLayout layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float x = layout.pointerX(screenX), y = layout.pointerY(screenY);
        return x >= layout.width() * .5f && x <= layout.width()
                && y > SongSelectChrome.bottomHeight(layout.height()) && y < layout.height() - Metrics.HEADER_HEIGHT;
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        seconds += Math.min(delta, .05f);
        setClickGuard = Math.max(0, setClickGuard - Math.max(0, delta));
        calculateLayout(layout);
        thumbnails.advance(delta);
        visibleRows = layoutRows(layout, delta);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (px >= searchX && px <= searchX + searchW && py >= top + 4 && py <= top + 29) searchActive = true;
            else if (py < bottom && px < Metrics.BACK_WIDTH) { goBack(); return; }
            else if (py < bottom && px >= Metrics.IMPORT_X && px < Metrics.IMPORT_X + Metrics.IMPORT_WIDTH) { requestImport(); }
            else if (randomHit(px, py)) { searchActive = false; randomize(); }
            else if (selectedDifficulty() != null && playCookie.hit(px, py)) { playSelected(); return; }
            else { searchActive = false; handleRowClick(px, py); }
        }

        view.clear();
        backgroundFade = Math.min(1, backgroundFade + Math.max(0, delta) / .22f);
        view.background(thumbnails.get(backgroundPath), .72f * backgroundFade);
        randomPulse = Math.max(0, randomPulse - Math.max(0, delta));
        boolean randomEnabled = !visibleRows.isEmpty() && !importing && !outgoing.pending();
        float fadeTarget = randomEnabled && (randomHit(px, py) || randomPulse > 0) ? 1 : 0;
        float fadeStep = Math.max(0, delta) / .14f;
        randomFade += Math.max(-fadeStep, Math.min(fadeStep, fadeTarget - randomFade));
        view.beginShapes();
        view.box(0, 0, layout.width(), layout.height(), 0, DIM);
        if (!has(Image.TOP)) view.box(0, layout.height() - 112, layout.width() * .52f, 112, 0, TOP);
        if (!has(Image.TOP)) view.box(layout.width() * .55f, top, layout.width() * .45f, layout.height() - top, 0, TOP);

        if (!has(Image.BOTTOM)) view.box(0, 0, layout.width(), bottom, 0, BOTTOM);
        view.endShapes();
        view.beginText();
        drawTopSkin(layout);
        skinImage(Image.BOTTOM, 0, 0, layout.width(), skinChromeHeight(Image.BOTTOM, layout, bottom), Color.WHITE);
        view.endText();
        view.beginShapes();
        if (!has(Image.BACK)) view.box(0, 0, Metrics.BACK_WIDTH, bottom, 0, BACK_PINK);
        view.box(Metrics.IMPORT_X, 10, Metrics.IMPORT_WIDTH, bottom - 20, 0, OTHER);
        if (!has(Image.RANDOM)) view.box(Metrics.RANDOM_X, 0, Metrics.RANDOM_WIDTH, bottom, 0,
                !randomEnabled ? LEFT : randomFade > 0 ? SIBLING_HOVER : OTHER);
        if (searchActive || !search.isEmpty()) view.box(searchX, top + 4, searchW, 25, 0, LEFT);
        view.box(18, layout.height() - 158, layout.width() * .27f, 28, 0, TOP);
        view.box(18, layout.height() - 205, layout.width() * .27f, 36, 0, LEFT);
        view.box(layout.width() * .48f, 10, layout.width() * .22f, bottom - 20, 0, LEFT);
        if (toastSeconds > 0) view.box(18, bottom + 12, Math.min(450, layout.width() * .42f), 35, 0, BOTTOM);
        view.endShapes();
        drawRows(layout);
        if (selectedDifficulty() != null) {
            playCookie.draw(view, seconds, playCookie.hit(px, py), Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        }
        view.beginText();
        // Fit Back into logical bounds regardless of transparent pixels or image dimensions.
        skinImageFit(Image.BACK, 0, 0, Metrics.BACK_WIDTH, bottom, Color.WHITE);
        Color randomTint = randomEnabled ? Color.WHITE : RANDOM_DISABLED;
        skinImageFit(Image.RANDOM, Metrics.RANDOM_X, 0, Metrics.RANDOM_WIDTH, bottom, randomTint);
        if (has(Image.RANDOM) && randomEnabled) skinImageOverlay(Image.RANDOM, Image.RANDOM_OVER, Metrics.RANDOM_X, 0,
                Metrics.RANDOM_WIDTH, bottom, randomOverlayTint.set(1, 1, 1, randomFade));
        drawMetadata(layout);
        drawRanking(layout);
        view.textSmooth("Group: Sets    Sort: Title", layout.width() * .58f, layout.height() - 22, layout.width() * .40f, .80f, UiTheme.TEXT);
        view.textSmooth(search.isEmpty() ? "Search: type to search" : search + (searchActive ? "|" : ""),
                searchX + 9, top + 11, searchW - 18, .75f, search.isEmpty() ? UiTheme.MUTED : UiTheme.TEXT);
        if (!has(Image.BACK)) view.textSmooth("‹  back", 22, 32, 120, 1.2f, UiTheme.TEXT);
        view.textSmooth("Import", Metrics.IMPORT_X + 14, 43, Metrics.IMPORT_WIDTH - 24, .9f, UiTheme.TEXT);
        view.textSmooth(".osz / .osu", Metrics.IMPORT_X + 14, 24, Metrics.IMPORT_WIDTH - 24, .64f, UiTheme.MUTED);
        if (!has(Image.RANDOM)) view.textSmooth("F2 Random", Metrics.RANDOM_X + 7, 32,
                Metrics.RANDOM_WIDTH - 14, .70f, !randomEnabled ? UiTheme.MUTED : UiTheme.TEXT);
        view.textSmooth("Local Library", layout.width() * .49f, 52, layout.width() * .20f, .90f, UiTheme.TEXT);
        view.textSmooth(sets.size() + " local sets", layout.width() * .49f, 30, layout.width() * .20f, .72f, UiTheme.MUTED);
        view.textSmooth("F6  DEBUG AUTO", layout.width() * .73f, 25, 132, .62f, UiTheme.MUTED);
        if (toastSeconds > 0) view.textSmooth(toast, 27, bottom + 34, Math.min(430, layout.width() * .4f), UiTheme.META, toastColor);
        view.endText();
        toastSeconds = Math.max(0, toastSeconds - Math.max(0, delta));
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { closed = true; thumbnails.close(); playCookie.close(); if (rowFill != null) { rowFill.dispose(); rowFill = null; } if (skin != null) skin.dispose(); }

    private void calculateLayout(UiLayout layout) {
        bottom = SongSelectChrome.bottomHeight(layout.height());
        top = layout.height() - Metrics.HEADER_HEIGHT;
        searchW = layout.width() * .36f;
        searchX = layout.width() - searchW - 16;
        cookieRadius = SongSelectChrome.cookieRadius(layout.height());
        cookieX = SongSelectChrome.cookieX(layout.width(), cookieRadius);
        cookieY = SongSelectChrome.cookieY(cookieRadius);
        playCookie.bounds(cookieX, cookieY, cookieRadius, bottom);
    }

    private List<Row> layoutRows(UiLayout layout, float delta) {
        updateContent(layout);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        Row hit = hitRow(px, py);
        carousel.advance(delta, hit == null ? null : rowKey(hit.setIndex(), hit.difficultyIndex()));
        List<Row> result = new ArrayList<>();
        for (SongSelectCarousel.Row entry : carousel.rows()) {
            float y = carousel.renderY(entry, top);
            if (y + carousel.rowHeight() < bottom || y > top) continue;
            int setIndex = entry.entry.setIndex(), diffIndex = entry.entry.difficultyIndex();
            boolean selected = setIndex == selectedSetIndex && diffIndex == selectedDifficultyIndex;
            result.add(new Row(setIndex, diffIndex, selected, setIndex == selectedSetIndex && !selected,
                    carousel.renderX(entry, layout.width()), y, rowWidth(layout), carousel.rowHeight(), entry.hoverAmount, selected ? 1 : entry.revealAmount * entry.revealAmount));
        }
        return result;
    }

    private float rowWidth(UiLayout layout) { return layout.width() * .50f + 18; }

    private void updateContent(UiLayout layout) {
        float height = has(Image.MENU_BUTTON_BACKGROUND)
                ? SongSelectCarousel.skinRowHeight(skin.get(Image.MENU_BUTTON_BACKGROUND).logicalWidth(),
                    skin.get(Image.MENU_BUTTON_BACKGROUND).logicalHeight(), rowWidth(layout)) * skin.rowBody().height() / skin.rowBody().width() : 80;
        height = Math.max(76, Math.min(88, height));
        float viewportHeight = top - bottom;
        if (contentDirty || contentWidth != layout.width() || contentViewportHeight != viewportHeight || contentRowHeight != height) {
            List<SongSelectCarousel.Entry> entries = new ArrayList<>();
            String query = search.toLowerCase(Locale.ROOT).strip();
            for (int i = 0; i < sets.size(); i++) {
                if (!matches(sets.get(i), query)) continue;
                if (i == selectedSetIndex) {
                    // Library order is the existing difficulty policy; indices remain Gameplay identities.
                    for (int j = 0; j < sets.get(i).difficulties().size(); j++)
                        entries.add(new SongSelectCarousel.Entry(rowKey(i, j), i, j));
                } else entries.add(new SongSelectCarousel.Entry(rowKey(i, -1), i, -1));
            }
            carousel.content(entries, viewportHeight, height, height * .96f, height * 1.02f, selectedRowKey());
            contentDirty = false;
            contentWidth = layout.width(); contentViewportHeight = viewportHeight; contentRowHeight = height;
        }
    }

    private String selectedRowKey() { return selectedSet() == null ? null : rowKey(selectedSetIndex, selectedDifficultyIndex); }

    private void refreshSelection(boolean rebuild) {
        contentDirty |= rebuild;
        // Input and wheel arbitration can run before render(), so synchronize the model here too.
        if (contentWidth > 0) updateContent(new UiLayout(contentWidth, top + Metrics.HEADER_HEIGHT, 1));
        carousel.select(selectedRowKey());
        selectBackground();
    }

    private boolean has(Image image) { return skin != null && skin.get(image) != null; }

    private void skinImage(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        game.batch().setColor(tint);
        game.batch().draw(skin.get(image).texture(), x, y, w, h);
        game.batch().setColor(Color.WHITE);
    }

    private float skinChromeHeight(Image image, UiLayout layout, float fallback) {
        if (!has(image)) return fallback;
        // osujava independently uses a 960-unit skin canvas at the 720-unit UI baseline.
        return Math.min(layout.height() * .34f, skin.get(image).logicalHeight() * layout.height() / 960f);
    }

    private void drawTopSkin(UiLayout layout) {
        if (!has(Image.TOP)) return;
        var asset = skin.get(Image.TOP);
        float h = skinChromeHeight(Image.TOP, layout, 112);
        float w = asset.logicalWidth() * h / asset.logicalHeight();
        var texture = asset.texture();
        if (w < layout.width()) game.batch().draw(texture, w, layout.height() - h, layout.width() - w, h,
                1 - 1f / texture.getWidth(), 1, 1, 0);
        skinImage(Image.TOP, 0, layout.height() - h, w, h, Color.WHITE);
    }

    private void skinImageFit(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        var asset = skin.get(image);
        float scale = Math.min(w / asset.logicalWidth(), h / asset.logicalHeight());
        float width = asset.logicalWidth() * scale, height = asset.logicalHeight() * scale;
        skinImage(image, x + (w - width) / 2, y, width, height, tint);
    }

    private void skinImageOverlay(Image normal, Image overlay, float x, float y, float w, float h, Color tint) {
        var asset = skin.get(normal);
        float scale = Math.min(w / asset.logicalWidth(), h / asset.logicalHeight());
        float width = asset.logicalWidth() * scale, height = asset.logicalHeight() * scale;
        skinImage(overlay, x + (w - width) / 2, y, width, height, tint);
    }

    private static Color textColor(SkinConfiguration.Rgb rgb, Color fallback) {
        return rgb == null ? fallback : new Color(rgb.r(), rgb.g(), rgb.b(), 1);
    }

    private void cacheRowContent() {
        rowContent.clear();
        for (BeatmapSet set : sets) {
            rowContent.put(set, SongSelectRowPresentation.content(set, null, OptionalDouble.empty()));
            for (BeatmapDifficulty diff : set.difficulties())
                rowContent.put(diff, SongSelectRowPresentation.content(set, diff, ratings.apply(diff)));
        }
    }

    private void drawRows(UiLayout layout) {
        // Crop the carousel to its existing logical area, including partially visible rows.
        float scaleX = Gdx.graphics.getBackBufferWidth() / layout.width();
        float scaleY = Gdx.graphics.getBackBufferHeight() / layout.height();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, Math.round(bottom * scaleY), Math.round(layout.width() * scaleX), Math.round((top - bottom) * scaleY));
        try {
            for (Row row : visibleRows) if (!row.selected()) drawRow(row);
            for (Row row : visibleRows) if (row.selected()) drawRow(row);
        } finally { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); }
        if (visibleRows.isEmpty()) {
            view.beginShapes();
            view.box(layout.width() * .59f, bottom + 155, layout.width() * .38f, 66, 0, LEFT);
            view.endShapes();
            view.beginText();
            view.textSmooth(search.isEmpty() ? "Import a beatmap to begin" : "No matching beatmaps",
                    searchX - 220, bottom + 196, 410, UiTheme.BODY, UiTheme.TEXT);
            view.endText();
        }
    }

    private void drawRow(Row row) {
        if (row.revealAmount() < .01f) return;
        Color color = rowTint.set(row.selected() ? SELECTED : row.sibling() ? SIBLING : OTHER);
        if (!row.selected()) color.lerp(row.sibling() ? SIBLING_HOVER : OTHER_HOVER, row.hoverAmount());
        color.a *= row.revealAmount();
        float x = row.x(), y = row.y();
        if (has(Image.MENU_BUTTON_BACKGROUND)) {
            view.beginText();
            var body = skin.rowBody();
            float imageWidth = row.width() / body.width(), imageHeight = row.height() / body.height();
            skinImage(Image.MENU_BUTTON_BACKGROUND, x - body.left() * imageWidth,
                    y - body.bottom() * imageHeight, imageWidth, imageHeight, color);
        } else {
            view.beginShapes();
            view.quad(x, y, x + row.width(), y, x + row.width(), y + row.height(), x + 9, y + row.height(), color);
            view.endShapes();
            view.beginText();
        }
        BeatmapSet set = sets.get(row.setIndex());
        SongSelectRowPresentation.Content content = rowContent.get(row.difficultyIndex() < 0
                ? set : set.difficulties().get(row.difficultyIndex()));
        var geometry = SongSelectRowPresentation.geometry(row.width(), row.height(), contentWidth - x,
                showThumbnails());
        float tx = x + geometry.thumbnailX(), ty = y + geometry.thumbnailY();
        if (showThumbnails()) {
            // Keep body, thumbnail fallback, cover and text in the same sprite batch.
            game.batch().setColor(thumbnailTint.set(THUMB_FALLBACK.r, THUMB_FALLBACK.g, THUMB_FALLBACK.b, row.revealAmount()));
            game.batch().draw(rowFill, tx, ty, geometry.thumbnailWidth(), geometry.thumbnailHeight());
            var texture = thumbnails.get(content.thumbnail());
            float brightness = row.selected() ? 1 : .78f + row.hoverAmount() * .12f;
            game.batch().setColor(brightness, brightness, brightness, row.revealAmount() * thumbnails.opacity(content.thumbnail()));
            view.imageCover(texture, tx, ty, geometry.thumbnailWidth(), geometry.thumbnailHeight());
            game.batch().setColor(Color.WHITE);
        }
        drawRowLabel(row, content, geometry);
        view.endText();
    }

    private void drawRowLabel(Row row, SongSelectRowPresentation.Content content, SongSelectRowPresentation.Geometry geometry) {
        Color base = row.selected() ? activeText : inactiveText != null ? inactiveText : UiTheme.TEXT;
        Color primary = primaryTint.set(base);
        primary.a *= row.revealAmount() * (row.sibling() ? .24f : 1);
        Color secondary = secondaryTint.set(base);
        secondary.a *= row.revealAmount() * (row.sibling() ? .20f : .80f);
        Color detail = detailTint.set(base);
        detail.a *= row.revealAmount() * (row.difficultyIndex() >= 0 ? 1 : .72f);
        float x = row.x() + geometry.textX(), width = geometry.textWidth();
        boolean child = row.difficultyIndex() >= 0;
        view.textSmooth(content.title(), x, row.y() + (child ? geometry.titleY() : row.height() / 2 + 8), width,
                .87f, primary);
        view.textSmooth(content.byline(), x, row.y() + (child ? geometry.bylineY() : row.height() / 2 - 12), width, .65f, secondary);
        if (child) view.textSmoothBold(content.detail(), x, row.y() + geometry.detailY(), width, .90f, detail);
        var stars = content.stars();
        if (stars.present() && width >= stars.width()) drawStars(stars, x,
                row.y() + geometry.starsY(), detail, row.selected());
    }

    private void drawStars(SongSelectRowPresentation.Stars stars, float x, float y, Color tint, boolean selected) {
        var texture = skin == null ? null : skin.starTexture();
        if (texture == null) return;
        float scale = Math.min(15f / texture.getWidth(), 15f / texture.getHeight());
        float w = texture.getWidth() * scale, h = texture.getHeight() * scale;
        for (int i = 0; i < stars.slots(); i++) {
            float sx = x + i * 18 + (15 - w) / 2, sy = y + (15 - h) / 2;
            game.batch().setColor(starTint.set(tint.r, tint.g, tint.b, tint.a * .24f));
            game.batch().draw(texture, sx, sy, w, h);
            float fill = stars.fill(i);
            game.batch().setColor(starTint.set(tint.r, tint.g, tint.b, tint.a * (selected ? 1 : .88f)));
            if (fill > 0) {
                if (skin.configuration().legacyVersion() >= 2.2)
                    game.batch().draw(texture, sx + w * (1 - fill) / 2, sy + h * (1 - fill) / 2, w * fill, h * fill);
                else game.batch().draw(texture, sx, sy, w * fill, h, 0, 1, fill, 0);
            }
        }
        game.batch().setColor(Color.WHITE);
        view.textSmooth(stars.label(), x + stars.slots() * 18 + 3, y + 3, stars.numericWidth() - 3, .62f, tint);
    }

    private void drawMetadata(UiLayout layout) {
        BeatmapSet set = selectedSet();
        BeatmapDifficulty diff = selectedDifficulty();
        if (set == null || diff == null) {
            view.textSmooth("SONG SELECT", 21, layout.height() - 26, layout.width() * .52f, UiTheme.TITLE, UiTheme.TEXT);
            return;
        }
        if (metadataDifficulty != diff) {
            metadataDifficulty = diff;
            metadataTitle = set.title() + " [" + diff.version() + "]";
            metadataMapper = set.artist() + " // Mapped by " + diff.creator();
            long circles = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.CIRCLE).count();
            long sliders = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SLIDER).count();
            long spinners = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SPINNER).count();
            long firstMs = diff.hitObjects().stream().mapToLong(o -> (long) o.timeMs()).min().orElse(0);
            long lastMs = diff.hitObjects().stream().mapToLong(o -> (long) o.endTimeMs()).max().orElse(0);
            String bpm = bpmText(diff);
            metadataSummary = "Length " + formatTime(Math.max(0, lastMs - firstMs)) + "    BPM " + bpm + "    Objects " + diff.hitObjects().size();
            var rating = rowContent.get(diff).stars();
            metadataStatus = "Local beatmap" + (rating.present() ? "    Stars " + rating.label() : "");
            metadataStats = "Circles " + circles + "   Sliders " + sliders + "   Spinners " + spinners
                    + "    OD " + oneDecimal(diff.settings().overallDifficulty())
                    + "   AR " + oneDecimal(diff.settings().approachRate())
                    + "   CS " + oneDecimal(diff.settings().circleSize())
                    + "   HP " + oneDecimal(diff.settings().hpDrainRate());
        }
        float w = layout.width() * .52f - 28;
        view.textSmoothBold(metadataTitle, 18, layout.height() - 20, w, 1.05f, UiTheme.TEXT);
        view.textSmooth(metadataMapper, 19, layout.height() - 42, w, .74f, UiTheme.TEXT);
        view.textSmooth(metadataSummary, 19, layout.height() - 63, w, .70f, UiTheme.TEXT);
        view.textSmooth(metadataStats, 19, layout.height() - 83, w, .64f, UiTheme.MUTED);
        view.textSmooth(metadataStatus,
                19, layout.height() - 103, w, .63f, UiTheme.MUTED);
    }

    private void drawRanking(UiLayout layout) {
        view.textSmooth("Local Rankings", 28, layout.height() - 147, layout.width() * .29f, .96f, UiTheme.TEXT);
        view.textSmooth("No local scores", 28, layout.height() - 192, layout.width() * .29f, .72f, UiTheme.MUTED);
        BeatmapDifficulty diff = selectedDifficulty();
        if (diff != null && !game.osuRuleset().supportsMode(diff.mode()))
            view.textSmooth("This mode cannot be played yet", 22, bottom + 25, layout.width() * .31f - 20, UiTheme.META, UiTheme.ERROR);
    }

    private boolean rowHit(Row row, float x, float y) {
        return row.revealAmount() >= .05f && x >= row.x() && x <= row.x() + row.width() && y >= row.y() && y <= row.y() + row.height();
    }
    private Row hitRow(float x, float y) {
        if (y <= bottom || y >= top) return null;
        // Match compositing order: the selected difficulty is drawn above overlapping rows.
        for (Row row : visibleRows) if (row.selected() && rowHit(row, x, y)) return row;
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            Row row = visibleRows.get(i);
            if (rowHit(row, x, y)) return row;
        }
        return null;
    }

    private void handleRowClick(float x, float y) {
        Row row = hitRow(x, y);
        if (row == null) return;
        if (row.difficultyIndex() >= 0) {
            if (row.setIndex() == selectedSetIndex && row.difficultyIndex() == selectedDifficultyIndex) {
                if (setClickGuard == 0) playSelected();
            }
            else { selectSet(row.setIndex()); selectDifficulty(row.difficultyIndex()); }
        } else {
            selectSet(row.setIndex());
            // Expansion replaces the clicked Set at this position; its double-click must not play.
            setClickGuard = .24f;
        }
    }
    private boolean randomHit(float x, float y) {
        return y >= 0 && y < bottom && x >= Metrics.RANDOM_X && x < Metrics.RANDOM_X + Metrics.RANDOM_WIDTH;
    }

    private void randomize() {
        if (importing || outgoing.pending()) return;
        String query = search.toLowerCase(Locale.ROOT).strip();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) if (matches(sets.get(i), query)) candidates.add(i);
        if (candidates.isEmpty()) return;
        if (candidates.size() > 1) candidates.remove(Integer.valueOf(selectedSetIndex));
        selectSet(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
        randomPulse = .20f;
    }

    private void advance(int direction) {
        if (direction == 0) return;
        BeatmapSet current = selectedSet();
        if (current == null) return;
        int nextDifficulty = selectedDifficultyIndex + direction;
        if (nextDifficulty >= 0 && nextDifficulty < current.difficulties().size()) {
            selectDifficulty(nextDifficulty);
            return;
        }
        String query = search.toLowerCase(Locale.ROOT).strip();
        for (int i = selectedSetIndex + direction; i >= 0 && i < sets.size(); i += direction) {
            if (!matches(sets.get(i), query)) continue;
            selectSet(i);
            if (direction < 0) selectDifficulty(sets.get(i).difficulties().size() - 1);
            return;
        }
    }
    private void advanceSet(int direction) {
        String query = search.toLowerCase(Locale.ROOT).strip();
        for (int i = selectedSetIndex + direction; i >= 0 && i < sets.size(); i += direction)
            if (matches(sets.get(i), query)) { selectSet(i); return; }
    }
    private boolean matches(BeatmapSet set, String query) {
        if (query.isEmpty()) return true;
        return (set.title() + " " + set.artist() + " " + set.creator()).toLowerCase(Locale.ROOT).contains(query)
                || set.difficulties().stream().anyMatch(d -> d.version().toLowerCase(Locale.ROOT).contains(query));
    }
    private void ensureVisibleSelection() {
        String query = search.toLowerCase(Locale.ROOT).strip();
        if (!sets.isEmpty() && !matches(sets.get(selectedSetIndex), query)) {
            for (int i = 0; i < sets.size(); i++) if (matches(sets.get(i), query)) {
                selectSet(i);
                return;
            }
        }
        refreshSelection(true);
    }
    private void selectSet(int index) {
        if (sets.isEmpty()) return;
        int next = Math.max(0, Math.min(sets.size() - 1, index));
        if (next == selectedSetIndex) return;
        selectedSetIndex = next;
        selectedDifficultyIndex = 0;
        refreshSelection(true);
    }
    private void selectDifficulty(int index) {
        BeatmapSet set = selectedSet();
        if (set == null) return;
        int next = Math.max(0, Math.min(set.difficulties().size() - 1, index));
        if (next == selectedDifficultyIndex) return;
        selectedDifficultyIndex = next;
        refreshSelection(false);
    }
    private String rowKey(int setIndex, int difficultyIndex) {
        return sets.get(setIndex).id() + "#" + difficultyIndex;
    }
    private List<BeatmapSet> sortedSets() {
        List<BeatmapSet> result = new ArrayList<>(game.library().all());
        result.sort(Comparator.comparing(BeatmapSet::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BeatmapSet::artist, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    private BeatmapSet selectedSet() { return selectedSetIndex < sets.size() ? sets.get(selectedSetIndex) : null; }
    private BeatmapDifficulty selectedDifficulty() {
        BeatmapSet set = selectedSet();
        return set == null ? null : set.difficulties().get(selectedDifficultyIndex);
    }
    private void selectBackground() {
        BeatmapSet set = selectedSet();
        BeatmapDifficulty diff = selectedDifficulty();
        Path next = diff != null && diff.backgroundPath() != null ? diff.backgroundPath()
                : set == null ? null : set.backgroundPath();
        if (next == null ? backgroundPath != null : !next.equals(backgroundPath)) {
            backgroundPath = next;
            backgroundFade = 0;
        }
    }
    private String oneDecimal(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private String formatTime(long ms) { return (ms / 60000) + ":" + String.format(Locale.ROOT, "%02d", (ms / 1000) % 60); }
    private String bpmText(BeatmapDifficulty diff) {
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (TimingPoint point : diff.timingPoints()) if (point.uninherited() && point.beatLength() > 0) {
            double bpm = 60000 / point.beatLength();
            min = Math.min(min, bpm); max = Math.max(max, bpm);
        }
        if (max == 0) return "—";
        return Math.round(min) == Math.round(max) ? "" + Math.round(max) : Math.round(min) + "–" + Math.round(max);
    }
    private void goBack() { outgoing.request(() -> game.navigate(new MainMenuScreen(game))); }
    private void playSelected() { startSelectedPlay(GameplayRunMode.MANUAL); }

    private void startSelectedPlay(GameplayRunMode runMode) {
        BeatmapSet set = selectedSet(); BeatmapDifficulty difficulty = selectedDifficulty();
        if (set == null) { showToast("Import a beatmap to play.", UiTheme.MUTED); return; }
        if (!game.osuRuleset().supportsMode(difficulty.mode())) {
            showToast("Only osu!standard is playable right now.", UiTheme.ERROR); return;
        }
        if (runMode == GameplayRunMode.DEBUG_AUTO) showToast("Debug Auto Play", UiTheme.ACCENT);
        outgoing.request(() -> game.navigate(new GameplayScreen(game, set, difficulty, runMode)));
    }

    public void requestImport() {
        if (importing || outgoing.pending()) return;
        showToast("Choose an .osz or .osu file...", UiTheme.TEXT);
        game.fileChooser().chooseFile(path -> Gdx.app.postRunnable(() -> startImport(path)));
    }
    private void startImport(Path path) {
        if (closed || importing) return;
        importing = true;
        showToast("Importing " + path.getFileName() + "...", UiTheme.TEXT);
        Thread worker = new Thread(() -> {
            ImportResult result = null;
            String error = null;
            boolean duplicate = false;
            try {
                result = game.importer().importFile(path);
                for (BeatmapSet existing : game.library().all()) {
                    if (existing.id().equals(result.beatmapSet().id())) { duplicate = true; break; }
                }
                game.library().add(result.beatmapSet());
            } catch (BeatmapImportException | RuntimeException e) {
                error = e.getMessage() == null ? "Unknown import error" : e.getMessage();
            }
            ImportResult finished = result;
            String failure = error;
            boolean existing = duplicate;
            Gdx.app.postRunnable(() -> {
                importing = false;
                if (closed) return;
                if (failure != null) { showToast("Import failed: " + failure, UiTheme.ERROR); return; }
                sets = sortedSets();
                cacheRowContent();
                for (int i = 0; i < sets.size(); i++)
                    if (sets.get(i).id().equals(finished.beatmapSet().id())) { selectedSetIndex = i; break; }
                selectedDifficultyIndex = 0;
                refreshSelection(true);
                String message = existing ? "Already imported: " : "Imported: ";
                message += finished.beatmapSet().title();
                if (!finished.warnings().isEmpty()) message += " (some difficulties skipped)";
                showToast(message, UiTheme.SUCCESS);
            });
        }, "osujava-import");
        worker.setDaemon(true);
        worker.start();
    }
    private void showToast(String message, Color color) { toast = message; toastColor = color; toastSeconds = 4; }
}
