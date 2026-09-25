package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.library.BeatmapImportException;
import dev.osujava.library.ImportResult;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final Color BACK_PINK = new Color(.83f, .28f, .55f, 1f);

    private static final class Metrics {
        static final float HEADER_HEIGHT = 62;
        static final float TOOLBAR_HEIGHT = 38;
        static final float SELECTED_X = .49f;
        static final float SIBLING_X = .585f;
        static final float OTHER_X = .665f;
        static final float ROW_HEIGHT = 76;
        static final float ROW_STEP = 72;
        static final float THUMB_X = 9;
        static final float THUMB_Y = 3;
        static final float THUMB_WIDTH = 96;
        static final float THUMB_HEIGHT = 70;
        static final float BACK_WIDTH = 110;
        static final float IMPORT_X = 116;
        static final float IMPORT_WIDTH = 170;
        static final float ROW_TITLE_SCALE = .84f;
        static final float ROW_DETAIL_SCALE = .66f;
        static final float ROW_MODE_SCALE = .62f;
        static final float COOKIE_TEXT_SCALE = .68f;
    }

    private final OsuJavaGame game;
    private final UiView view;
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private final BeatmapThumbnails thumbnails = new BeatmapThumbnails();
    private final OsuCookie playCookie = new OsuCookie();
    private final Map<String, RowMotion> motions = new HashMap<>();
    private List<BeatmapSet> sets;
    private List<Row> visibleRows = List.of();
    private int selectedSetIndex, selectedDifficultyIndex;
    private boolean importing, closed, searchActive;
    private String search = "";
    private String toast = "";
    private Color toastColor = UiTheme.TEXT;
    private float toastSeconds, seconds;
    private float backgroundFade;
    private Path backgroundPath;
    private float top, bottom, searchX, searchW, cookieX, cookieY, cookieRadius;

    private static final class RowMotion {
        float x, y;
        RowMotion(float x, float y) { this.x = x; this.y = y; }
    }
    private record Row(int setIndex, int difficultyIndex, boolean selected, boolean sibling,
                       float x, float y, float width, float height) { }

    public SongSelectScreen(OsuJavaGame game) { this(game, null, 0); }
    public SongSelectScreen(OsuJavaGame game, String preferredSetId, int preferredDifficulty) {
        this.game = game;
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
                if (sets.isEmpty()) return false;
                advance((int) Math.signum(amountY));
                return true;
            }
        });
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        seconds += Math.min(delta, .05f);
        calculateLayout(layout);
        visibleRows = layoutRows(layout, delta);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (px >= searchX && px <= searchX + searchW && py >= top + 4 && py <= top + 29) searchActive = true;
            else if (py < bottom && px < Metrics.BACK_WIDTH) { goBack(); return; }
            else if (py < bottom && px >= Metrics.IMPORT_X && px < Metrics.IMPORT_X + Metrics.IMPORT_WIDTH) { requestImport(); }
            else if (selectedDifficulty() != null && playCookie.hit(px, py)) { playSelected(); return; }
            else { searchActive = false; handleRowClick(px, py); }
        }

        view.clear();
        backgroundFade = Math.min(1, backgroundFade + Math.max(0, delta) / .22f);
        view.background(thumbnails.get(backgroundPath), .72f * backgroundFade);
        view.beginShapes();
        view.box(0, 0, layout.width(), layout.height(), 0, DIM);
        view.box(0, top, layout.width(), layout.height() - top, 0, TOP);
        view.box(0, bottom, layout.width() * .32f, top - bottom, 0, LEFT);
        view.box(0, 0, layout.width(), bottom, 0, BOTTOM);
        view.box(0, 0, Metrics.BACK_WIDTH, bottom, 0, BACK_PINK);
        view.box(Metrics.IMPORT_X, 0, Metrics.IMPORT_WIDTH, bottom, 0, OTHER);
        view.box(searchX, top + 4, searchW, 25, 0, searchActive ? SIBLING : LEFT);
        drawRowShapes(layout, px, py);
        if (selectedDifficulty() != null) {
            playCookie.drawShape(view, seconds, playCookie.hit(px, py), Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        }
        if (toastSeconds > 0) view.box(18, bottom + 12, Math.min(450, layout.width() * .42f), 35, 0, BOTTOM);
        view.endShapes();
        view.beginText();
        drawThumbnails();
        drawRowText();
        drawMetadata(layout);
        drawRanking(layout);
        view.textSmooth("LOCAL SETS  ·  TITLE", layout.width() * .58f, top + 11, 235, .72f, UiTheme.TEXT);
        view.textSmooth(search.isEmpty() ? "Search beatmaps" : search + (searchActive ? "|" : ""),
                searchX + 9, top + 11, searchW - 18, .75f, search.isEmpty() ? UiTheme.MUTED : UiTheme.TEXT);
        view.textSmooth("‹  back", 12, 11, 96, 1.0f, UiTheme.TEXT);
        view.textSmooth("Import .osz / .osu", 126, 11, 151, .75f, UiTheme.TEXT);
        view.textSmooth(sets.size() + " local sets", 305, 11, 250, .72f, UiTheme.MUTED);
        view.textSmooth("F6  DEBUG AUTO", layout.width() - 145, 11, 132, .62f, UiTheme.MUTED);
        if (selectedDifficulty() != null) playCookie.drawText(view, Metrics.COOKIE_TEXT_SCALE);
        if (toastSeconds > 0) view.textSmooth(toast, 27, bottom + 34, Math.min(430, layout.width() * .4f), UiTheme.META, toastColor);
        view.endText();
        toastSeconds = Math.max(0, toastSeconds - Math.max(0, delta));
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { closed = true; thumbnails.close(); }

    private void calculateLayout(UiLayout layout) {
        bottom = Metrics.TOOLBAR_HEIGHT;
        top = layout.height() - Metrics.HEADER_HEIGHT;
        searchW = Math.min(210, layout.width() * .19f);
        searchX = layout.width() - searchW - 16;
        cookieRadius = Math.min(70, layout.height() * .10f);
        cookieX = layout.width() - cookieRadius * .50f;
        cookieY = cookieRadius * .55f;
        playCookie.bounds(cookieX, cookieY, cookieRadius);
    }

    private List<Row> layoutRows(UiLayout layout, float delta) {
        List<int[]> entries = new ArrayList<>();
        int selectedEntry = 0;
        String query = search.toLowerCase(Locale.ROOT).strip();
        for (int i = 0; i < sets.size(); i++) {
            BeatmapSet set = sets.get(i);
            if (!matches(set, query)) continue;
            if (i == selectedSetIndex) {
                int first = Math.max(0, selectedDifficultyIndex - 2);
                int last = Math.min(set.difficulties().size(), selectedDifficultyIndex + 3);
                for (int j = first; j < last; j++) {
                    if (j == selectedDifficultyIndex) selectedEntry = entries.size();
                    entries.add(new int[]{i, j});
                }
            } else entries.add(new int[]{i, -1});
        }
        List<Row> result = new ArrayList<>();
        float centerY = bottom + (top - bottom) * .49f;
        float right = layout.width() + 2;
        for (int i = 0; i < entries.size(); i++) {
            int setIndex = entries.get(i)[0], diffIndex = entries.get(i)[1];
            boolean selected = setIndex == selectedSetIndex && diffIndex == selectedDifficultyIndex;
            boolean sibling = setIndex == selectedSetIndex && !selected;
            float targetX = layout.width() * (selected ? Metrics.SELECTED_X : sibling ? Metrics.SIBLING_X : Metrics.OTHER_X);
            float targetY = centerY + (selectedEntry - i) * Metrics.ROW_STEP;
            float width = right - targetX;
            String key = rowKey(setIndex, diffIndex);
            RowMotion motion = motions.computeIfAbsent(key, unused -> new RowMotion(targetX + 28, targetY));
            float factor = Math.min(1, Math.max(0, delta) * 14);
            motion.x += (targetX - motion.x) * factor;
            motion.y += (targetY - motion.y) * factor;
            if (motion.y + Metrics.ROW_HEIGHT < bottom || motion.y > top) continue;
            result.add(new Row(setIndex, diffIndex, selected, sibling, motion.x, motion.y,
                    width + targetX - motion.x, Metrics.ROW_HEIGHT));
        }
        return result;
    }

    private void drawRowShapes(UiLayout layout, float px, float py) {
        for (Row row : visibleRows) if (!row.selected()) drawRowShape(row, px, py);
        for (Row row : visibleRows) if (row.selected()) drawRowShape(row, px, py);
        if (visibleRows.isEmpty()) view.box(layout.width() * .59f, bottom + 155, layout.width() * .38f, 66, 0, LEFT);
    }

    private void drawRowShape(Row row, float px, float py) {
        boolean hover = rowHit(row, px, py);
        Color color = row.selected() ? SELECTED : row.sibling()
                ? hover ? SIBLING_HOVER : SIBLING : hover ? OTHER_HOVER : OTHER;
        float x = row.x() - (hover && !row.selected() ? 7 : 0), y = row.y();
        view.quad(x, y, x + row.width(), y, x + row.width(), y + row.height(), x + 9, y + row.height(), color);
        view.box(x + Metrics.THUMB_X, y + Metrics.THUMB_Y, Metrics.THUMB_WIDTH, Metrics.THUMB_HEIGHT, 0, THUMB_FALLBACK);
    }

    private void drawThumbnails() {
        for (Row row : visibleRows) if (!row.selected()) drawThumbnail(row);
        for (Row row : visibleRows) if (row.selected()) drawThumbnail(row);
    }

    private void drawThumbnail(Row row) {
        BeatmapSet set = sets.get(row.setIndex());
        BeatmapDifficulty diff = row.difficultyIndex() >= 0 ? set.difficulties().get(row.difficultyIndex()) : set.difficulties().get(0);
        Path path = diff.backgroundPath() != null ? diff.backgroundPath() : set.backgroundPath();
        Texture texture = thumbnails.get(path);
        view.imageCover(texture, row.x() + Metrics.THUMB_X, row.y() + Metrics.THUMB_Y,
                Metrics.THUMB_WIDTH, Metrics.THUMB_HEIGHT);
    }

    private void drawRowText() {
        if (visibleRows.isEmpty()) {
            view.textSmooth(search.isEmpty() ? "Import a beatmap to begin" : "No matching beatmaps",
                    searchX - 220, bottom + 196, 410, UiTheme.BODY, UiTheme.TEXT);
            return;
        }
        for (Row row : visibleRows) if (!row.selected()) drawRowLabel(row);
        for (Row row : visibleRows) if (row.selected()) drawRowLabel(row);
    }

    private void drawRowLabel(Row row) {
        BeatmapSet set = sets.get(row.setIndex());
        BeatmapDifficulty diff = row.difficultyIndex() >= 0 ? set.difficulties().get(row.difficultyIndex()) : null;
        Color primary = row.selected() ? DARK_TEXT : UiTheme.TEXT;
        Color secondary = row.selected() ? DARK_TEXT : UiTheme.MUTED;
        float x = row.x() + 118, w = Math.max(60, row.width() - 136);
        view.textSmooth(set.artist() + " - " + set.title(), x, row.y() + 50, w, Metrics.ROW_TITLE_SCALE, primary);
        view.textSmooth(diff == null ? set.creator() + "  ·  " + set.difficulties().size() + " difficulties"
                        : "[" + diff.version() + "]  mapped by " + set.creator(),
                x, row.y() + 30, w, Metrics.ROW_DETAIL_SCALE, secondary);
        if (diff != null) view.textSmooth(modeName(diff.mode()) + (row.selected()
                        ? "  ·  " + (row.difficultyIndex() + 1) + "/" + set.difficulties().size() : ""),
                x, row.y() + 13, w, Metrics.ROW_MODE_SCALE, secondary);
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
        return x >= row.x() && x <= row.x() + row.width() && y >= row.y() && y <= row.y() + row.height();
    }
    private void handleRowClick(float x, float y) {
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            Row row = visibleRows.get(i);
            if (!rowHit(row, x, y)) continue;
            if (row.selected()) playSelected();
            else if (row.difficultyIndex() >= 0) selectDifficulty(row.difficultyIndex());
            else selectSet(row.setIndex());
            return;
        }
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
        if (sets.isEmpty() || matches(sets.get(selectedSetIndex), search.toLowerCase(Locale.ROOT).strip())) return;
        for (int i = 0; i < sets.size(); i++) if (matches(sets.get(i), search.toLowerCase(Locale.ROOT).strip())) {
            selectSet(i);
            return;
        }
    }
    private void selectSet(int index) {
        if (sets.isEmpty()) return;
        int next = Math.max(0, Math.min(sets.size() - 1, index));
        if (next == selectedSetIndex) return;
        selectedSetIndex = next;
        selectedDifficultyIndex = 0;
        selectBackground();
    }
    private void selectDifficulty(int index) {
        BeatmapSet set = selectedSet();
        if (set == null) return;
        int next = Math.max(0, Math.min(set.difficulties().size() - 1, index));
        if (next == selectedDifficultyIndex) return;
        selectedDifficultyIndex = next;
        selectBackground();
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
                motions.clear();
                selectBackground();
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
