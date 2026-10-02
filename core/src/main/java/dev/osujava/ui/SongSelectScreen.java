package dev.osujava.ui;

import dev.osujava.score.DifficultyIdentity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
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
    private static final Color DARK_TEXT = new Color(.14f, .10f, .18f, 1f);

    private final OsuJavaGame game;
    private final UiView view;
    private final SongSelectRowRenderer rowRenderer;
    private final SongSelectRenderer renderer;
    private final SongSelectViewState viewState = new SongSelectViewState();
    private List<SongSelectRowRenderer.Presentation> rowPresentations = List.of();
    private final Map<SongSelectCarousel.Row, SongSelectRowColourAnimation> rowColours = new IdentityHashMap<>();
    private final Color rowBaseColour = new Color();
    private double rowColourTimeMs;
    private static final class RowStars { SongSelectStarAnimation animation; }
    private final Map<SongSelectCarousel.Row, RowStars> rowStars = new IdentityHashMap<>();
    private final Map<SongSelectCarousel.Row, SongSelectForegroundAnimation> rowForeground = new IdentityHashMap<>();
    private String geometryViewport;
    private SongSelectSkinAssets skin;
    private final SongSelectBackAnimation backAnimation = new SongSelectBackAnimation();
    private SongSelectSkinAssets.SkinTexture backFrame, backGeometry, layoutBackFrame;
    private SongSelectCursor cursor;
    private SongSelectAudio audio;
    private SongSelectPreview preview;
    private Color activeText = DARK_TEXT, inactiveText;
    private final SongSelectToolboxState toolbox = new SongSelectToolboxState();
    boolean renderedSelectionProcedural(Selection action) { return renderer.renderedSelectionProcedural(action); }
    private final Map<BeatmapDifficulty, SongSelectRowPresentation.Content> rowContent = new IdentityHashMap<>();
    private final Function<BeatmapDifficulty, OptionalDouble> ratings;
    private BeatmapDifficulty metadataDifficulty;
    private BeatmapSet metadataSet;
    private SongSelectDetails details;
    private final UiTransition entrance = viewState.entrance;
    private final UiNavigation outgoing = new UiNavigation();
    private Texture rowFill;
    private final BeatmapThumbnails thumbnails = new BeatmapThumbnails();
    private final OsuCookie playCookie = new OsuCookie();
    private final SongSelectCarousel carousel = viewState.carousel;
    private SongSelectInputController input;
    private SongSelectWheelInput wheelInput;
    private boolean contentDirty = true;
    private float contentWidth, contentViewportHeight, contentRowHeight;
    private boolean legacyThumbnailPreview;
    void legacyThumbnailPreview(boolean enabled) { legacyThumbnailPreview = enabled; }
    private boolean showThumbnails() { return legacyThumbnailPreview || skin == null || skin.thumbnailsEnabled(); }
    private final SongBrowserModel browser;
    private final ScoreBrowserModel scores;
    private final SongSelectScoreHover scoreHover = new SongSelectScoreHover();
    private final SongSelectScoreSnapshot scoreSnapshot;
    private final SongBrowserControls controls = new SongBrowserControls();
    private List<SongBrowserModel.Row> browserRows = List.of();
    private List<SongBrowserModel.Entry> browserEntries = List.of();
    private List<SongSelectCarousel.Entry> carouselEntries = List.of();
    private Map<String, String> groupLabels = Map.of();
    // Transient display indices only; browser identities are authoritative.
    private List<BeatmapSet> sets;
    private List<SongSelectRow> visibleRows = List.of();
    private int selectedSetIndex, selectedDifficultyIndex;
    private boolean importing, closed, searchActive;
    private String search = "";
    private String toast = "";
    private Color toastColor = UiTheme.TEXT;
    private float toastSeconds, seconds;
    private float backgroundFade;
    private Path backgroundPath;
    private SongSelectToolboxLayout bottomLayout;
    private SongSelectLayout.Snapshot layoutSnapshot;
    private SongSelectChrome.Content chromeContent;
    private float chromeWidth = -1, chromeHeight = -1;
    private SongSelectLayout.Snapshot layoutSnapshot(UiLayout layout) {
        var currentBack = backGeometry != null ? backGeometry : skin == null ? null : skin.get(Image.BACK);
        if (layoutSnapshot == null || chromeWidth != layout.width() || chromeHeight != layout.height() || layoutBackFrame != currentBack) {
            layoutSnapshot = SongSelectLayout.create(layout, skin,currentBack);
            layoutBackFrame = currentBack;
            chromeWidth = layout.width(); chromeHeight = layout.height();
        }
        return layoutSnapshot;
    }
    SongSelectChrome.Content chromeBounds(UiLayout layout) { return layoutSnapshot(layout).chrome(); }
    ScoreBrowserBounds scoreBounds(UiLayout layout) { return layoutSnapshot(layout).scores(); }
    boolean renderedChromeProcedural(Image image) {
        return renderer.renderedChromeProcedural(image);
    }
    private float viewportHeight;
    private float top, bottom, searchX, searchW;

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
        renderer = new SongSelectRenderer(view, game.batch(), rowRenderer, playCookie);
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
            if (Gdx.gl != null) { skin.prepareStarFallback(); skin.prepareModeFallbacks(); }
            if (Gdx.gl != null && skin.get(Image.CURSOR) != null) {
                if (cursor == null) cursor = new SongSelectCursor(skin);
                cursor.show();
            }
            activeText = textColor(skin.configuration().songSelect().activeText(), Color.BLACK);
            inactiveText = textColor(skin.configuration().songSelect().inactiveText(), null);
        }
        contentDirty = true;
        chromeContent = null;
        layoutSnapshot = null;
        bottomLayout = null;
        syncBrowser(true);
        scoreSnapshot.refresh(sets);
        input = new SongSelectInputController(toolbox, controls, new SongSelectInput.Target() {
            @Override public String search() { return search; }
            @Override public boolean searchActive() { return searchActive; }
            @Override public void searchActive(boolean active) { searchActive = active; }
            @Override public void search(String query) {
                if (audio != null && query.length() > search.length()) audio.typed();
                search = query; ensureVisibleSelection();
            }
            @Override public void perform(SongSelectAction action) { SongSelectScreen.this.perform(action); }
            @Override public void difficulty(int direction) { advance(direction); }
            @Override public void set(int direction) { advanceSet(direction); }
            @Override public void group(int direction) {
                if (importing || outgoing.pending()) return;
                browser.moveGroup(direction); syncBrowser(true);
            }
            @Override public void parentGroup() {
                if (importing || outgoing.pending()) return;
                browser.toggleParentGroup(); syncBrowser(true);
            }
            @Override public void confirm() {
                if (importing || outgoing.pending()) return;
                var identity = browser.selection();
                if (browser.confirmFocus()) { syncBrowser(true); selectionSound(identity); }
                else perform(SongSelectAction.PLAY);
            }
            @Override public void page(int direction) {
                if (importing || outgoing.pending()) return;
                var identity = browser.selection();
                var previous = browser.selectedSet();
                carousel.keyboardNavigation();
                browser.movePage(direction); syncBrowser(previous != browser.selectedSet());
                selectionSound(identity);
            }
            @Override public boolean scroll(float amount) { return scrollAtPointer(amount); }
            @Override public boolean repeatEnabled() { return !closed && !importing && !outgoing.pending() && !game.volumeHud().active(); }
            @Override public void cursor(int x, int y, int button, boolean down) {
                if (button == Input.Buttons.LEFT && down) carousel.pointerPressed();
                if (cursor != null) cursor.event(Gdx.input.getCurrentEventTime(), x, y, button, down);
            }
        });
        wheelInput = new SongSelectWheelInput(input);
        Gdx.input.setInputProcessor(wheelInput);
        carousel.snapOnNextFrame();
        if (preview == null && Gdx.audio != null && Gdx.gl != null) {
            preview = new SongSelectPreview(path -> java.nio.file.Files.isRegularFile(path)
                    ? Gdx.audio.newMusic(Gdx.files.absolute(path.toString())) : null, game.audioVolumes());
        }
        if (audio == null && skin != null && Gdx.audio != null && Gdx.gl != null) {
            audio = new SongSelectAudio(skin.resolver(), file -> Gdx.audio.newSound(file.handle()), game.audioVolumes());
            sound(SongSelectAudio.Cue.EXPAND);
        }
    }

    private boolean scrollAtPointer(float amount) {
        if (!usesMouseWheelAt(Gdx.input.getX(), Gdx.input.getY())) return false;
        if (!importing) {
            UiLayout layout = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            var bounds = scoreBounds(layout);
            if (bounds.contains(layout.pointerX(Gdx.input.getX()), layout.pointerY(Gdx.input.getY()))) scores.scroll(amount);
            else carousel.wheel(amount);
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
        renderer.draw(new SongSelectRenderer.Frame(
                skin,
                activeText,
                inactiveText,
                toolbox,
                new SongSelectRenderer.BrowserView(browser.sort(), browser.group(), browser.visibleSets().size()),
                new SongSelectRenderer.ScoreView(scores.rows(), scores.first(), scores.selected(), scoreHover.snapshot()),
                controls,
                sets,
                visibleRows,
                rowPresentations,
                rowFill,
                details,
                bottomLayout,
                chromeContent,
                searchActive,
                search,
                importing,
                toast,
                toastColor,
                toastSeconds,
                seconds,
                backFrame,
                preview != null && preview.available() ? preview.positionMs() / 1000.0 : seconds,
                backgroundFade,
                bottom,
                top,
                searchX,
                searchW,
                contentWidth,
                carousel.scrollOffset(),
                carousel.maxScroll(),
                viewState.pointerX,
                viewState.pointerY,
                viewState.pointerPressed,
                viewState.hover.appearance(),
                entrance.opacity(),
                outgoing.opacity(),
                outgoing.pending(),
                selectedDifficulty(),
                selectedDifficulty() == null || game.osuRuleset().supportsMode(selectedDifficulty().mode()),
                game.localScores().status(),
                thumbnails.resident(backgroundPath),
                showThumbnails(),
                scoreBounds(layout)), layout);
        if (Boolean.getBoolean("osujava.songSelectGeometry")) drawGeometry(layout, viewState.pointerX, viewState.pointerY);
        if (cursor != null) cursor.draw(game.batch(), game.shapes());
    }

    /** Input, model synchronization, animation and resource preparation precede drawing. */
    private boolean update(UiLayout layout, float delta) {
        wheelInput.dispatch();
        input.advanceKeys(delta);
        viewState.sample(layout, Gdx.input.getX(), Gdx.input.getY(), Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        viewState.advance(delta);
        seconds = viewState.elapsed;
        if (skin != null) {
            backAnimation.update((int)(seconds * 1000),skin.backFrameCount(),skin.configuration().animationFramerate());
            backFrame = skin.backFrameAt(backAnimation.frame());
            backGeometry = skin.backFrameAt(backAnimation.geometryFrame());
        }
        if (preview != null) {
            var difficulty = selectedDifficulty();
            var set = selectedSet();
            Path path = difficulty != null && difficulty.audioPath() != null ? difficulty.audioPath()
                    : set == null ? null : set.audioPath();
            preview.select(path, difficulty == null ? -1 : difficulty.previewTimeMs());
            preview.advance(outgoing.opacity());
        }
        if (cursor != null) cursor.update(layout, Gdx.input.getX(), Gdx.input.getY(), viewState.pointerPressed,
                Gdx.input.isButtonPressed(Input.Buttons.RIGHT), seconds);
        toastSeconds = Math.max(0, toastSeconds - delta);
        calculateLayout(layout);
        if (scoreSnapshot.refresh(sets)) {
            var set = selectedSet();
            scores.target(set == null ? null : DifficultyIdentity.of(set.id(),selectedDifficulty()));
        }
        visibleRows = layoutRows(layout, 0, false);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        var buttons = input.buttons(Gdx.input.isButtonPressed(Input.Buttons.LEFT),
                Gdx.input.isButtonPressed(Input.Buttons.RIGHT), Gdx.input.isButtonPressed(Input.Buttons.MIDDLE), delta * 1000.0);
        if (buttons.physicalPressed()) input.pressPosition(Gdx.input.getX(), Gdx.input.getY());
        boolean rowPressAllowed = true;
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            carousel.pointerPressed();
            rowPressAllowed = false;
            var oldSort = browser.sort(); var oldGroup = browser.group();
            var previousMenu = controls.menu();
            if (toolbox.open()) SongSelectToolboxOverlay.click(toolbox,layout,px,py);
            else if (controls.click(px, py, layout.width(), layout.height(), browser,
                    tab -> showToast(tab.label + " is unavailable.",UiTheme.MUTED))) {
                sound(controls.menu() != null && controls.menu() != previousMenu ? SongSelectAudio.Cue.EXPAND : SongSelectAudio.Cue.CONFIRM);
                if (oldSort != browser.sort() || oldGroup != browser.group()) { refreshBrowserOrder(); }
            }
            else if (layoutSnapshot.search().contains(px, py)) searchActive = true;
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
                if (slot >= 0) {
                    var result = scores.open(scores.first() + slot);
                    var set = selectedSet(); var difficulty = selectedDifficulty();
                    if (result != null && set != null && difficulty != null) {
                        outgoing.request(() -> game.navigate(new ResultsScreen(game, set, difficulty, result)));
                        return false;
                    }
                }
                else rowPressAllowed = true;
            }
        }

        boolean blocked = toolbox.open() || controls.open() || importing || outgoing.pending() || !rowPressAllowed;
        if (blocked) input.cancelPointer();
        else if (buttons.pressed()) {
            var row = hitRow(px, py);
            input.pressPointer(row == null ? null : row.key(), Gdx.input.getX(), Gdx.input.getY(), buttons.right(), carousel);
        }
        var pressedRow = visibleRows.stream().filter(row -> row.key().equals(input.pressedKey())).findFirst().orElse(null);
        if (buttons.released() && input.releasePointer(buttons.left(),
                pressedRow != null && rowPointerVisible(px, py) && pressedRow.boundsContain(px, py)) != null)
            handleRowClick(pressedRow, buttons.context());
        float referenceScale = SongSelectMetrics.carouselScale(layout.height());
        if (!blocked && !outgoing.pending()) input.samplePointer(buttons, Gdx.input.getX(), Gdx.input.getY(),
                layout.scale(), px / referenceScale, (layout.height() - py) / referenceScale, delta, carousel);
        else input.cancelPointer();
        // Sample input before integrating free flight, then publish drawing and hit geometry together.
        visibleRows = layoutRows(layout, delta);
        advanceRowColours(delta);
        advanceRowStars(delta);
        advanceRowForeground(delta);
        prepareRowPresentations(delta);
        if (audio != null) {
            String target = null;
            var cue = SongSelectAudio.Cue.HOVER_CONTROL;
            if (!toolbox.open() && !importing && !outgoing.pending()) {
                var action = SongSelectAction.bottom(px, py, bottomLayout);
                if (action != null) {
                    target = "control:" + action;
                    if (action == SongSelectAction.BACK) cue = SongSelectAudio.Cue.HOVER_BACK;
                } else if (controls.hover(px,py,layout.width(),layout.height()) != null) {
                    target = "browser:" + controls.hover(px,py,layout.width(),layout.height());
                } else if (!controls.open()) {
                    var row = hitRow(px, py);
                    if (row != null) {
                        target = "row:" + row.key();
                        cue = SongSelectAudio.Cue.HOVER_ROW;
                    }
                }
            }
            audio.hover(target, cue);
        }
        backgroundFade = Math.min(1, backgroundFade + Math.max(0, delta) / .22f);
        viewState.hover.advance(delta, toolbox.open() || importing || outgoing.pending() ? null
                : SongSelectAction.bottom(px,py,bottomLayout));
        var rankingBounds = scoreBounds(layout);
        scoreHover.advance(delta, scores.rows(), scores.first(), rankingBounds.capacity(),
                toolbox.open() || controls.open() || importing || outgoing.pending() ? -1 : rankingBounds.slot(px, py));
        return true;
    }

    /** Composition policy is documented in song-select-stable-reference.md. No selection writes. */

    /** Opt-in diagnostics use the exact animated rectangles consumed by rendering and input. */
    private void drawGeometry(UiLayout layout, float px, float py) {
        logGeometryAssets(layout);
        var hovered = hitRow(px, py);
        view.beginShapes();
        for (var row : visibleRows) {
            var color = row.selected() ? Color.GREEN : row == hovered ? Color.CYAN : Color.YELLOW;
            float y = Math.max(bottom, row.y()), end = Math.min(top, row.y() + row.height());
            if (end <= y) continue;
            float width = Math.min(row.width(), layout.width() - row.x());
            view.box(row.x(), y, width, 1, 0, color);
            view.box(row.x(), end - 1, width, 1, 0, color);
            view.box(row.x(), y, 1, end - y, 0, color);
        }
        view.box(0, bottom, layout.width(), 1, 0, Color.MAGENTA);
        view.box(0, top, layout.width(), 1, 0, Color.MAGENTA);
        view.box(0, layout.height() - SongSelectMetrics.SELECTION_Y * SongSelectMetrics.carouselScale(layout.height()),
                layout.width(), 1, 0, Color.GREEN);
        view.endShapes();
        view.beginText();
        float y = layout.height() - 210;
        view.textSmooth("selected=" + selectedSetIndex + ":" + selectedDifficultyIndex
                + " hovered=" + (hovered == null ? "none" : hovered.setIndex() + ":" + hovered.difficultyIndex()),
                20, y, 650, .7f, Color.GREEN);
        y -= 18;
        var set = selectedSet();
        var diff = selectedDifficulty();
        view.textSmooth("active=" + (set == null ? "none" : set.id()) + " / "
                + (diff == null ? "none" : diff.version()), 20, y, 650, .65f, Color.GREEN);
        y -= 18;
        view.textSmooth(String.format(Locale.ROOT, "UI %.0fx%.0f window %dx%d framebuffer %dx%d scale %.2f",
                layout.width(), layout.height(), Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), layout.scale()),
                20, y, 650, .6f, Color.WHITE);
        y -= 18;
        view.textSmooth(String.format(Locale.ROOT, "scroll %.1f -> %.1f velocity %.1f",
                carousel.scrollOffset(), carousel.scrollTarget(), carousel.scrollVelocity()),
                20, y, 650, .6f, Color.WHITE);
        for (var row : visibleRows) view.textSmooth(String.format(Locale.ROOT, "%d:%d (%.1f,%.1f) %.1fx%.1f",
                row.setIndex(), row.difficultyIndex(), row.x(), row.y(), row.width(), row.height()),
                row.x() + 5, Math.min(top, row.y() + row.height()) - 2, row.width() - 10, .5f, Color.YELLOW);
        view.endText();
    }

    private void logGeometryAssets(UiLayout layout) {
        String viewport = Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight() + "/"
                + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight();
        if (viewport.equals(geometryViewport)) return;
        geometryViewport = viewport;
        if (skin == null) return;
        for (var image : Image.values()) {
            var asset = skin.get(image);
            if (asset == null) continue;
            float width, height;
            var selection = Selection.of(image);
            if (selection != null) {
                var control = bottomLayout.control(selection);
                var bounds = (image == selection.normal ? control.normal() : control.hover()).image();
                width = bounds.width(); height = bounds.height();
            } else if (image == Image.TOP) {
                var bounds = SongSelectChrome.top(layout.width(), layout.height(), asset);
                width = bounds.width(); height = bounds.height();
            } else if (image == Image.BOTTOM) {
                width = bottomLayout.bottomImage.width(); height = bottomLayout.bottomImage.height();
            } else if (image == Image.BACK) {
                width = bottomLayout.backImage.width(); height = bottomLayout.backImage.height();
            } else if (image == Image.MENU_BUTTON_BACKGROUND) {
                width = asset.logicalWidth() * layout.height() / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
                height = asset.logicalHeight() * layout.height() / SongSelectMetrics.LEGACY_CANVAS_HEIGHT;
            } else continue; // Stars and grades vary per row; this inventory covers chrome and row canvases.
            Gdx.app.log("SongSelect geometry", String.format(Locale.ROOT,
                    "%s: source %dx%d @%dx -> SD %.1fx%.1f -> UI %.1fx%.1f -> window %.1fx%.1f -> framebuffer %.1fx%.1f",
                    image.basename, asset.texture().getWidth(), asset.texture().getHeight(), asset.density(),
                    asset.logicalWidth(), asset.logicalHeight(), width, height, width * layout.scale(), height * layout.scale(),
                    width * Gdx.graphics.getBackBufferWidth() / layout.width(),
                    height * Gdx.graphics.getBackBufferHeight() / layout.height()));
        }
    }

    private void cancelInput() {
        if (input != null) input.cancel();
        if (wheelInput != null) wheelInput.cancel();
    }
    @Override public void pause() { cancelInput(); }
    @Override public void hide() { cancelInput(); if (preview != null) preview.close(); if (cursor != null) cursor.hide(); }
    @Override public void dispose() { closed = true; cancelInput(); if (preview != null) preview.close(); if (audio != null) audio.close(); if (cursor != null) cursor.close(); thumbnails.close(); playCookie.close(); if (rowFill != null) { rowFill.dispose(); rowFill = null; } if (skin != null) skin.dispose(); }

    private void calculateLayout(UiLayout layout) {
        viewportHeight = layout.height();
        var geometry = layoutSnapshot(layout);
        bottomLayout = geometry.toolbox();
        chromeContent = geometry.chrome();
        bottom = chromeContent.bottom();
        top = chromeContent.carouselTop();
        searchW = geometry.search().width();
        searchX = geometry.search().x();
        var cookie = bottomLayout.cookie;
        float cookieRadius = cookie.width() / 2;
        playCookie.bounds(cookie.x() + cookieRadius, cookie.y() + cookieRadius, cookieRadius, bottom);
        scores.capacity(scoreBounds(layout).capacity());
    }

    private List<SongSelectRow> layoutRows(UiLayout layout, float delta) {
        return layoutRows(layout, delta, true);
    }
    private List<SongSelectRow> layoutRows(UiLayout layout, float delta, boolean advance) {
        updateContent(layout);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        SongSelectRow hit = toolbox.open() || controls.open() || importing || outgoing.pending() ? null : hitRow(px, py);
        carousel.pointerTracking(px / SongSelectMetrics.carouselScale(layout.height()),
                Gdx.input.isButtonPressed(Input.Buttons.LEFT), !toolbox.open() && !importing && !outgoing.pending());
        carousel.focus(browser.focusKey());
        carousel.selectionTrackingTarget(browser.selectionTrackingKey());
        carousel.emphasize(browser.selectedKey());
        if (advance) carousel.advance(delta, hit == null ? null : hit.key(), input != null && input.rightScrolling());
        List<SongSelectRow> result = new ArrayList<>();
        for (int i = carousel.activeStart(); i < carousel.activeEnd(); i++) {
            SongSelectCarousel.Row entry = carousel.allRows().get(i);
            if (!carousel.presents(entry)) continue;
            float y = carousel.renderY(entry, top);
            // Resident buffer rows may have artwork extending beyond their body into the viewport.
            int setIndex = entry.entry.setIndex(), diffIndex = entry.entry.difficultyIndex();
            boolean selected = setIndex == selectedSetIndex && diffIndex == selectedDifficultyIndex;
            var target = carousel.targetPosition(entry.logicalIndex, layout.width(), top);
            result.add(new SongSelectRow(setIndex, diffIndex, groupLabels.get(entry.entry.key()), selected, setIndex == selectedSetIndex && !selected,
                    carousel.renderX(entry, layout.width()), y, rowWidth(layout), carousel.rowHeight(), entry.hoverAmount, selected ? 1 : entry.revealAmount * entry.revealAmount, entry.logicalIndex, target[0], target[1], entry.entry.key(),
                    browser.row(entry.entry.key()).group() && browser.row(entry.entry.key()).expanded, entry.focusAmount));
        }
        return result;
    }

    private float rowWidth(UiLayout layout) { return SongSelectMetrics.rowWidth(layout.width(), layout.height()); }

    private void updateContent(UiLayout layout) {
        float height = SongSelectMetrics.rowHeight(layout.height());
        float viewportHeight = top - bottom;
        if (contentDirty || browserRows != browser.rows() || browserEntries != browser.entries()
                || contentWidth != layout.width() || contentViewportHeight != viewportHeight || contentRowHeight != height) {
            if (browserRows != browser.rows()) {
                List<SongSelectCarousel.Entry> entries = new ArrayList<>();
                Map<String, String> labels = new java.util.HashMap<>();
                Map<String, Integer> indices = new java.util.HashMap<>();
                for (int i = 0; i < sets.size(); i++) indices.put(sets.get(i).id(), i);
                for (var row : browser.rows()) {
                    boolean expanded = row.state.ordinal() >= SongBrowserModel.RowState.EXPANDED.ordinal();
                    if (row.group()) {
                        entries.add(new SongSelectCarousel.Entry(row.key, -1, -2, row.key, expanded, row.visible()));
                        int count = row.matchingChildren;
                        labels.put(row.key, row.label + " (" + count + (count == 1 ? " beatmap)" : " beatmaps)"));
                    } else {
                        int i = indices.get(row.set.id());
                        int j = row.state == SongBrowserModel.RowState.COLLAPSED ? -1 : row.set.difficulties().indexOf(row.difficulty);
                        entries.add(new SongSelectCarousel.Entry(row.key, i, j, row.set.id(), expanded, row.visible()));
                    }
                }
                groupLabels = Map.copyOf(labels);
                carouselEntries = entries;
            } else {
                // Selection/group state changes preserve order, set indices and metadata.
                // Only the old/new family changes its immutable carousel entries.
                for (int i = 0; i < browser.rows().size(); i++) {
                    var row = browser.rows().get(i);
                    var previous = carouselEntries.get(i);
                    boolean expanded = row.state.ordinal() >= SongBrowserModel.RowState.EXPANDED.ordinal();
                    int difficulty = row.group() ? -2 : row.state == SongBrowserModel.RowState.COLLAPSED ? -1
                            : previous.difficultyIndex() >= 0 ? previous.difficultyIndex() : row.set.difficulties().indexOf(row.difficulty);
                    if (previous.expanded() != expanded || previous.visible() != row.visible()
                            || previous.difficultyIndex() != difficulty)
                        carouselEntries.set(i, new SongSelectCarousel.Entry(previous.key(), previous.setIndex(), difficulty,
                                previous.familyKey(), expanded, row.visible()));
                }
            }
            browserRows = browser.rows();
            browserEntries = browser.entries();
            carousel.content(carouselEntries, viewportHeight, height, SongSelectMetrics.rowPitch(layout.height()),
                    selectedRowKey(), layout.height(), top);
            contentDirty = false;
            contentWidth = layout.width(); contentViewportHeight = viewportHeight; contentRowHeight = height;
        }
    }

    private String selectedRowKey() {
        return browser.scrollTargetKey();
    }

    private void refreshSelection(boolean rebuild) {
        contentDirty |= rebuild;
        // Input and wheel arbitration can run before render(), so synchronize the model here too.
        if (contentWidth > 0) updateContent(new UiLayout(contentWidth, viewportHeight, 1));
        carousel.select(selectedRowKey());
        if (contentWidth > 0) visibleRows = layoutRows(new UiLayout(contentWidth, viewportHeight, 1), 0);
        updateDetails();
        selectBackground();
    }

    private static Color textColor(SkinConfiguration.Rgb rgb, Color fallback) {
        return rgb == null ? fallback : new Color(rgb.r(), rgb.g(), rgb.b(), 1);
    }

    private void cacheRowContent() {
        rowContent.clear();
        for (BeatmapSet set : sets) {
            for (BeatmapDifficulty diff : set.difficulties())
                rowContent.put(diff, SongSelectRowPresentation.content(set, diff, ratings.apply(diff)));
        }
    }

    private boolean groupContainsSelection(String key) {
        var selected = browser.row(browser.selectedKey());
        return selected != null && !selected.excluded && selected.parent == browser.row(key);
    }

    private boolean rowPlayed(SongSelectCarousel.Entry entry) {
        if (entry.header()) return false;
        var set = sets.get(entry.setIndex());
        return entry.difficultyIndex() < 0 ? scoreSnapshot.played(set)
                : scoreSnapshot.best(set, set.difficulties().get(entry.difficultyIndex())) != null;
    }

    /** Retain colour clocks for resident sprites, including those outside the drawing viewport. */
    private void advanceRowColours(float delta) {
        double frameMs = Float.isFinite(delta) ? Math.max(0, delta * 1000.0) : 0;
        rowColourTimeMs += frameMs;
        for (var row : carousel.residentRows()) {
            var entry = row.entry;
            var model = browser.row(entry.key());
            SongSelectRowColours.base(rowBaseColour, entry.header(), model.expanded,
                    model.state == SongBrowserModel.RowState.SELECTED,
                    model.state == SongBrowserModel.RowState.EXPANDED, rowPlayed(entry),
                    entry.header() && groupContainsSelection(entry.key()));
            rowColours.computeIfAbsent(row, ignored -> new SongSelectRowColourAnimation()).update(
                    model.state.ordinal(), Color.rgba8888(rowBaseColour), entry.key().equals(browser.focusKey()),
                    entry.key().equals(carousel.hoverKey()), (long) rowColourTimeMs, (int) frameMs);
        }
        rowColours.keySet().retainAll(carousel.residentRows());
    }

    private void advanceRowStars(float delta) {
        long now = (long) rowColourTimeMs;
        int frameMs = Float.isFinite(delta) ? (int) Math.max(0, delta * 1000.0) : 0;
        var asset = skin == null ? null : skin.get(Image.STAR);
        boolean cropped = croppedStars();
        int width = asset == null ? 40 : (int) asset.logicalWidth();
        for (var row : carousel.residentRows()) {
            if (row.entry.header()) continue;
            boolean created = !rowStars.containsKey(row);
            var state = rowStars.computeIfAbsent(row, ignored -> new RowStars());
            var model = browser.row(row.entry.key());
            if (model.state.ordinal() >= SongBrowserModel.RowState.SINGLETON.ordinal()) {
                if (state.animation == null) state.animation = new SongSelectStarAnimation(cropped, width);
                state.animation.update(rowContent.get(model.difficulty).stars(), created && row.instantSprites, now, frameMs);
            } else {
                // 06003267 clears the manager each frame; 06000fd2 removes these pairs
                // before resubmission. Its fade transforms do not create visible afterimages.
                state.animation = null;
            }
        }
        rowStars.keySet().removeIf(row -> row.entry.header() || !carousel.residentRows().contains(row));
    }

    private boolean croppedStars() {
        var background = skin == null ? null : skin.get(Image.MENU_BUTTON_BACKGROUND);
        return SongSelectStarAnimation.cropped(skin == null ? 2.2 : skin.configuration().legacyVersion(),
                background == null || background.file().provider() == dev.osujava.skin.SkinAssetResolver.Provider.BUNDLED);
    }

    private void advanceRowForeground(float delta) {
        int frameMs = Float.isFinite(delta) ? (int) Math.max(0, delta * 1000.0) : 0;
        for (var row : carousel.residentRows()) {
            boolean created = !rowForeground.containsKey(row);
            rowForeground.computeIfAbsent(row, ignored -> new SongSelectForegroundAnimation()).update(
                    browser.row(row.entry.key()).state.ordinal(), created && row.instantSprites,
                    (long) rowColourTimeMs, frameMs);
        }
        rowForeground.keySet().retainAll(carousel.residentRows());
    }

    /** Resource lookup and score projection happen before any drawing. */
    private void prepareRowPresentations(float delta) {
        long now = (long) rowColourTimeMs;
        int frameMs = Float.isFinite(delta) ? (int) Math.max(0, delta * 1000.0) : 0;
        var paths = new java.util.HashSet<Path>();
        var thumbnailRows = new java.util.HashSet<String>();
        if (backgroundPath != null) paths.add(backgroundPath);
        if (showThumbnails()) for (var row : visibleRows) {
            if (row.setIndex() < 0 || row.revealAmount() < .01f) continue;
            var content = rowContent.get(browser.row(row.key()).difficulty);
            if (content.thumbnail() != null && rowForeground.get(carousel.row(row.key())).requestThumbnail(now)) {
                paths.add(content.thumbnail());
                thumbnailRows.add(row.key());
            }
        }
        thumbnails.prepare(paths);
        var result = new ArrayList<SongSelectRowRenderer.Presentation>(visibleRows.size());
        for (var row : visibleRows) {
            var resident = carousel.row(row.key());
            var animation = rowForeground.get(resident);
            int colour = rowColours.get(resident).rgba();
            if (row.setIndex() < 0) {
                boolean containsSelection = groupContainsSelection(row.key());
                result.add(new SongSelectRowRenderer.Presentation(row, null, false, null, null, rowGeometry(row, false, false), containsSelection,
                        colour, SongSelectStarAnimation.Snapshot.EMPTY, animation.snapshot()));
                continue;
            }
            var set = sets.get(row.setIndex());
            // A collapsed card still owns its representative difficulty's sprites, including fading details.
            var diff = browser.row(row.key()).difficulty;
            var content = rowContent.get(diff);
            var best = scoreSnapshot.best(set, diff);
            boolean played = row.difficultyIndex() < 0 ? scoreSnapshot.played(set) : best != null;
            var texture = thumbnailRows.contains(row.key()) ? thumbnails.resident(content.thumbnail()) : null;
            if (texture != null) animation.thumbnailLoaded(now, frameMs);
            var starState = rowStars.get(resident);
            var stars = starState.animation == null ? SongSelectStarAnimation.Snapshot.EMPTY : starState.animation.snapshot();
            boolean gradeImage = best != null && skin != null && skin.get(SongSelectRowRenderer.gradeImage(best.grade())) != null;
            result.add(new SongSelectRowRenderer.Presentation(row, content, played, best == null ? null : best.grade(),
                    texture, rowGeometry(row, gradeImage, content.mode() > 0),
                    false, colour, stars, animation.snapshot()));
        }
        rowPresentations = List.copyOf(result);
    }

    private SongSelectLayout.RowGeometry rowGeometry(SongSelectRow row, boolean grade, boolean mode) {
        return SongSelectLayout.row(row, row.logicalIndex(), row.targetX(), row.targetY(),
                contentWidth, bottom, top, showThumbnails(), grade, mode, croppedStars());
    }

    private void updateDetails() {
        var set = selectedSet();
        var diff = selectedDifficulty();
        if (metadataSet == set && metadataDifficulty == diff) return;
        metadataSet = set; metadataDifficulty = diff;
        details = set == null || diff == null ? null : SongSelectDetails.of(set, diff, rowContent.get(diff).stars());
    }

    private SongSelectRow hitRow(float x, float y) {
        return rowPointerVisible(x, y) ? SongSelectRow.hit(visibleRows, x, y, bottom, top) : null;
    }

    private boolean rowPointerVisible(float x, float y) {
        return SongSelectRow.inViewport(x, y, contentWidth, bottom, top);
    }

    private void handleRowClick(float x, float y) {
        handleRowClick(hitRow(x, y), false);
    }
    private void handleRowClick(SongSelectRow row, boolean context) {
        var identity = browser.selection();
        if (row == null) return;
        if (row.group()) {
            browser.toggleGroup(row.key());
            syncBrowser(true);
            sound(SongSelectAudio.Cue.CONFIRM);
            return;
        }
        if (row.difficultyIndex() >= 0) {
            if (row.setIndex() == selectedSetIndex && row.difficultyIndex() == selectedDifficultyIndex) {
                if (!context) playSelected();
            }
            else { selectSet(row.setIndex()); selectDifficulty(row.difficultyIndex()); }
        } else {
            if (row.key() == null) selectSet(row.setIndex());
            else { browser.activateRow(row.key()); syncBrowser(true); }
        }
        selectionSound(identity);
        if (context) perform(SongSelectAction.OPTIONS);
    }
    private void randomize() {
        if (importing || outgoing.pending()) return;
        var identity = browser.selection();
        browser.random(); syncBrowser(true); selectionSound(identity);
    }
    private void previousRandom() {
        if (importing || outgoing.pending()) return;
        var identity = browser.selection();
        browser.previousRandom(); syncBrowser(true); selectionSound(identity);
    }
    private void advance(int direction) {
        if (importing || outgoing.pending()) return;
        var identity = browser.selection();
        var previous = browser.selectedSet();
        carousel.keyboardNavigation();
        browser.moveDifficulty(direction); syncBrowser(previous != browser.selectedSet()); selectionSound(identity);
    }
    private void advanceSet(int direction) {
        if (importing || outgoing.pending()) return;
        var identity = browser.selection();
        var previous = browser.selectedSet();
        browser.moveSet(direction); syncBrowser(previous != browser.selectedSet()); selectionSound(identity);
    }
    private void ensureVisibleSelection() { browser.search(search); syncBrowser(true); }
    private void selectSet(int index) {
        if (index < 0 || index >= sets.size() || index == selectedSetIndex) return;
        browser.selectSet(sets.get(index).id()); syncBrowser(true);
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
    void previewSelection(int set, int difficulty) {
        if (set < 0 || set >= sets.size()) throw new IllegalArgumentException("Invalid capture set");
        browser.select(sets.get(set).id(), difficulty); syncBrowser(true);
    }
    void previewScroll(float distance) { carousel.scrollBy(distance); }
    List<SongSelectLayout.RowGeometry> rowGeometrySnapshot() {
        return rowPresentations.stream().map(SongSelectRowRenderer.Presentation::geometry).toList();
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
                sound(SongSelectAudio.Cue.CONFIRM);
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
    private void goBack() { sound(SongSelectAudio.Cue.BACK); outgoing.request(() -> game.navigate(new MainMenuScreen(game))); }
    private void playSelected() { startSelectedPlay(GameplayRunMode.MANUAL); }

    private void startSelectedPlay(GameplayRunMode runMode) {
        BeatmapSet set = selectedSet(); BeatmapDifficulty difficulty = selectedDifficulty();
        if (set == null) { showToast("Import a beatmap to play.", UiTheme.MUTED); return; }
        if (!game.osuRuleset().supportsMode(difficulty.mode())) {
            showToast("Only osu!standard is playable right now.", UiTheme.ERROR); return;
        }
        if (runMode == GameplayRunMode.DEBUG_AUTO) showToast("Debug Auto Play", UiTheme.ACCENT);
        sound(SongSelectAudio.Cue.PLAY);
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
    private void sound(SongSelectAudio.Cue cue) { if (audio != null) audio.play(cue); }
    private void selectionSound(SongBrowserModel.Selection previous) {
        if (audio != null) audio.selection(previous, browser.selection());
    }
    private void showToast(String message, Color color) { toast = message; toastColor = color; toastSeconds = 4; }
}
