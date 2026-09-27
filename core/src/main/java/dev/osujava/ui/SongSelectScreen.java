package dev.osujava.ui;

import dev.osujava.score.LocalScoreStore;
import dev.osujava.score.DifficultyIdentity;
import dev.osujava.ruleset.osu.OsuGrade;

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
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.Locale;

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
    private BeatmapSet metadataSet;
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
    private final SongBrowserModel browser;
    private final ScoreBrowserModel scores;
    private final SongBrowserControls controls = new SongBrowserControls();
    private Map<String, String> groupLabels = Map.of();
    // Transient display indices only; browser identities are authoritative.
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
    private SongSelectChrome.Bottom bottomLayout;
    private SongSelectChrome.Content chromeContent;
    private float chromeWidth = -1, chromeHeight = -1;
    SongSelectChrome.Content chromeBounds(UiLayout layout) {
        if (chromeContent == null || chromeWidth != layout.width() || chromeHeight != layout.height()) {
            chromeContent = SongSelectChrome.content(layout.width(), layout.height(), skin);
            chromeWidth = layout.width(); chromeHeight = layout.height();
        }
        return chromeContent;
    }
    ScoreBrowserBounds scoreBounds(UiLayout layout) {
        var content = chromeBounds(layout);
        return new ScoreBrowserBounds(18, content.bottom() + 48, layout.width() * .35f, content.rankingHeaderTop() - 64);
    }
    private boolean renderedTopProcedural, renderedBottomProcedural;
    boolean renderedChromeProcedural(Image image) {
        return image == Image.TOP ? renderedTopProcedural : renderedBottomProcedural;
    }
    private float viewportHeight;
    private float top, bottom, searchX, searchW, cookieX, cookieY, cookieRadius;

    private record Row(int setIndex, int difficultyIndex, String header, boolean selected, boolean sibling,
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
        browser = new SongBrowserModel(game.library().all());
        scores = new ScoreBrowserModel(game.localScores());
        sets = browser.librarySets();
        cacheRowContent();
        if (preferredSetId != null) {
            for (int i = 0; i < sets.size(); i++) if (sets.get(i).id().equals(preferredSetId)) {
                selectedSetIndex = i;
                selectedDifficultyIndex = Math.max(0, Math.min(preferredDifficulty, sets.get(i).difficulties().size() - 1));
                browser.select(preferredSetId, selectedDifficultyIndex);
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
        chromeContent = null;
        syncBrowser(true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (key == Input.Keys.F2) {
                    perform(SongSelectAction.shortcut(key, Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)));
                    return true;
                }
                if (key == Input.Keys.ESCAPE && controls.open()) { controls.close(); return true; }
                if (key == Input.Keys.BACKSPACE && !search.isEmpty()) {
                    search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
                    ensureVisibleSelection();
                    return true;
                }
                if (searchActive) {
                    if (key == Input.Keys.ESCAPE || key == Input.Keys.ENTER) { searchActive = false; return true; }
                    return false;
                }
                var action = SongSelectAction.shortcut(key, false);
                if (action != null) { perform(action); return true; }
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
                controls.close();
                searchActive = true;
                if (search.codePointCount(0, search.length()) < 80
                        || Character.isLowSurrogate(character) && !search.isEmpty() && Character.isHighSurrogate(search.charAt(search.length() - 1))) {
                    search += character;
                    ensureVisibleSelection();
                }
                return true;
            }
            @Override public boolean scrolled(float amountX, float amountY) {
                if (!Float.isFinite(amountY) || amountY == 0
                        || !usesMouseWheelAt(Gdx.input.getX(), Gdx.input.getY())) return false;
                if (!importing) {
                    UiLayout layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                    var bounds = scoreBounds(layout);
                    if (bounds.contains(layout.pointerX(Gdx.input.getX()), layout.pointerY(Gdx.input.getY()))) scores.scroll(amountY);
                    else carousel.scrollBy(Math.max(-10000, Math.min(10000, amountY)) * carousel.rowHeight());
                }
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

    /** Reserve each browser viewport, including row gaps and empty results. */
    public boolean usesMouseWheelAt(int screenX, int screenY) {
        if (closed || outgoing.pending()) return false;
        if (Gdx.graphics.getWidth() <= 0 || Gdx.graphics.getHeight() <= 0) return false;
        UiLayout layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float x = layout.pointerX(screenX), y = layout.pointerY(screenY);
        var chrome = chromeBounds(layout);
        return scoreBounds(layout).contains(x, y) || x >= layout.width() * .5f && x <= layout.width()
                && y > chrome.bottom() && y < chrome.carouselTop();
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
            var oldSort = browser.sort(); var oldGroup = browser.group();
            if (controls.click(px, py, layout.width(), layout.height(), browser)) {
                if (oldSort != browser.sort() || oldGroup != browser.group()) { refreshBrowserOrder(); }
            }
            else if (px >= searchX && px <= searchX + searchW && py >= layout.height() - 58 && py <= layout.height() - 33) searchActive = true;
            else if (SongSelectAction.bottom(px, py, bottomLayout) != null) {
                var action = SongSelectAction.bottom(px, py, bottomLayout);
                if (action == SongSelectAction.RANDOM) searchActive = false;
                perform(action);
                if (action == SongSelectAction.BACK) return;
            }
            else if (selectedDifficulty() != null && playCookie.hit(px, py)) { playSelected(); return; }
            else {
                searchActive = false;
                int slot = scoreBounds(layout).slot(px, py);
                if (slot >= 0) scores.select(scores.first() + slot);
                else handleRowClick(px, py);
            }
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
        renderedTopProcedural = SongSelectChrome.procedural(skin, Image.TOP);
        renderedBottomProcedural = SongSelectChrome.procedural(skin, Image.BOTTOM);
        if (renderedTopProcedural) view.box(0, layout.height() - 112, layout.width() * .52f, 112, 0, TOP);
        if (renderedTopProcedural) view.box(layout.width() * .55f, top, layout.width() * .45f, layout.height() - top, 0, TOP);

        if (renderedBottomProcedural) view.box(0, 0, layout.width(), bottom, 0, BOTTOM);
        view.endShapes();
        view.beginText();
        drawTopSkin(layout);
        skinImage(Image.BOTTOM, bottomLayout.skinBounds(), Color.WHITE);
        view.endText();
        view.beginShapes();
        if (!has(Image.BACK)) chromeBox(bottomLayout.back(), BACK_PINK);
        if (!has(Image.RANDOM)) chromeBox(bottomLayout.random(),
                !randomEnabled ? LEFT : randomFade > 0 ? SIBLING_HOVER : OTHER);
        if (searchActive || !search.isEmpty()) view.box(searchX, layout.height() - 58, searchW, 25, 0, LEFT);
        view.box(18, chromeContent.rankingHeaderTop() - 28, layout.width() * .35f, 28, 0, TOP);
        drawScoreShapes(layout, px, py);
        if (toastSeconds > 0) view.box(18, bottom + 12, Math.min(450, layout.width() * .42f), 35, 0, BOTTOM);
        view.endShapes();
        drawRows(layout);
        if (selectedDifficulty() != null) {
            playCookie.draw(view, seconds, playCookie.hit(px, py), Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        }
        view.beginText();
        drawAction(Image.BACK, bottomLayout.back(), Color.WHITE);
        Color randomTint = randomEnabled ? Color.WHITE : RANDOM_DISABLED;
        drawAction(Image.RANDOM, bottomLayout.random(), randomTint);
        if (has(Image.RANDOM) && randomEnabled)
            drawAction(Image.RANDOM_OVER, bottomLayout.random(), randomOverlayTint.set(1, 1, 1, randomFade));
        drawMetadata(layout);
        drawRanking(layout);
        controls.drawLabels(view, layout.width(), layout.height(), browser);
        view.textSmooth(search.isEmpty() ? "Search: type to search" : search + (searchActive ? "|" : ""),
                searchX + 9, layout.height() - 51, searchW - 18, .75f, search.isEmpty() ? UiTheme.MUTED : UiTheme.TEXT);
        float labelY = bottomLayout.controlBaseline() + bottomLayout.actionHeight() * .5f;
        if (!has(Image.BACK)) view.textSmooth("‹  back", bottomLayout.back().x() + 22, labelY,
                bottomLayout.back().width() - 30, 1.2f, UiTheme.TEXT);
        var importBounds = bottomLayout.importAction();
        // Local helper: quiet text in the common action slot, without a primary navigation panel.
        view.textSmooth("Import", importBounds.x() + 14, labelY + 5, importBounds.width() - 24, .72f, UiTheme.MUTED);
        view.textSmooth(".osz / .osu", importBounds.x() + 14, labelY - 13, importBounds.width() - 24, .55f, UiTheme.MUTED);
        if (!has(Image.RANDOM)) view.textSmooth("F2 Random", bottomLayout.random().x() + 7, labelY,
                bottomLayout.random().width() - 14, .70f, !randomEnabled ? UiTheme.MUTED : UiTheme.TEXT);
        view.textSmooth("Local Library", layout.width() * .49f, labelY + 7, layout.width() * .20f, .72f, UiTheme.MUTED);
        view.textSmooth(browser.visibleSets().size() + " / " + sets.size() + " sets", layout.width() * .49f, labelY - 12,
                layout.width() * .20f, .62f, UiTheme.MUTED);
        view.textSmooth("F6  DEBUG AUTO", layout.width() * .73f, labelY - 12, 132, .55f, UiTheme.MUTED);
        if (toastSeconds > 0) view.textSmooth(toast, 27, bottom + 34, Math.min(430, layout.width() * .4f), UiTheme.META, toastColor);
        view.endText();
        controls.drawMenu(view, layout.width(), layout.height(), browser);
        toastSeconds = Math.max(0, toastSeconds - Math.max(0, delta));
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { closed = true; thumbnails.close(); playCookie.close(); if (rowFill != null) { rowFill.dispose(); rowFill = null; } if (skin != null) skin.dispose(); }

    private void calculateLayout(UiLayout layout) {
        viewportHeight = layout.height();
        bottomLayout = SongSelectChrome.bottom(layout.width(), layout.height(), skin);
        chromeContent = chromeBounds(layout);
        bottom = chromeContent.bottom();
        top = chromeContent.carouselTop();
        searchW = layout.width() * .36f;
        searchX = layout.width() - searchW - 16;
        var cookie = bottomLayout.cookie();
        cookieRadius = cookie.width() / 2;
        cookieX = cookie.x() + cookieRadius;
        cookieY = cookie.y() + cookieRadius;
        playCookie.bounds(cookieX, cookieY, cookieRadius, bottom);
        scores.capacity(scoreBounds(layout).capacity());
    }

    private List<Row> layoutRows(UiLayout layout, float delta) {
        updateContent(layout);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        Row hit = hitRow(px, py);
        carousel.advance(delta, hit == null || hit.setIndex() < 0 ? null : rowKey(hit.setIndex(), hit.difficultyIndex()));
        List<Row> result = new ArrayList<>();
        for (SongSelectCarousel.Row entry : carousel.rows()) {
            float y = carousel.renderY(entry, top);
            if (y + carousel.rowHeight() < bottom || y > top) continue;
            int setIndex = entry.entry.setIndex(), diffIndex = entry.entry.difficultyIndex();
            boolean selected = setIndex == selectedSetIndex && diffIndex == selectedDifficultyIndex;
            result.add(new Row(setIndex, diffIndex, groupLabels.get(entry.entry.key()), selected, setIndex == selectedSetIndex && !selected,
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
            Map<String, String> labels = new java.util.HashMap<>();
            Map<String, Integer> indices = new java.util.HashMap<>();
            for (int i = 0; i < sets.size(); i++) indices.put(sets.get(i).id(), i);
            for (var entry : browser.entries()) {
                if (entry.kind() == SongBrowserModel.Kind.GROUP_HEADER) {
                    entries.add(new SongSelectCarousel.Entry(entry.key(), -1, -2));
                    labels.put(entry.key(), entry.label());
                } else {
                    int i = indices.get(entry.set().id());
                    int j = entry.difficulty() == null ? -1 : entry.set().difficulties().indexOf(entry.difficulty());
                    entries.add(new SongSelectCarousel.Entry(rowKey(i, j), i, j));
                }
            }
            groupLabels = Map.copyOf(labels);
            carousel.content(entries, viewportHeight, height, height * .96f, height * 1.02f, selectedRowKey());
            contentDirty = false;
            contentWidth = layout.width(); contentViewportHeight = viewportHeight; contentRowHeight = height;
        }
    }

    private String selectedRowKey() { return selectedSet() == null ? null : rowKey(selectedSetIndex, selectedDifficultyIndex); }

    private void refreshSelection(boolean rebuild) {
        contentDirty |= rebuild;
        // Input and wheel arbitration can run before render(), so synchronize the model here too.
        if (contentWidth > 0) updateContent(new UiLayout(contentWidth, viewportHeight, 1));
        carousel.select(selectedRowKey());
        if (contentWidth > 0) visibleRows = layoutRows(new UiLayout(contentWidth, viewportHeight, 1), 0);
        selectBackground();
    }

    private boolean has(Image image) { return skin != null && skin.get(image) != null; }

    private void skinImage(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        game.batch().setColor(tint);
        game.batch().draw(skin.get(image).texture(), x, y, w, h);
        game.batch().setColor(Color.WHITE);
    }

    private void skinImage(Image image, SongSelectChrome.Bounds bounds, Color tint) {
        skinImage(image, bounds.x(), bounds.y(), bounds.width(), bounds.height(), tint);
    }

    private void chromeBox(SongSelectChrome.Bounds bounds, Color tint) {
        view.box(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0, tint);
    }

    private void drawTopSkin(UiLayout layout) {
        if (!has(Image.TOP)) return;
        var asset = skin.get(Image.TOP);
        var bounds = SongSelectChrome.top(layout.width(), layout.height(), asset);
        var texture = asset.texture();
        float edgePixels = Math.min(20 * asset.density(), texture.getWidth());
        float tileWidth = edgePixels / asset.density() * layout.height() / 768f;
        // Edge repetitions go underneath the original. Window clipping handles over-wide artwork.
        for (float x = Math.max(0, bounds.width() - tileWidth); x < layout.width(); x += tileWidth) {
            float width = Math.min(tileWidth, layout.width() - x);
            game.batch().draw(texture, x, bounds.y(), width, bounds.height(),
                    1 - edgePixels / texture.getWidth(), 1,
                    1 - edgePixels / texture.getWidth() + width / tileWidth * edgePixels / texture.getWidth(), 0);
        }
        skinImage(Image.TOP, bounds, Color.WHITE);
    }

    private void drawAction(Image image, SongSelectChrome.Bounds slot, Color tint) {
        if (!has(image)) return;
        var asset = skin.get(image);
        var body = skin.actionBody(image);
        var bounds = SongSelectChrome.actionImage(slot, asset, body);
        // An unusually wider overlay is fitted independently; never distort it to the normal canvas.
        if (bounds.width() > slot.width()) {
            float height = slot.width() * asset.logicalHeight() / asset.logicalWidth();
            bounds = new SongSelectChrome.Bounds(slot.x(), slot.y() - body.bottom() * height, slot.width(), height);
        }
        skinImage(image, bounds, tint);
    }

    private void skinImageFit(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        var asset = skin.get(image);
        float scale = Math.min(w / asset.logicalWidth(), h / asset.logicalHeight());
        float width = asset.logicalWidth() * scale, height = asset.logicalHeight() * scale;
        skinImage(image, x + (w - width) / 2, y, width, height, tint);
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
            view.textSmooth(sets.isEmpty() ? "Import a beatmap to begin" : "No matching beatmaps",
                    searchX - 220, bottom + 196, 410, UiTheme.BODY, UiTheme.TEXT);
            view.endText();
        }
    }

    private void drawRow(Row row) {
        if (row.revealAmount() < .01f) return;
        if (row.setIndex() < 0) {
            // Compact divider at the row center; no beatmap body, thumbnail, hover or play target.
            view.beginShapes();
            view.box(row.x() + 12, row.y() + row.height() / 2 - 13, row.width(), 26, 0, TOP);
            view.endShapes();
            view.beginText();
            view.textSmoothBold(row.header(), row.x() + 22,
                    row.y() + row.height() / 2 + 2, contentWidth - row.x() - 30, .74f, UiTheme.TEXT);
            view.endText();
            return;
        }
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
        var best = child ? game.localScores().best(DifficultyIdentity.of(
                sets.get(row.setIndex()).id(), sets.get(row.setIndex()).difficulties().get(row.difficultyIndex()))) : null;
        if (best != null) {
            drawGrade(best.grade(), x, row.y() + row.height() / 2 - 17, 44, 34, thumbnailTint.set(1, 1, 1, detail.a), detail);
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
        if (metadataDifficulty != diff || metadataSet != set) {
            metadataDifficulty = diff; metadataSet = set;
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

    private void drawScoreShapes(UiLayout layout, float px, float py) {
        var bounds = scoreBounds(layout);
        for (int slot = 0; slot < bounds.capacity() && scores.first() + slot < scores.rows().size(); slot++) {
            var row = scores.rows().get(scores.first() + slot);
            boolean selected = row.score().playId().equals(scores.selected());
            boolean hover = bounds.slot(px, py) == slot;
            view.box(bounds.x(), bounds.rowY(slot), bounds.width(), ScoreBrowserBounds.HEIGHT, 0,
                    selected ? SIBLING : hover ? TOP : LEFT);
            if (selected) view.box(bounds.x(), bounds.rowY(slot), 3, ScoreBrowserBounds.HEIGHT, 0, UiTheme.ACCENT);
        }
    }

    private void drawGrade(OsuGrade grade, float x, float y, float w, float h, Color tint, Color textTint) {
        Image image = switch (grade) {
            case SS -> Image.GRADE_SS; case S -> Image.GRADE_S; case A -> Image.GRADE_A;
            case B -> Image.GRADE_B; case C -> Image.GRADE_C; case D -> Image.GRADE_D;
        };
        if (has(image)) skinImageFit(image, x, y, w, h, tint);
        else view.textSmoothBold(grade.name(), x + 3, y + h * .35f, w - 6, 1.35f, textTint);
    }

    private void drawRanking(UiLayout layout) {
        var bounds = scoreBounds(layout);
        view.textSmooth("Local Rankings", 28, chromeContent.rankingHeaderTop() - 17, bounds.width() - 20, .96f, UiTheme.TEXT);
        view.textSmooth("Score descending · " + scores.rows().size() + " local scores", 28,
                chromeContent.rankingHeaderTop() - 46, bounds.width() - 20, .64f, UiTheme.MUTED);
        if (scores.rows().isEmpty()) {
            String message = selectedDifficulty() == null ? (sets.isEmpty() ? "Import a beatmap to view rankings" : "Select a matching difficulty")
                    : game.localScores().status() == LocalScoreStore.Status.UNAVAILABLE
                    ? "Local score storage unavailable" : "No local scores";
            view.textSmooth(message, 28, bounds.top() - 24, bounds.width() - 20, .72f, UiTheme.MUTED);
        }
        for (int slot = 0; slot < bounds.capacity() && scores.first() + slot < scores.rows().size(); slot++) {
            var row = scores.rows().get(scores.first() + slot);
            float y = bounds.rowY(slot), x = bounds.x();
            drawGrade(row.score().grade(), x + 8, y + 13, 60, 40, UiTheme.TEXT, UiTheme.TEXT);
            float textX = x + 78, width = bounds.width() - 88;
            view.textSmoothBold(row.value(), textX, y + 44, width * .62f, .94f, UiTheme.TEXT);
            view.textSmooth(row.accuracy(), textX + width * .64f, y + 44, width * .36f, .82f, UiTheme.TEXT);
            view.textSmooth(row.combo(), textX, y + 25, width, .73f, UiTheme.TEXT);
            view.textSmooth(row.date(), textX, y + 8, width, .59f, UiTheme.MUTED);
        }
        if (scores.rows().size() > bounds.capacity()) view.textSmooth(
                (scores.first() + 1) + "–" + Math.min(scores.rows().size(), scores.first() + bounds.capacity()) + " / " + scores.rows().size(),
                28, bounds.bottom() - 17, bounds.width() - 20, .60f, UiTheme.MUTED);
        if (game.localScores().status() != LocalScoreStore.Status.READY)
            view.textSmooth(game.localScores().status() == LocalScoreStore.Status.PARTIAL
                    ? "Some damaged score records were skipped" : "Local score storage unavailable", 28,
                    bounds.bottom() - 33, bounds.width() - 20, .60f, UiTheme.ERROR);
        BeatmapDifficulty diff = selectedDifficulty();
        if (diff != null && !game.osuRuleset().supportsMode(diff.mode()))
            view.textSmooth("This mode cannot be played yet", 22, bottom + 25, layout.width() * .31f - 20, UiTheme.META, UiTheme.ERROR);
    }

    private boolean rowHit(Row row, float x, float y) {
        return row.setIndex() >= 0 && row.revealAmount() >= .05f && x >= row.x() && x <= row.x() + row.width() && y >= row.y() && y <= row.y() + row.height();
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
        return SongSelectAction.bottom(x, y, bottomLayout) == SongSelectAction.RANDOM;
    }

    private void randomize() {
        if (importing || outgoing.pending()) return;
        browser.random(); syncBrowser(true); randomPulse = .20f;
    }
    private void previousRandom() {
        if (importing || outgoing.pending()) return;
        browser.previousRandom(); syncBrowser(true);
    }
    private void advance(int direction) {
        BeatmapSet current = selectedSet();
        if (current == null || direction == 0) return;
        int next = selectedDifficultyIndex + direction;
        if (next >= 0 && next < current.difficulties().size()) { selectDifficulty(next); return; }
        int nextSet = browser.visibleSets().indexOf(current) + direction;
        if (nextSet >= 0 && nextSet < browser.visibleSets().size()) {
            var set = browser.visibleSets().get(nextSet);
            selectSet(sets.indexOf(set));
            if (direction < 0) selectDifficulty(set.difficulties().size() - 1);
        }
    }
    private void advanceSet(int direction) {
        int next = browser.visibleSets().indexOf(selectedSet()) + direction;
        if (next >= 0 && next < browser.visibleSets().size()) selectSet(sets.indexOf(browser.visibleSets().get(next)));
    }
    private void ensureVisibleSelection() { browser.search(search); syncBrowser(true); }
    private void selectSet(int index) {
        if (index < 0 || index >= sets.size() || index == selectedSetIndex) return;
        browser.select(sets.get(index).id(), 0); syncBrowser(true);
    }
    private void selectDifficulty(int index) {
        BeatmapSet set = selectedSet();
        if (set == null) return;
        browser.select(set.id(), index); syncBrowser(false);
    }
    private void syncBrowser(boolean rebuild) {
        List<BeatmapSet> next = browser.librarySets();
        if (sets != next) { sets = next; cacheRowContent(); }
        var selected = browser.selectedSet();
        if (selected != null) {
            selectedSetIndex = sets.indexOf(selected);
            selectedDifficultyIndex = selected.difficulties().indexOf(browser.selectedDifficulty());
        }
        scores.target(selected == null ? null : DifficultyIdentity.of(selected.id(), browser.selectedDifficulty()));
        refreshSelection(rebuild);
    }
    // Package-local bridge for deterministic visual capture; production uses the controls.
    void browserMode(SongBrowserModel.Sort sort, SongBrowserModel.Group group) {
        browser.sort(sort); browser.group(group); refreshBrowserOrder();
    }
    private void refreshBrowserOrder() {
        syncBrowser(true);
        carousel.reordered();
        if (contentWidth > 0) visibleRows = layoutRows(new UiLayout(contentWidth, viewportHeight, 1), 0);
    }
    void browserSearch(String query, boolean active) { search = query; searchActive = active; ensureVisibleSelection(); }
    private String rowKey(int setIndex, int difficultyIndex) {
        // Existing carousel keys are display projections; browser keeps persistent difficulty identities.
        return sets.get(setIndex).id() + "#" + difficultyIndex;
    }
    private BeatmapSet selectedSet() { return browser.selectedSet(); }
    private BeatmapDifficulty selectedDifficulty() { return browser.selectedDifficulty(); }
    private void selectBackground() {
        BeatmapSet set = selectedSet();
        BeatmapDifficulty diff = selectedDifficulty();
        Path next = diff != null && diff.backgroundPath() != null ? diff.backgroundPath()
                : set == null ? null : set.backgroundPath();
        if (set == null && !sets.isEmpty()) return;
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
    private void perform(SongSelectAction action) {
        switch (action) {
            case BACK -> goBack();
            case IMPORT -> requestImport();
            case RANDOM -> randomize();
            case PREVIOUS_RANDOM -> previousRandom();
            case PLAY -> playSelected();
            case DEBUG_AUTO -> startSelectedPlay(GameplayRunMode.DEBUG_AUTO);
        }
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
                browser.library(game.library().all());
                browser.select(finished.beatmapSet().id(), 0);
                syncBrowser(true);
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
