package dev.osujava.ui;

import dev.osujava.score.LocalScoreStore;
import dev.osujava.score.DifficultyIdentity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.library.BeatmapImportException;
import dev.osujava.library.ImportResult;
import dev.osujava.skin.SkinConfiguration;
import dev.osujava.skin.SongSelectSkinAssets;
import dev.osujava.skin.SongSelectSkinAssets.Image;
import dev.osujava.skin.SongSelectSkinAssets.Selection;
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
    private static final Color SIBLING = new Color(.25f, .54f, .73f, .92f);
    private static final Color SIBLING_HOVER = new Color(.34f, .66f, .84f, .98f);
    private static final Color SELECTED = new Color(.96f, .95f, .98f, .98f);
    private static final Color DARK_TEXT = new Color(.14f, .10f, .18f, 1f);
    private static final Color BACK_PINK = new Color(.83f, .28f, .55f, 1f);

    private final OsuJavaGame game;
    private final UiView view;
    private final SongSelectRowRenderer rowRenderer;
    private List<SongSelectRowRenderer.Presentation> rowPresentations = List.of();
    private SongSelectSkinAssets skin;
    private Color activeText = DARK_TEXT, inactiveText;
    private final SongSelectToolboxState toolbox = new SongSelectToolboxState();
    private final Color actionTint = new Color(Color.WHITE);
    private final java.util.EnumSet<Selection> renderedSelectionProcedural = java.util.EnumSet.noneOf(Selection.class);
    boolean renderedSelectionProcedural(Selection action) { return renderedSelectionProcedural.contains(action); }
    private final Map<Object, SongSelectRowPresentation.Content> rowContent = new IdentityHashMap<>();
    private final Function<BeatmapDifficulty, OptionalDouble> ratings;
    private BeatmapDifficulty metadataDifficulty;
    private BeatmapSet metadataSet;
    private SongSelectDetails details;
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private Texture rowFill;
    private final BeatmapThumbnails thumbnails = new BeatmapThumbnails();
    private final OsuCookie playCookie = new OsuCookie();
    private final SongSelectCarousel carousel = new SongSelectCarousel();
    private final SongSelectPointer rowPointer = new SongSelectPointer();
    private boolean contentDirty = true;
    private float contentWidth, contentViewportHeight, contentRowHeight;
    private boolean legacyThumbnailPreview;
    void legacyThumbnailPreview(boolean enabled) { legacyThumbnailPreview = enabled; }
    private boolean showThumbnails() { return legacyThumbnailPreview || skin == null || skin.thumbnailsEnabled(); }
    private final SongBrowserModel browser;
    private final ScoreBrowserModel scores;
    private final SongSelectScoreSnapshot scoreSnapshot;
    private final SongBrowserControls controls = new SongBrowserControls();
    private Map<String, String> groupLabels = Map.of();
    // Transient display indices only; browser identities are authoritative.
    private List<BeatmapSet> sets;
    private List<SongSelectRow> visibleRows = List.of();
    private int selectedSetIndex, selectedDifficultyIndex;
    private boolean importing, closed, searchActive;
    private String search = "";
    private String toast = "";
    private Color toastColor = UiTheme.TEXT;
    private float toastSeconds, seconds, setClickGuard;
    private float backgroundFade;
    private Path backgroundPath;
    private SongSelectToolboxLayout bottomLayout;
    private float toolboxWidth = -1, toolboxHeight = -1;
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
        rowRenderer = new SongSelectRowRenderer(game.batch(), view);
        browser = new SongBrowserModel(game.library().all());
        scores = new ScoreBrowserModel(game.localScores());
        scoreSnapshot = new SongSelectScoreSnapshot(game.localScores());
        sets = browser.librarySets();
        cacheRowContent();
        scoreSnapshot.refresh(sets);
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
        bottomLayout = null;
        syncBrowser(true);
        scoreSnapshot.refresh(sets);
        Gdx.input.setInputProcessor(new SongSelectInput(toolbox, controls, new SongSelectInput.Target() {
            @Override public String search() { return search; }
            @Override public boolean searchActive() { return searchActive; }
            @Override public void searchActive(boolean active) { searchActive = active; }
            @Override public void search(String query) { search = query; ensureVisibleSelection(); }
            @Override public void perform(SongSelectAction action) { SongSelectScreen.this.perform(action); }
            @Override public void difficulty(int direction) { advance(direction); }
            @Override public void set(int direction) { advanceSet(direction); }
            @Override public void page(int direction) {
                var previous = browser.selectedSet();
                browser.movePage(direction); syncBrowser(previous != browser.selectedSet());
            }
            @Override public boolean scroll(float amount) { return scrollAtPointer(amount); }
        }));
    }

    private boolean scrollAtPointer(float amount) {
        if (!usesMouseWheelAt(Gdx.input.getX(), Gdx.input.getY())) return false;
        if (!importing) {
            UiLayout layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            var bounds = scoreBounds(layout);
            if (bounds.contains(layout.pointerX(Gdx.input.getX()), layout.pointerY(Gdx.input.getY()))) scores.scroll(amount);
            else carousel.scrollBy(Math.max(-10000, Math.min(10000, amount)) * carousel.rowHeight());
        }
        return true;
    }

    @Override public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        UiLayout layout = UiLayout.fromPixels(width, height);
        calculateLayout(layout);
        if (scoreSnapshot.refresh(sets)) {
            var set = selectedSet();
            scores.target(set == null ? null : DifficultyIdentity.of(set.id(),selectedDifficulty()));
        }
        visibleRows = layoutRows(layout, 0);
    }

    /** Reserve each browser viewport, including row gaps and empty results. */
    public boolean usesMouseWheelAt(int screenX, int screenY) {
        if (closed || outgoing.pending()) return false;
        if (toolbox.open()) return true;
        if (Gdx.graphics.getWidth() <= 0 || Gdx.graphics.getHeight() <= 0) return false;
        UiLayout layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float x = layout.pointerX(screenX), y = layout.pointerY(screenY);
        var chrome = chromeBounds(layout);
        return scoreBounds(layout).contains(x, y) || x >= SongSelectMetrics.wheelLeft(layout.width(), layout.height()) && x <= layout.width()
                && y > chrome.bottom() && y < chrome.carouselTop();
    }

    @Override public void render(float delta) {
        delta = Float.isFinite(delta) ? Math.max(0, Math.min(2, delta)) : 0;
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        if (!update(layout, delta)) return;
        draw(layout, delta);
    }

    /** Input, model synchronization, animation and resource preparation precede drawing. */
    private boolean update(UiLayout layout, float delta) {
        seconds += delta;
        setClickGuard = Math.max(0, setClickGuard - Math.max(0, delta));
        calculateLayout(layout);
        if (scoreSnapshot.refresh(sets)) {
            var set = selectedSet();
            scores.target(set == null ? null : DifficultyIdentity.of(set.id(),selectedDifficulty()));
        }
        thumbnails.advance(delta);
        visibleRows = layoutRows(layout, delta);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            rowPointer.cancel();
            var oldSort = browser.sort(); var oldGroup = browser.group();
            if (toolbox.open()) SongSelectToolboxOverlay.click(toolbox,layout,px,py);
            else if (controls.click(px, py, layout.width(), layout.height(), browser)) {
                if (oldSort != browser.sort() || oldGroup != browser.group()) { refreshBrowserOrder(); }
            }
            else if (px >= searchX && px <= searchX + searchW && py >= layout.height() - 58 && py <= layout.height() - 33) searchActive = true;
            else if (SongSelectAction.bottom(px, py, bottomLayout) != null) {
                var action = SongSelectAction.bottom(px, py, bottomLayout);
                if (action == SongSelectAction.RANDOM && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))) action = SongSelectAction.PREVIOUS_RANDOM;
                if (action == SongSelectAction.RANDOM) searchActive = false;
                perform(action);
                if (action == SongSelectAction.BACK) return false;
            }
            else if (selectedDifficulty() != null && playCookie.hit(px, py)) { playSelected(); return false; }
            else {
                searchActive = false;
                int slot = scoreBounds(layout).slot(px, py);
                if (slot >= 0) scores.select(scores.first() + slot);
                else {
                    var row = hitRow(px, py);
                    rowPointer.press(row == null ? null : rowKey(row.setIndex(), row.difficultyIndex()), px, py);
                }
            }
        }

        if (toolbox.open() || importing || outgoing.pending()) rowPointer.cancel();
        var releasedRow = hitRow(px, py);
        if (rowPointer.update(Gdx.input.isButtonPressed(Input.Buttons.LEFT),
                releasedRow == null ? null : rowKey(releasedRow.setIndex(), releasedRow.difficultyIndex()), px, py) != null)
            handleRowClick(px, py);
        prepareRowPresentations();
        backgroundFade = Math.min(1, backgroundFade + Math.max(0, delta) / .22f);
        return true;
    }

    /** Composition policy is documented in song-select-stable-reference.md. No selection writes. */
    private void draw(UiLayout layout, float delta) {
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        view.clear();
        view.background(thumbnails.resident(backgroundPath), .72f * backgroundFade);
        view.beginShapes();
        view.box(0, 0, layout.width(), layout.height(), 0, DIM);
        renderedTopProcedural = SongSelectChrome.procedural(skin, Image.TOP);
        renderedBottomProcedural = SongSelectChrome.procedural(skin, Image.BOTTOM);
        if (renderedTopProcedural) view.box(0, layout.height() - 112, layout.width() * .52f, 112, 0, TOP);
        if (renderedTopProcedural) view.box(layout.width() * .55f, top, layout.width() * .45f, layout.height() - top, 0, TOP);

        if (renderedBottomProcedural) view.box(0, 0, layout.width(), bottom, 0, BOTTOM);
        view.endShapes();
        // Native-sized artwork can exceed the content reservation. Clip only the chrome
        // pass, flushing before changing scissor state so deferred sprites cannot escape it.
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        try {
            chromeClip(layout, SongSelectChrome.topClip(layout.width(), layout.height()));
            view.beginText();
            drawTopSkin(layout);
            view.endText();
            chromeClip(layout, SongSelectChrome.bottomClip(layout.width(), layout.height()));
            view.beginText();
            skinImage(Image.BOTTOM, bottomLayout.bottomImage, Color.WHITE);
            view.endText();
        } finally { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); }
        view.beginShapes();
        boolean backHover = !toolbox.open() && bottomLayout.backInteraction.contains(px,py);
        boolean backPressed = backHover && Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (!has(Image.BACK)) chromeBox(bottomLayout.back,actionTint.set(BACK_PINK).lerp(SELECTED,backPressed ? .22f : backHover ? .10f : 0));
        renderedSelectionProcedural.clear();
        for (var action : Selection.values()) if (!has(action.normal)) {
            renderedSelectionProcedural.add(action);
            var slot = bottomLayout.control(action).slot();
            boolean hover = !toolbox.open() && bottomLayout.control(action).interaction().contains(px,py);
            chromeBox(slot,!selectionEnabled(action) ? LEFT : hover ? SIBLING_HOVER : OTHER);
        }
        if (searchActive || !search.isEmpty()) view.box(searchX, layout.height() - 58, searchW, 25, 0, LEFT);
        view.box(18, chromeContent.rankingHeaderTop() - 28, layout.width() * .35f, 28, 0, TOP);
        drawScoreShapes(layout, px, py);
        if (toastSeconds > 0) view.box(18, bottom + 12, Math.min(450, layout.width() * .42f), 35, 0, BOTTOM);
        view.endShapes();
        // Composite selection artwork belongs under browser content and the independent Cookie.
        view.beginText();
        for (var action : Selection.values()) drawSelection(action,px,py);
        view.endText();
        drawRows(layout);
        if (selectedDifficulty() != null) {
            playCookie.draw(view, seconds, !toolbox.open() && playCookie.hit(px, py),
                    !toolbox.open() && Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        }
        view.beginText();
        float backBrightness = backPressed ? .78f : backHover ? 1 : .94f;
        skinImage(Image.BACK,bottomLayout.backImage,actionTint.set(backBrightness,backBrightness,backBrightness,1));
        drawMetadata(layout);
        drawRanking(layout);
        controls.drawLabels(view, layout.width(), layout.height(), browser);
        view.textSmooth(search.isEmpty() ? "Search: type to search" : search + (searchActive ? "|" : ""),
                searchX + 9, layout.height() - 51, searchW - 18, .75f, search.isEmpty() ? UiTheme.MUTED : UiTheme.TEXT);
        float labelY = bottomLayout.baseline + bottomLayout.controlHeight * .5f;
        if (!has(Image.BACK)) view.textSmooth("‹  back", bottomLayout.back.x() + 22, labelY,
                bottomLayout.back.width() - 30, 1.2f, UiTheme.TEXT);
        for (var action : Selection.values()) if (!has(action.normal)) {
            var slot = bottomLayout.control(action).slot();
            view.textSmooth(action.name().substring(0,1) + action.name().substring(1).toLowerCase(Locale.ROOT),
                    slot.x()+7,labelY,slot.width()-14,.65f,selectionEnabled(action) ? UiTheme.TEXT : UiTheme.MUTED);
        }
        var importBounds = bottomLayout.importAction;
        boolean importHover = !toolbox.open() && importBounds.contains(px,py) && !importing;
        view.textSmooth("I  Import",importBounds.x()+4,importBounds.y()+importBounds.height()*.55f,
                importBounds.width()-8,.65f,importHover ? actionTint.set(UiTheme.TEXT).mul(Gdx.input.isButtonPressed(Input.Buttons.LEFT) ? .8f : 1) : UiTheme.MUTED);
        var status = bottomLayout.status;
        var debug = bottomLayout.debug;
        boolean auxiliaryAboveArtwork = status.y() >= bottom;
        boolean unsupported = selectedDifficulty() != null && !game.osuRuleset().supportsMode(selectedDifficulty().mode());
        if (!auxiliaryAboveArtwork || toastSeconds <= 0 && !unsupported) {
            view.textSmooth(browser.visibleSets().size() + " / " + sets.size() + " local sets",status.x(),status.y()+8,status.width(),.60f,UiTheme.MUTED);
            view.textSmooth("F6  DEBUG AUTO",debug.x(),debug.y()+5,debug.width(),.50f,UiTheme.MUTED);
        }
        if (!toolbox.open() && bottomLayout.control(Selection.OPTIONS).interaction().contains(px,py))
            view.textSmooth("Beatmap Options unavailable",bottomLayout.control(Selection.OPTIONS).slot().x(),bottom+14,220,.62f,UiTheme.MUTED);
        if (toastSeconds > 0) view.textSmooth(toast, 27, bottom + 34, Math.min(430, layout.width() * .4f), UiTheme.META, toastColor);
        view.endText();
        controls.drawMenu(view, layout.width(), layout.height(), browser);
        SongSelectToolboxOverlay.draw(view,layout,toolbox);
        toastSeconds = Math.max(0, toastSeconds - Math.max(0, delta));
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { closed = true; thumbnails.close(); playCookie.close(); if (rowFill != null) { rowFill.dispose(); rowFill = null; } if (skin != null) skin.dispose(); }

    private void calculateLayout(UiLayout layout) {
        viewportHeight = layout.height();
        if (bottomLayout == null || toolboxWidth != layout.width() || toolboxHeight != layout.height()) {
            bottomLayout = SongSelectToolboxLayout.create(layout.width(),layout.height(),skin);
            toolboxWidth = layout.width(); toolboxHeight = layout.height();
        }
        chromeContent = chromeBounds(layout);
        bottom = chromeContent.bottom();
        top = chromeContent.carouselTop();
        searchW = layout.width() * .36f;
        searchX = layout.width() - searchW - 16;
        var cookie = bottomLayout.cookie;
        cookieRadius = cookie.width() / 2;
        cookieX = cookie.x() + cookieRadius;
        cookieY = cookie.y() + cookieRadius;
        playCookie.bounds(cookieX, cookieY, cookieRadius, bottom);
        scores.capacity(scoreBounds(layout).capacity());
    }

    private List<SongSelectRow> layoutRows(UiLayout layout, float delta) {
        updateContent(layout);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        SongSelectRow hit = toolbox.open() ? null : hitRow(px, py);
        carousel.advance(delta, hit == null || hit.setIndex() < 0 ? null : rowKey(hit.setIndex(), hit.difficultyIndex()));
        List<SongSelectRow> result = new ArrayList<>();
        for (SongSelectCarousel.Row entry : carousel.rows()) {
            float y = carousel.renderY(entry, top);
            if (y + carousel.rowHeight() < bottom || y > top) continue;
            int setIndex = entry.entry.setIndex(), diffIndex = entry.entry.difficultyIndex();
            boolean selected = setIndex == selectedSetIndex && diffIndex == selectedDifficultyIndex;
            result.add(new SongSelectRow(setIndex, diffIndex, groupLabels.get(entry.entry.key()), selected, setIndex == selectedSetIndex && !selected,
                    carousel.renderX(entry, layout.width()), y, rowWidth(layout), carousel.rowHeight(), entry.hoverAmount, selected ? 1 : entry.revealAmount * entry.revealAmount));
        }
        return result;
    }

    private float rowWidth(UiLayout layout) { return SongSelectMetrics.rowWidth(layout.width(), layout.height()); }

    private void updateContent(UiLayout layout) {
        float height = SongSelectMetrics.rowHeight(layout.height());
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
            carousel.content(entries, viewportHeight, height, SongSelectMetrics.rowPitch(layout.height()), SongSelectMetrics.rowPitch(layout.height()),
                    selectedRowKey(), layout.height(), top);
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
        updateDetails();
        selectBackground();
    }

    private boolean has(Image image) { return skin != null && skin.get(image) != null; }

    private void skinImage(Image image, float x, float y, float w, float h, Color tint) {
        if (!has(image)) return;
        game.batch().setColor(tint);
        game.batch().draw(skin.get(image).texture(), x, y, w, h);
        game.batch().setColor(Color.WHITE);
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

    private boolean selectionEnabled(Selection action) {
        return !importing && !outgoing.pending() && action != Selection.OPTIONS
                && (action != Selection.RANDOM || !browser.visibleSets().isEmpty());
    }

    private void drawSelection(Selection action, float px, float py) {
        var geometry = bottomLayout.control(action);
        boolean hover = !toolbox.open() && geometry.interaction().contains(px,py);
        boolean enabled = selectionEnabled(action);
        boolean pressed = hover && enabled && Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        float brightness = !enabled ? .54f : pressed ? .78f : hover && !has(action.hover) ? 1 : .94f;
        actionTint.set(brightness,brightness,brightness,1);
        skinImage(action.normal,geometry.normal().image(),actionTint);
        if (hover) skinImage(action.hover,geometry.hover().image(),actionTint);
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

    /** Resource lookup and score projection happen before any drawing. */
    private void prepareRowPresentations() {
        var paths = new java.util.HashSet<Path>();
        if (backgroundPath != null) paths.add(backgroundPath);
        if (showThumbnails()) for (var row : visibleRows) {
            if (row.setIndex() < 0 || row.revealAmount() < .01f) continue;
            var set = sets.get(row.setIndex());
            var content = rowContent.get(row.difficultyIndex() < 0 ? set : set.difficulties().get(row.difficultyIndex()));
            if (content.thumbnail() != null) paths.add(content.thumbnail());
        }
        thumbnails.prepare(paths);
        var result = new ArrayList<SongSelectRowRenderer.Presentation>(visibleRows.size());
        for (var row : visibleRows) {
            if (row.setIndex() < 0) {
                result.add(new SongSelectRowRenderer.Presentation(row, null, false, null, null, 0));
                continue;
            }
            var set = sets.get(row.setIndex());
            var diff = row.difficultyIndex() < 0 ? null : set.difficulties().get(row.difficultyIndex());
            var content = rowContent.get(diff == null ? set : diff);
            var best = diff == null ? null : scoreSnapshot.best(set, diff);
            boolean played = diff == null ? scoreSnapshot.played(set) : best != null;
            result.add(new SongSelectRowRenderer.Presentation(row, content, played, best == null ? null : best.grade(),
                    showThumbnails() ? thumbnails.resident(content.thumbnail()) : null, thumbnails.opacity(content.thumbnail())));
        }
        rowPresentations = List.copyOf(result);
    }

    private void drawRows(UiLayout layout) {
        rowRenderer.draw(rowPresentations, new SongSelectRowRenderer.Style(contentWidth, skin, rowFill,
                activeText, inactiveText, showThumbnails()), layout, bottom, top);
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

    private void updateDetails() {
        var set = selectedSet();
        var diff = selectedDifficulty();
        if (metadataSet == set && metadataDifficulty == diff) return;
        metadataSet = set; metadataDifficulty = diff;
        details = set == null || diff == null ? null : SongSelectDetails.of(set, diff, rowContent.get(diff).stars());
    }

    private void drawMetadata(UiLayout layout) {
        BeatmapSet set = selectedSet();
        BeatmapDifficulty diff = selectedDifficulty();
        if (set == null || diff == null) {
            view.textSmooth("SONG SELECT", 21, layout.height() - 26, layout.width() * .52f, UiTheme.TITLE, UiTheme.TEXT);
            return;
        }
        float w = layout.width() * .52f - 28;
        view.textSmoothBold(details.title(), 18, layout.height() - 20, w, 1.05f, UiTheme.TEXT);
        view.textSmooth(details.mapper(), 19, layout.height() - 42, w, .74f, UiTheme.TEXT);
        view.textSmooth(details.summary(), 19, layout.height() - 63, w, .70f, UiTheme.TEXT);
        view.textSmooth(details.stats(), 19, layout.height() - 83, w, .64f, UiTheme.MUTED);
        view.textSmooth(details.status(),
                19, layout.height() - 103, w, .63f, UiTheme.MUTED);
    }

    private void drawScoreShapes(UiLayout layout, float px, float py) {
        var bounds = scoreBounds(layout);
        for (int slot = 0; slot < bounds.capacity() && scores.first() + slot < scores.rows().size(); slot++) {
            var row = scores.rows().get(scores.first() + slot);
            boolean selected = row.score().playId().equals(scores.selected());
            boolean hover = !toolbox.open() && bounds.slot(px, py) == slot;
            view.box(bounds.x(), bounds.rowY(slot), bounds.width(), ScoreBrowserBounds.HEIGHT, 0,
                    selected ? SIBLING : hover ? TOP : LEFT);
            if (selected) view.box(bounds.x(), bounds.rowY(slot), 3, ScoreBrowserBounds.HEIGHT, 0, UiTheme.ACCENT);
        }
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
            rowRenderer.drawGrade(row.score().grade(), x + 8, y + 13, 60, 40, UiTheme.TEXT, UiTheme.TEXT);
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

    private SongSelectRow hitRow(float x, float y) {
        return SongSelectRow.hit(visibleRows, x, y, bottom, top);
    }

    private void handleRowClick(float x, float y) {
        SongSelectRow row = hitRow(x, y);
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
    private void randomize() {
        if (importing || outgoing.pending()) return;
        browser.random(); syncBrowser(true);
    }
    private void previousRandom() {
        if (importing || outgoing.pending()) return;
        browser.previousRandom(); syncBrowser(true);
    }
    private void advance(int direction) {
        var previous = browser.selectedSet();
        browser.moveDifficulty(direction); syncBrowser(previous != browser.selectedSet());
    }
    private void advanceSet(int direction) {
        var previous = browser.selectedSet();
        browser.moveSet(direction); syncBrowser(previous != browser.selectedSet());
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
        scoreSnapshot.refresh(sets);
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
    private void perform(SongSelectAction action) {
        if (toolbox.open() || importing || outgoing.pending()) return;
        switch (action) {
            case MODE, MODS -> {
                controls.close(); searchActive = false;
                toolbox.open(action == SongSelectAction.MODE ? SongSelectToolboxState.Overlay.MODE : SongSelectToolboxState.Overlay.MODS);
            }
            case OPTIONS -> showToast("Beatmap Options unavailable in this version.",UiTheme.MUTED);
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
