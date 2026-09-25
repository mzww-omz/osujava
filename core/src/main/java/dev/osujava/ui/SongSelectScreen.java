package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.library.BeatmapImportException;
import dev.osujava.library.ImportResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SongSelectScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.055f, 0.05f, 0.09f, 1);
    private static final Color PANEL = new Color(0.12f, 0.105f, 0.17f, 0.94f);
    private static final Color PANEL_SELECTED = new Color(0.24f, 0.16f, 0.27f, 0.97f);
    private static final Color ACCENT = new Color(0.97f, 0.32f, 0.59f, 1);
    private final OsuJavaGame game;
    private int selectedSetIndex;
    private int selectedDifficultyIndex;
    private String status = "Import an .osz or .osu file to build your local library.";
    private Texture backdrop;
    private float importX, importY, importW, importH;
    private float backX, backY, backW, backH;
    private float playX, playY, playW, playH;
    private float leftX, leftY, leftW, panelH, rightX, rightW, rowTop, rowHeight;

    public SongSelectScreen(OsuJavaGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        reloadBackdrop();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                float x = screenX;
                float y = Gdx.graphics.getHeight() - screenY;
                if (contains(x, y, importX, importY, importW, importH)) {
                    openImport();
                } else if (contains(x, y, backX, backY, backW, backH)) {
                    game.navigate(new MainMenuScreen(game));
                } else if (contains(x, y, playX, playY, playW, playH)) {
                    playSelected();
                } else if (x >= leftX && x <= leftX + leftW && y <= rowTop && y >= leftY) {
                    int index = (int) ((rowTop - y) / rowHeight);
                    if (index < sets().size()) selectSet(index);
                } else if (x >= rightX && x <= rightX + rightW && y <= rowTop && y >= leftY) {
                    BeatmapSet set = selectedSet();
                    int index = (int) ((rowTop - y) / rowHeight);
                    if (set != null && index < set.difficulties().size()) selectedDifficultyIndex = index;
                }
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (AppShortcuts.handleQuit(keycode)) return true;
                if (keycode == Input.Keys.ESCAPE) {
                    game.navigate(new MainMenuScreen(game));
                    return true;
                }
                if (keycode == Input.Keys.I) {
                    openImport();
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    playSelected();
                    return true;
                }
                if (keycode == Input.Keys.UP) {
                    selectSet(Math.max(0, selectedSetIndex - 1));
                    return true;
                }
                if (keycode == Input.Keys.DOWN) {
                    selectSet(Math.min(sets().size() - 1, selectedSetIndex + 1));
                    return true;
                }
                if (keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT) {
                    BeatmapSet set = selectedSet();
                    if (set != null) {
                        int direction = keycode == Input.Keys.RIGHT ? 1 : -1;
                        selectedDifficultyIndex = Math.max(0, Math.min(set.difficulties().size() - 1,
                                selectedDifficultyIndex + direction));
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (sets().isEmpty()) return false;
                selectSet(Math.max(0, Math.min(sets().size() - 1, selectedSetIndex + (int) Math.signum(amountY))));
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        calculateLayout(width, height);
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (backdrop != null) {
            game.batch().setColor(1, 1, 1, 0.22f);
            game.batch().begin();
            game.batch().draw(backdrop, 0, 0, width, height);
            game.batch().end();
            game.batch().setColor(Color.WHITE);
        }

        ShapeRenderer shapes = game.shapes();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.045f, 0.04f, 0.07f, backdrop == null ? 1 : 0.80f);
        shapes.rect(0, 0, width, height);
        shapes.setColor(PANEL);
        shapes.rect(leftX, leftY, leftW, panelH);
        shapes.rect(rightX, leftY, rightW, panelH);
        drawButton(shapes, importX, importY, importW, importH, ACCENT);
        drawButton(shapes, backX, backY, backW, backH, new Color(0.25f, 0.22f, 0.31f, 1));
        BeatmapDifficulty difficulty = selectedDifficulty();
        Color playColor = difficulty != null && game.osuRuleset().supportsMode(difficulty.mode())
                ? ACCENT : new Color(0.32f, 0.29f, 0.36f, 1);
        drawButton(shapes, playX, playY, playW, playH, playColor);
        BeatmapSet current = selectedSet();
        if (current != null) {
            for (int i = 0; i < current.difficulties().size(); i++) {
                float y = rowTop - (i + 1) * rowHeight;
                if (y < leftY + 8) break;
                shapes.setColor(i == selectedDifficultyIndex ? PANEL_SELECTED : new Color(0.09f, 0.08f, 0.13f, 0.82f));
                shapes.rect(rightX + 10, y, rightW - 20, rowHeight - 5);
                if (i == selectedDifficultyIndex) {
                    shapes.setColor(ACCENT);
                    shapes.rect(rightX + 10, y, 4, rowHeight - 5);
                }
            }
        }
        List<BeatmapSet> sets = sets();
        for (int i = 0; i < sets.size(); i++) {
            float y = rowTop - (i + 1) * rowHeight;
            if (y < leftY + 8) break;
            shapes.setColor(i == selectedSetIndex ? PANEL_SELECTED : new Color(0.09f, 0.08f, 0.13f, 0.82f));
            shapes.rect(leftX + 10, y, leftW - 20, rowHeight - 5);
            if (i == selectedSetIndex) {
                shapes.setColor(ACCENT);
                shapes.rect(leftX + 10, y, 4, rowHeight - 5);
            }
        }
        shapes.end();

        SpriteBatch batch = game.batch();
        batch.begin();
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1.15f);
        game.font().draw(batch, "Import .osz / .osu", importX + 16, importY + 29);
        game.font().draw(batch, "Back", backX + 22, backY + 29);
        game.font().getData().setScale(1.7f);
        game.font().draw(batch, "Play", playX + playW / 2 - 31, playY + playH / 2 + 9);
        game.font().getData().setScale(1.35f);
        game.font().draw(batch, "SONG SELECT", leftX, height - 40);
        game.font().setColor(new Color(0.78f, 0.76f, 0.84f, 1));
        game.font().getData().setScale(0.85f);
        game.font().draw(batch, "BEATMAP SETS", leftX + 15, rowTop + 15);
        game.font().draw(batch, "DIFFICULTIES", rightX + 15, rowTop + 15);
        drawSetRows(batch, sets);
        drawDifficultyRows(batch, current);
        game.font().setColor(new Color(0.83f, 0.80f, 0.88f, 1));
        game.font().getData().setScale(0.85f);
        game.font().draw(batch, status, leftX, 38);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
        batch.end();
    }

    @Override
    public void dispose() {
        if (backdrop != null) backdrop.dispose();
    }

    private void calculateLayout(int width, int height) {
        leftX = 28;
        leftY = 88;
        leftW = Math.max(300, width * 0.41f);
        rightX = leftX + leftW + 16;
        rightW = width - rightX - 28;
        panelH = Math.max(220, height - 190);
        rowHeight = Math.max(54, Math.min(66, panelH / 8));
        rowTop = leftY + panelH - 55;
        importX = 28;
        importY = height - 68;
        importW = 190;
        importH = 44;
        backX = importX + importW + 14;
        backY = importY;
        backW = 100;
        backH = importH;
        playW = 220;
        playH = 54;
        playX = width - playW - 28;
        playY = 20;
    }

    private void drawSetRows(SpriteBatch batch, List<BeatmapSet> sets) {
        if (sets.isEmpty()) {
            game.font().setColor(Color.WHITE);
            game.font().getData().setScale(1f);
            game.font().draw(batch, "No beatmaps imported yet.", leftX + 18, rowTop - 42);
            game.font().setColor(new Color(0.78f, 0.76f, 0.84f, 1));
            game.font().getData().setScale(0.82f);
            game.font().draw(batch, "Choose an .osz or single .osu file above.", leftX + 18, rowTop - 66);
            return;
        }
        for (int i = 0; i < sets.size(); i++) {
            float y = rowTop - i * rowHeight - 21;
            if (y < leftY + 14) break;
            BeatmapSet set = sets.get(i);
            game.font().setColor(Color.WHITE);
            game.font().getData().setScale(1.0f);
            game.font().draw(batch, set.title(), leftX + 24, y);
            game.font().setColor(new Color(0.75f, 0.72f, 0.81f, 1));
            game.font().getData().setScale(0.78f);
            game.font().draw(batch, set.artist() + "  ·  " + set.difficulties().size() + " difficulties", leftX + 24, y - 21);
        }
    }

    private void drawDifficultyRows(SpriteBatch batch, BeatmapSet set) {
        if (set == null) return;
        for (int i = 0; i < set.difficulties().size(); i++) {
            float y = rowTop - i * rowHeight - 23;
            if (y < leftY + 14) break;
            BeatmapDifficulty difficulty = set.difficulties().get(i);
            game.font().setColor(Color.WHITE);
            game.font().getData().setScale(1.0f);
            game.font().draw(batch, difficulty.version(), rightX + 27, y);
            game.font().setColor(new Color(0.75f, 0.72f, 0.81f, 1));
            game.font().getData().setScale(0.78f);
            String mode = difficulty.mode() == 0 ? "osu!standard" : "mode " + difficulty.mode() + " (imported)";
            game.font().draw(batch, mode + "  ·  OD " + difficulty.settings().overallDifficulty(), rightX + 27, y - 20);
        }
    }

    private void openImport() {
        game.fileChooser().chooseFile(path -> Gdx.app.postRunnable(() -> importFile(path)));
    }

    private void importFile(Path path) {
        try {
            ImportResult result = game.importer().importFile(path);
            game.library().add(result.beatmapSet());
            selectedSetIndex = game.library().size() - 1;
            selectedDifficultyIndex = 0;
            reloadBackdrop();
            status = "Imported " + result.beatmapSet().title() + " · " + result.beatmapSet().difficulties().size() + " difficulties";
            if (!result.warnings().isEmpty()) status += " · " + result.warnings().getFirst();
        } catch (BeatmapImportException e) {
            status = "Import failed: " + e.getMessage();
        }
    }

    private void playSelected() {
        BeatmapSet set = selectedSet();
        BeatmapDifficulty difficulty = selectedDifficulty();
        if (set == null || difficulty == null) {
            status = "Import a beatmap before starting.";
            return;
        }
        if (!game.osuRuleset().supportsMode(difficulty.mode())) {
            status = "Mode " + difficulty.mode() + " is imported, but only osu!standard is playable yet.";
            return;
        }
        game.navigate(new GameplayScreen(game, set, difficulty));
    }

    private void selectSet(int index) {
        if (sets().isEmpty()) return;
        int next = Math.max(0, Math.min(sets().size() - 1, index));
        if (next != selectedSetIndex) {
            selectedSetIndex = next;
            selectedDifficultyIndex = 0;
            reloadBackdrop();
        }
    }

    private List<BeatmapSet> sets() {
        return game.library().all();
    }

    private BeatmapSet selectedSet() {
        List<BeatmapSet> sets = sets();
        return selectedSetIndex >= 0 && selectedSetIndex < sets.size() ? sets.get(selectedSetIndex) : null;
    }

    private BeatmapDifficulty selectedDifficulty() {
        BeatmapSet set = selectedSet();
        if (set == null || selectedDifficultyIndex < 0 || selectedDifficultyIndex >= set.difficulties().size()) return null;
        return set.difficulties().get(selectedDifficultyIndex);
    }

    private void reloadBackdrop() {
        if (backdrop != null) {
            backdrop.dispose();
            backdrop = null;
        }
        BeatmapSet set = selectedSet();
        if (set == null || set.backgroundPath() == null || !Files.isRegularFile(set.backgroundPath())) return;
        try {
            backdrop = new Texture(Gdx.files.absolute(set.backgroundPath().toString()));
            backdrop.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (GdxRuntimeException ignored) {
            status = "The imported background image could not be loaded.";
        }
    }

    private void drawButton(ShapeRenderer shapes, float x, float y, float width, float height, Color color) {
        shapes.setColor(color);
        shapes.rect(x, y, width, height);
    }

    private boolean contains(float x, float y, float rectX, float rectY, float rectW, float rectH) {
        return x >= rectX && x <= rectX + rectW && y >= rectY && y <= rectY + rectH;
    }
}
