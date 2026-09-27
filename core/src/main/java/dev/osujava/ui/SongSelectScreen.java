package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
        static final float TOOLBAR_HEIGHT = 38;
        static final float THUMB_X = 9;
        static final float THUMB_Y = 3;
        static final float THUMB_WIDTH = 96;
        static final float THUMB_HEIGHT = 70;
        static final float BACK_WIDTH = 110;
        static final float IMPORT_X = 116;
        static final float IMPORT_WIDTH = 170;
        static final float RANDOM_X = 298;
        static final float RANDOM_WIDTH = 92;
        static final float ROW_TITLE_SCALE = .84f;
        static final float ROW_DETAIL_SCALE = .66f;
        static final float ROW_MODE_SCALE = .62f;
    }

    private final OsuJavaGame game;
    private final UiView view;
    private SongSelectSkinAssets skin;
    private Color activeText = DARK_TEXT, inactiveText;
    private float randomFade, randomPulse;
    private final Color randomOverlayTint = new Color(Color.WHITE);
    private final Color rowTint = new Color(), thumbnailTint = new Color();
    private final Color primaryTint = new Color(), secondaryTint = new Color();
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private final BeatmapThumbnails thumbnails = new BeatmapThumbnails();
    private final OsuCookie playCookie = new OsuCookie();
    private final SongSelectCarousel carousel = new SongSelectCarousel();
    private boolean contentDirty = true;
    private float contentWidth, contentViewportHeight, contentRowHeight;
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
        this.game = game;
        this.skin = skin;
        view = new UiView(game);
        sets = sortedSets();
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
        if (Gdx.gl != null) playCookie.loadGraphics();
        if (skin != null) {
            activeText = textColor(skin.configuration().songSelect().activeText(), DARK_TEXT);
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
                if (!searchActive || Character.isISOControl(character)) return false;
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
                && y > Metrics.TOOLBAR_HEIGHT && y < layout.height() - Metrics.HEADER_HEIGHT;
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        seconds += Math.min(delta, .05f);
        setClickGuard = Math.max(0, setClickGuard - Math.max(0, delta));
        calculateLayout(layout);
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
        if (!has(Image.TOP)) view.box(0, top, layout.width(), layout.height() - top, 0, TOP);
        view.box(0, bottom, layout.width() * .32f, top - bottom, 0, LEFT);
        if (!has(Image.BOTTOM)) view.box(0, 0, layout.width(), bottom, 0, BOTTOM);
        view.endShapes();
        view.beginText();
        skinImage(Image.TOP, 0, top, layout.width(), layout.height() - top, Color.WHITE);
        skinImage(Image.BOTTOM, 0, 0, layout.width(), bottom, Color.WHITE);
        view.endText();
        view.beginShapes();
        if (!has(Image.BACK)) view.box(0, 0, Metrics.BACK_WIDTH, bottom, 0, BACK_PINK);
        view.box(Metrics.IMPORT_X, 0, Metrics.IMPORT_WIDTH, bottom, 0, OTHER);
        if (!has(Image.RANDOM)) view.box(Metrics.RANDOM_X, 0, Metrics.RANDOM_WIDTH, bottom, 0,
                !randomEnabled ? LEFT : randomFade > 0 ? SIBLING_HOVER : OTHER);
        view.box(searchX, top + 4, searchW, 25, 0, searchActive ? SIBLING : LEFT);
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
        view.textSmooth("LOCAL SETS  ·  TITLE", layout.width() * .58f, top + 11, 235, .72f, UiTheme.TEXT);
        view.textSmooth(search.isEmpty() ? "Search beatmaps" : search + (searchActive ? "|" : ""),
                searchX + 9, top + 11, searchW - 18, .75f, search.isEmpty() ? UiTheme.MUTED : UiTheme.TEXT);
        if (!has(Image.BACK)) view.textSmooth("‹  back", 12, 11, 96, 1.0f, UiTheme.TEXT);
        view.textSmooth("Import .osz / .osu", 126, 11, 151, .75f, UiTheme.TEXT);
        if (!has(Image.RANDOM)) view.textSmooth("F2 Random", Metrics.RANDOM_X + 7, 11,
                Metrics.RANDOM_WIDTH - 14, .70f, !randomEnabled ? UiTheme.MUTED : UiTheme.TEXT);
        view.textSmooth(sets.size() + " local sets", 405, 11, 160, .72f, UiTheme.MUTED);
        view.textSmooth("F6  DEBUG AUTO", layout.width() - 145, 11, 132, .62f, UiTheme.MUTED);
        if (toastSeconds > 0) view.textSmooth(toast, 27, bottom + 34, Math.min(430, layout.width() * .4f), UiTheme.META, toastColor);
        view.endText();
        toastSeconds = Math.max(0, toastSeconds - Math.max(0, delta));
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { closed = true; thumbnails.close(); playCookie.close(); if (skin != null) skin.dispose(); }

    private void calculateLayout(UiLayout layout) {
        bottom = Metrics.TOOLBAR_HEIGHT;
        top = layout.height() - Metrics.HEADER_HEIGHT;
        searchW = Math.min(210, layout.width() * .19f);
        searchX = layout.width() - searchW - 16;
        cookieRadius = Math.min(70, layout.height() * .10f);
        cookieX = layout.width() - cookieRadius * .50f;
        cookieY = cookieRadius * .55f;
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

    private float rowWidth(UiLayout layout) { return layout.width() * .46f + 18; }

    private void updateContent(UiLayout layout) {
        float height = has(Image.MENU_BUTTON_BACKGROUND)
                ? SongSelectCarousel.skinRowHeight(skin.get(Image.MENU_BUTTON_BACKGROUND).logicalWidth(),
                    skin.get(Image.MENU_BUTTON_BACKGROUND).logicalHeight(), rowWidth(layout)) : 76;
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
            carousel.content(entries, viewportHeight, height, has(Image.MENU_BUTTON_BACKGROUND) ? height + 3 : 72, selectedRowKey());
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

    private float thumbnailHeight() { return Math.min(Metrics.THUMB_HEIGHT, carousel.rowHeight() - 6); }

    private float thumbnailWidth() {
        // Wiki v2.2+ ratio, adapted to the existing row height; old skins retain our original layout.
        return has(Image.MENU_BUTTON_BACKGROUND) && skin.configuration().legacyVersion() >= 2.2
                ? thumbnailHeight() * 115f / 85f : Metrics.THUMB_WIDTH * thumbnailHeight() / Metrics.THUMB_HEIGHT;
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
            skinImage(Image.MENU_BUTTON_BACKGROUND, x, y, row.width(), row.height(), color);
            view.endText();
        } else {
            view.beginShapes();
            view.quad(x, y, x + row.width(), y, x + row.width(), y + row.height(), x + 9, y + row.height(), color);
            view.endShapes();
        }
        view.beginShapes();
        view.box(x + Metrics.THUMB_X, y + Metrics.THUMB_Y, thumbnailWidth(), thumbnailHeight(), 0,
                thumbnailTint.set(THUMB_FALLBACK.r, THUMB_FALLBACK.g, THUMB_FALLBACK.b, row.revealAmount()));
        view.endShapes();
        BeatmapSet set = sets.get(row.setIndex());
        BeatmapDifficulty diff = row.difficultyIndex() >= 0 ? set.difficulties().get(row.difficultyIndex()) : set.difficulties().get(0);
        Path path = diff.backgroundPath() != null ? diff.backgroundPath() : set.backgroundPath();
        view.beginText();
        game.batch().setColor(1, 1, 1, row.revealAmount());
        view.imageCover(thumbnails.get(path), x + Metrics.THUMB_X, y + Metrics.THUMB_Y, thumbnailWidth(), thumbnailHeight());
        game.batch().setColor(Color.WHITE);
        drawRowLabel(row);
        view.endText();
    }

    private void drawRowLabel(Row row) {
        BeatmapSet set = sets.get(row.setIndex());
        BeatmapDifficulty diff = row.difficultyIndex() >= 0 ? set.difficulties().get(row.difficultyIndex()) : null;
        Color primary = row.selected() ? activeText : inactiveText != null ? inactiveText : UiTheme.TEXT;
        Color secondary = row.selected() ? activeText : inactiveText != null ? inactiveText : UiTheme.MUTED;
        primary = primaryTint.set(primary); primary.a *= row.revealAmount();
        secondary = secondaryTint.set(secondary); secondary.a *= row.revealAmount();
        float textOffset = Metrics.THUMB_X + thumbnailWidth() + 13;
        float x = row.x() + textOffset, w = Math.max(60, row.width() - textOffset - 18);
        view.textSmooth(set.artist() + " - " + set.title(), x, row.y() + row.height() * .66f, w, Metrics.ROW_TITLE_SCALE, primary);
        view.textSmooth(diff == null ? set.creator() + "  ·  " + set.difficulties().size() + " difficulties"
                        : "[" + diff.version() + "]  mapped by " + set.creator(),
                x, row.y() + row.height() * .40f, w, Metrics.ROW_DETAIL_SCALE, secondary);
        if (diff != null) view.textSmooth(modeName(diff.mode()) + (row.selected()
                        ? "  ·  " + (row.difficultyIndex() + 1) + "/" + set.difficulties().size() : ""),
                x, row.y() + row.height() * .17f, w, Metrics.ROW_MODE_SCALE, secondary);
    }

    private void drawMetadata(UiLayout layout) {
        BeatmapSet set = selectedSet();
        BeatmapDifficulty diff = selectedDifficulty();
        if (set == null || diff == null) {
            view.textSmooth("SONG SELECT", 21, layout.height() - 26, layout.width() * .52f, UiTheme.TITLE, UiTheme.TEXT);
            return;
        }
        float w = layout.width() * .55f - 28;
        view.textSmooth(set.artist() + " - " + set.title() + " [" + diff.version() + "]",
                18, layout.height() - 14, w, .94f, UiTheme.TEXT);
        view.textSmooth("Mapped by " + set.creator(), 19, layout.height() - 29, w, .69f, UiTheme.TEXT);
        long circles = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.CIRCLE).count();
        long sliders = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SLIDER).count();
        long spinners = diff.hitObjects().stream().filter(o -> o.type() == HitObject.Type.SPINNER).count();
        long lastMs = diff.hitObjects().stream().mapToLong(o -> (long) o.endTimeMs()).max().orElse(0);
        String bpm = bpmText(diff);
        view.textSmooth("Map end " + formatTime(lastMs) + "    BPM " + bpm + "    Objects " + diff.hitObjects().size(),
                19, layout.height() - 43, w, .66f, UiTheme.TEXT);
        view.textSmooth("Circles " + circles + "   Sliders " + sliders + "   Spinners " + spinners
                        + "    OD " + oneDecimal(diff.settings().overallDifficulty())
                        + "   AR " + oneDecimal(diff.settings().approachRate())
                        + "   CS " + oneDecimal(diff.settings().circleSize())
                        + "   HP " + oneDecimal(diff.settings().hpDrainRate()),
                19, layout.height() - 57, w, .62f, UiTheme.MUTED);
    }

    private void drawRanking(UiLayout layout) {
        view.textSmooth("LOCAL SCORES", 19, top - 25, layout.width() * .29f, .96f, UiTheme.TEXT);
        view.textSmooth("No local scores", 19, top - 54, layout.width() * .29f, .72f, UiTheme.MUTED);
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
    private String modeName(int mode) { return mode == 0 ? "osu!standard" : "mode " + mode; }
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
