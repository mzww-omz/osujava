package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.library.BeatmapImportException;
import dev.osujava.library.ImportResult;
import dev.osujava.ui.theme.BeatmapBackdrop;
import dev.osujava.ui.theme.UiButton;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class SongSelectScreen extends ScreenAdapter {
    private final OsuJavaGame game;
    private final UiView view;
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private final BeatmapBackdrop backdrop = new BeatmapBackdrop();
    private final UiButton back = new UiButton("BACK", false);
    private final UiButton importButton = new UiButton("IMPORT BEATMAP", false);
    private final UiButton play = new UiButton("PLAY SELECTED", true);
    private List<BeatmapSet> sets;
    private int selectedSetIndex, selectedDifficultyIndex;
    private boolean importing, closed;
    private String toast = "";
    private Color toastColor = UiTheme.TEXT;
    private float toastSeconds;
    private float listX, listW, listTop, listBottom, heroX, heroW;

    public SongSelectScreen(OsuJavaGame game) { this(game, null, 0); }

    public SongSelectScreen(OsuJavaGame game, String preferredSetId, int preferredDifficulty) {
        this.game = game;
        view = new UiView(game);
        sets = game.library().all();
        if (preferredSetId != null) {
            for (int i = 0; i < sets.size(); i++) {
                if (sets.get(i).id().equals(preferredSetId)) {
                    selectedSetIndex = i;
                    selectedDifficultyIndex = Math.max(0, Math.min(preferredDifficulty, sets.get(i).difficulties().size() - 1));
                    break;
                }
            }
        }
    }

    @Override public void show() {
        backdrop.select(selectedSet(), selectedDifficulty());
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (key == Input.Keys.ESCAPE) { goBack(); return true; }
                if (key == Input.Keys.I) { requestImport(); return true; }
                if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) { playSelected(); return true; }
                if (key == Input.Keys.UP) { selectSet(selectedSetIndex - 1); return true; }
                if (key == Input.Keys.DOWN) { selectSet(selectedSetIndex + 1); return true; }
                if (key == Input.Keys.LEFT || key == Input.Keys.RIGHT) {
                    BeatmapSet set = selectedSet();
                    if (set != null) selectDifficulty(selectedDifficultyIndex + (key == Input.Keys.RIGHT ? 1 : -1));
                    return true;
                }
                return false;
            }
            @Override public boolean scrolled(float amountX, float amountY) {
                if (sets.isEmpty()) return false;
                selectSet(selectedSetIndex + (int) Math.signum(amountY));
                return true;
            }
        });
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        calculateLayout(layout);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float x = layout.pointerX(Gdx.input.getX()), y = layout.pointerY(Gdx.input.getY());
            if (back.hit(x, y)) { goBack(); return; }
            if (importButton.hit(x, y)) { requestImport(); }
            else if (play.hit(x, y)) { playSelected(); if (game.getScreen() != this) return; }
            else handleRowClick(x, y);
        }
        view.clear();
        backdrop.draw(view, delta);
        view.beginShapes();
        view.box(0, 0, layout.width(), layout.height(), 0, UiTheme.VEIL);
        view.box(0, layout.height() - 88, layout.width(), 88, 0, UiTheme.SURFACE);
        view.box(0, 0, layout.width(), 78, 0, UiTheme.SURFACE);
        view.box(heroX, 108, heroW, layout.height() - 220, UiTheme.RADIUS, UiTheme.SURFACE);
        view.box(listX, listBottom - 10, listW, listTop - listBottom + 42, UiTheme.RADIUS, UiTheme.SURFACE);
        drawRowsShape(layout);
        back.drawShape(view, layout, delta);
        importButton.drawShape(view, layout, delta);
        play.drawShape(view, layout, delta);
        if (toastSeconds > 0) view.box(heroX + 12, 90, Math.min(heroW - 24, 460), 48, UiTheme.RADIUS, UiTheme.SURFACE_RAISED);
        view.endShapes();
        view.beginText();
        view.text("SONG SELECT", layout.contentX() + 4, layout.height() - 29, 330, UiTheme.HEADING, UiTheme.TEXT);
        view.text(sets.size() + " LOCAL SETS", listX, layout.height() - 38, listW - 12, UiTheme.META, UiTheme.MUTED, Align.right);
        drawHeroText(layout);
        drawRowsText();
        back.drawText(view); importButton.drawText(view); play.drawText(view);
        if (toastSeconds > 0) view.text(toast, heroX + 26, 120, Math.min(heroW - 52, 432), UiTheme.META, toastColor);
        view.endText();
        toastSeconds = Math.max(0, toastSeconds - Math.max(0, delta));
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { closed = true; backdrop.close(); }

    private void calculateLayout(UiLayout layout) {
        float contentX = layout.contentX(), contentW = layout.contentWidth();
        heroX = contentX;
        heroW = Math.max(375, contentW * 0.43f);
        listX = heroX + heroW + 16;
        listW = contentX + contentW - listX;
        listTop = layout.height() - 119;
        listBottom = 112;
        back.bounds(contentX, 15, 104, 49);
        importButton.bounds(contentX + 116, 15, 182, 49);
        play.bounds(contentX + contentW - 213, 14, 213, 51);
        BeatmapDifficulty difficulty = selectedDifficulty();
        play.enabled(difficulty != null && game.osuRuleset().supportsMode(difficulty.mode()));
        importButton.enabled(!importing);
    }

    private int visibleStart() { return Math.max(0, selectedSetIndex - 3); }
    private float rowHeight(int index) { return index == selectedSetIndex ? 70 : 59; }
    private int diffStart() {
        BeatmapSet set = selectedSet();
        if (set == null) return 0;
        return Math.max(0, selectedDifficultyIndex - 3);
    }

    private void drawRowsShape(UiLayout layout) {
        float y = listTop;
        for (int i = visibleStart(); i < sets.size(); i++) {
            float h = rowHeight(i);
            y -= h;
            if (y < listBottom + 8) break;
            boolean selected = i == selectedSetIndex;
            boolean hover = rowHit(layout, listX + 8, y, listW - 16, h - 5);
            view.box(listX + (selected ? 2 : 10), y, listW - (selected ? 12 : 20), h - 5,
                    UiTheme.RADIUS, selected ? UiTheme.SURFACE_RAISED : hover ? UiTheme.SURFACE_RAISED : UiTheme.SURFACE);
            if (selected) {
                view.box(listX + 2, y + 5, 5, h - 15, 2, UiTheme.ACCENT);
                BeatmapSet set = sets.get(i);
                int start = diffStart();
                for (int j = start; j < set.difficulties().size(); j++) {
                    y -= 44;
                    if (y < listBottom + 8) break;
                    boolean active = j == selectedDifficultyIndex;
                    boolean diffHover = rowHit(layout, listX + 30, y, listW - 44, 39);
                    view.box(listX + 30, y, listW - 44, 39, 7,
                            active ? UiTheme.ACCENT : diffHover ? UiTheme.SURFACE_RAISED : UiTheme.SURFACE);
                }
                y -= 8;
            }
        }
    }

    private void drawRowsText() {
        float y = listTop;
        if (sets.isEmpty()) {
            view.text("YOUR LIBRARY IS EMPTY", listX + 28, y - 44, listW - 56, UiTheme.TITLE, UiTheme.TEXT);
            view.text("Import an .osz or .osu file to begin.", listX + 28, y - 77, listW - 56, UiTheme.BODY, UiTheme.MUTED);
            return;
        }
        for (int i = visibleStart(); i < sets.size(); i++) {
            float h = rowHeight(i);
            y -= h;
            if (y < listBottom + 8) break;
            BeatmapSet set = sets.get(i);
            boolean selected = i == selectedSetIndex;
            float x = listX + (selected ? 22 : 27);
            view.text(set.title(), x, y + h - 18, listW - 57, selected ? UiTheme.TITLE : UiTheme.BODY, UiTheme.TEXT);
            view.text(set.artist() + "  /  " + set.creator(), x, y + 18, listW - 57, UiTheme.META, UiTheme.MUTED);
            if (selected) {
                int start = diffStart();
                for (int j = start; j < set.difficulties().size(); j++) {
                    y -= 44;
                    if (y < listBottom + 8) break;
                    BeatmapDifficulty diff = set.difficulties().get(j);
                    Color color = j == selectedDifficultyIndex ? UiTheme.TEXT : UiTheme.MUTED;
                    view.text(">  " + diff.version(), listX + 43, y + 25, listW - 170, UiTheme.BODY, color);
                    view.text(modeName(diff.mode()), listX + listW - 122, y + 24, 100, UiTheme.META, color, Align.right);
                }
                y -= 8;
            }
        }
    }

    private void drawHeroText(UiLayout layout) {
        BeatmapSet set = selectedSet();
        if (set == null) {
            view.text("READY WHEN YOU ARE", heroX + 28, layout.height() - 164, heroW - 56, UiTheme.HEADING, UiTheme.TEXT);
            view.text("Build a local library to play.", heroX + 28, layout.height() - 205, heroW - 56, UiTheme.BODY, UiTheme.MUTED);
            return;
        }
        BeatmapDifficulty diff = selectedDifficulty();
        float top = layout.height() - 151;
        view.text("NOW SELECTED", heroX + 30, top, heroW - 60, UiTheme.META, UiTheme.ACCENT);
        view.text(set.title(), heroX + 28, top - 45, heroW - 56, UiTheme.HEADING, UiTheme.TEXT);
        view.text(set.artist(), heroX + 30, top - 78, heroW - 60, UiTheme.TITLE, UiTheme.TEXT);
        view.text("mapped by " + set.creator(), heroX + 30, top - 105, heroW - 60, UiTheme.META, UiTheme.MUTED);
        if (diff != null) {
            view.text("DIFFICULTY", heroX + 30, top - 169, heroW - 60, UiTheme.META, UiTheme.MUTED);
            view.text(diff.version(), heroX + 30, top - 205, heroW - 60, UiTheme.TITLE, UiTheme.TEXT);
            view.text(modeName(diff.mode()).toUpperCase(Locale.ROOT) + "    /    OD " + diff.settings().overallDifficulty(),
                    heroX + 30, top - 239, heroW - 60, UiTheme.META, UiTheme.MUTED);
            if (!game.osuRuleset().supportsMode(diff.mode()))
                view.text("This mode is imported but cannot be played yet.", heroX + 30, top - 273,
                        heroW - 60, UiTheme.META, UiTheme.ERROR);
        }
        view.text(set.difficulties().size() + " DIFFICULTIES IN THIS SET", heroX + 30, 158, heroW - 60, UiTheme.META, UiTheme.MUTED);
    }

    private boolean rowHit(UiLayout layout, float x, float y, float w, float h) {
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void handleRowClick(float x, float py) {
        if (x < listX || x > listX + listW) return;
        float y = listTop;
        for (int i = visibleStart(); i < sets.size(); i++) {
            float h = rowHeight(i);
            y -= h;
            if (y < listBottom + 8) break;
            if (py >= y && py <= y + h - 5) { selectSet(i); return; }
            if (i == selectedSetIndex) {
                BeatmapSet set = sets.get(i);
                for (int j = diffStart(); j < set.difficulties().size(); j++) {
                    y -= 44;
                    if (y < listBottom + 8) break;
                    if (py >= y && py <= y + 39) { selectDifficulty(j); return; }
                }
                y -= 8;
            }
        }
    }

    private void selectSet(int index) {
        if (sets.isEmpty()) return;
        int next = Math.max(0, Math.min(sets.size() - 1, index));
        if (next == selectedSetIndex) return;
        selectedSetIndex = next;
        selectedDifficultyIndex = 0;
        backdrop.select(selectedSet(), selectedDifficulty());
    }
    private void selectDifficulty(int index) {
        BeatmapSet set = selectedSet();
        if (set == null) return;
        selectedDifficultyIndex = Math.max(0, Math.min(set.difficulties().size() - 1, index));
        backdrop.select(set, selectedDifficulty());
    }
    private BeatmapSet selectedSet() { return selectedSetIndex < sets.size() ? sets.get(selectedSetIndex) : null; }
    private BeatmapDifficulty selectedDifficulty() {
        BeatmapSet set = selectedSet();
        return set == null ? null : set.difficulties().get(selectedDifficultyIndex);
    }
    private String modeName(int mode) { return mode == 0 ? "osu!standard" : "mode " + mode; }
    private void goBack() { outgoing.request(() -> game.navigate(new MainMenuScreen(game))); }
    private void playSelected() {
        BeatmapSet set = selectedSet(); BeatmapDifficulty difficulty = selectedDifficulty();
        if (set == null) { showToast("Import a beatmap to play.", UiTheme.MUTED); return; }
        if (!game.osuRuleset().supportsMode(difficulty.mode())) {
            showToast("Only osu!standard is playable right now.", UiTheme.ERROR); return;
        }
        outgoing.request(() -> game.navigate(new GameplayScreen(game, set, difficulty)));
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
                sets = game.library().all();
                for (int i = 0; i < sets.size(); i++)
                    if (sets.get(i).id().equals(finished.beatmapSet().id())) { selectedSetIndex = i; break; }
                selectedDifficultyIndex = 0;
                backdrop.select(selectedSet(), selectedDifficulty());
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
