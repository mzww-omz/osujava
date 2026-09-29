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
    private String geometryViewport;
    private SongSelectSkinAssets skin;
    private SongSelectCursor cursor;
    private SongSelectAudio audio;
    private SongSelectPreview preview;
    private Color activeText = DARK_TEXT, inactiveText;
    private final SongSelectToolboxState toolbox = new SongSelectToolboxState();
    boolean renderedSelectionProcedural(Selection action) { return renderer.renderedSelectionProcedural(action); }
    private final Map<Object, SongSelectRowPresentation.Content> rowContent = new IdentityHashMap<>();
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
    private SongSelectLayout.Snapshot layoutSnapshot;
    private SongSelectChrome.Content chromeContent;
    private float chromeWidth = -1, chromeHeight = -1;
    private SongSelectLayout.Snapshot layoutSnapshot(UiLayout layout) {
        if (layoutSnapshot == null || chromeWidth != layout.width() || chromeHeight != layout.height()) {
            layoutSnapshot = SongSelectLayout.create(layout, skin);
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
            @Override public void page(int direction) {
                var identity = browser.selection();
                var previous = browser.selectedSet();
                browser.movePage(direction); syncBrowser(previous != browser.selectedSet());
                selectionSound(identity);
            }
            @Override public boolean scroll(float amount) { return scrollAtPointer(amount); }
            @Override public void cursor(int x, int y, int button, boolean down) {
                if (cursor != null) cursor.event(Gdx.input.getCurrentEventTime(), x, y, button, down);
            }
        });
        Gdx.input.setInputProcessor(input);
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
        renderer.draw(new SongSelectRenderer.Frame(
                skin,
                activeText,
                inactiveText,
                toolbox,
                new SongSelectRenderer.BrowserView(browser.sort(), browser.group(), browser.visibleSets().size()),
                new SongSelectRenderer.ScoreView(scores.rows(), scores.first(), scores.selected()),
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
        viewState.sample(layout, Gdx.input.getX(), Gdx.input.getY(), Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        viewState.advance(delta);
        seconds = viewState.elapsed;
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
            input.cancelPointer();
            var oldSort = browser.sort(); var oldGroup = browser.group();
            var previousMenu = controls.menu();
            if (toolbox.open()) SongSelectToolboxOverlay.click(toolbox,layout,px,py);
            else if (controls.click(px, py, layout.width(), layout.height(), browser)) {
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
                if (slot >= 0) scores.select(scores.first() + slot);
                else {
                    var row = hitRow(px, py);
                    input.pressRow(row == null ? null : rowKey(row.setIndex(), row.difficultyIndex()), px, py);
                }
            }
        }

        if (toolbox.open() || importing || outgoing.pending()) input.cancelPointer();
        float scrollBeforePointer = carousel.scrollOffset();
        var releasedRow = hitRow(px, py);
        if (input.pointer(Gdx.input.isButtonPressed(Input.Buttons.LEFT),
                releasedRow == null ? null : rowKey(releasedRow.setIndex(), releasedRow.difficultyIndex()), px, py, carousel) != null)
            handleRowClick(px, py);
        // Direct dragging changed viewport position; publish the same geometry for drawing and the next hit test.
        if (carousel.scrollOffset() != scrollBeforePointer)
            visibleRows = layoutRows(layout, 0);
        prepareRowPresentations();
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
                        target = "row:" + rowKey(row.setIndex(), row.difficultyIndex());
                        cue = SongSelectAudio.Cue.HOVER_ROW;
                    }
                }
            }
            audio.hover(target, cue);
        }
        backgroundFade = Math.min(1, backgroundFade + Math.max(0, delta) / .22f);
        viewState.hover.advance(delta, toolbox.open() || importing || outgoing.pending() ? null
                : SongSelectAction.bottom(px,py,bottomLayout));
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

    @Override public void hide() { if (preview != null) preview.close(); if (cursor != null) cursor.hide(); }
    @Override public void dispose() { closed = true; if (preview != null) preview.close(); if (audio != null) audio.close(); if (cursor != null) cursor.close(); thumbnails.close(); playCookie.close(); if (rowFill != null) { rowFill.dispose(); rowFill = null; } if (skin != null) skin.dispose(); }

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
            var target = carousel.targetPosition(entry.logicalIndex, layout.width(), top);
            result.add(new SongSelectRow(setIndex, diffIndex, groupLabels.get(entry.entry.key()), selected, setIndex == selectedSetIndex && !selected,
                    carousel.renderX(entry, layout.width()), y, rowWidth(layout), carousel.rowHeight(), entry.hoverAmount, selected ? 1 : entry.revealAmount * entry.revealAmount, entry.logicalIndex, target[0], target[1]));
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
                result.add(new SongSelectRowRenderer.Presentation(row, null, false, null, null, 0, rowGeometry(row, false)));
                continue;
            }
            var set = sets.get(row.setIndex());
            var diff = row.difficultyIndex() < 0 ? null : set.difficulties().get(row.difficultyIndex());
            var content = rowContent.get(diff == null ? set : diff);
            var best = diff == null ? null : scoreSnapshot.best(set, diff);
            boolean played = diff == null ? scoreSnapshot.played(set) : best != null;
            result.add(new SongSelectRowRenderer.Presentation(row, content, played, best == null ? null : best.grade(),
                    showThumbnails() ? thumbnails.resident(content.thumbnail()) : null, thumbnails.opacity(content.thumbnail()), rowGeometry(row, best != null)));
        }
        rowPresentations = List.copyOf(result);
    }

    private SongSelectLayout.RowGeometry rowGeometry(SongSelectRow row, boolean grade) {
        return SongSelectLayout.row(row, row.logicalIndex(), row.targetX(), row.targetY(),
                contentWidth, bottom, top, showThumbnails(), grade);
    }

    private void updateDetails() {
        var set = selectedSet();
        var diff = selectedDifficulty();
        if (metadataSet == set && metadataDifficulty == diff) return;
        metadataSet = set; metadataDifficulty = diff;
        details = set == null || diff == null ? null : SongSelectDetails.of(set, diff, rowContent.get(diff).stars());
    }

    private SongSelectRow hitRow(float x, float y) {
        return SongSelectRow.hit(visibleRows, x, y, bottom, top);
    }

    private void handleRowClick(float x, float y) {
        var identity = browser.selection();
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
        selectionSound(identity);
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
        var identity = browser.selection();
        var previous = browser.selectedSet();
        browser.moveDifficulty(direction); syncBrowser(previous != browser.selectedSet()); selectionSound(identity);
    }
    private void advanceSet(int direction) {
        var identity = browser.selection();
        var previous = browser.selectedSet();
        browser.moveSet(direction); syncBrowser(previous != browser.selectedSet()); selectionSound(identity);
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
    void previewSelection(int set, int difficulty) {
        if (set < 0 || set >= sets.size()) throw new IllegalArgumentException("Invalid capture set");
        browser.select(sets.get(set).id(), difficulty); syncBrowser(true);
    }
    void previewScroll(float distance) { carousel.scrollBy(distance); }
    List<SongSelectLayout.RowGeometry> rowGeometrySnapshot() {
        return rowPresentations.stream().map(SongSelectRowRenderer.Presentation::geometry).toList();
    }

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
