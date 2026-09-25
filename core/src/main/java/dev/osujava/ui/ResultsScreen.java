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
import dev.osujava.gameplay.ScoreState;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.ui.theme.BeatmapBackdrop;
import dev.osujava.ui.theme.UiButton;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;

import java.util.Locale;

public final class ResultsScreen extends ScreenAdapter {
    private static final String[] JUDGEMENT_LABELS = {"300", "100", "50", "MISS"};
    private final String scoreText;
    private final String accuracyText;
    private final OsuJavaGame game;
    private final BeatmapSet set;
    private final BeatmapDifficulty difficulty;
    private final ScoreState score;
    private final GameplayRunMode runMode;
    private final UiView view;
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private final BeatmapBackdrop backdrop = new BeatmapBackdrop();
    private final UiButton retry = new UiButton("RETRY", false);
    private final UiButton songs = new UiButton("SONG SELECT", true);

    public ResultsScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty, ScoreState score) {
        this(game, set, difficulty, score, GameplayRunMode.MANUAL);
    }

    public ResultsScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty,
                         ScoreState score, GameplayRunMode runMode) {
        this.game = game; this.set = set; this.difficulty = difficulty; this.score = score; this.runMode = runMode;
        view = new UiView(game);
        scoreText = String.format(Locale.ROOT, "%,d", score.score());
        accuracyText = String.format(Locale.ROOT, "%.2f%%", score.accuracy() * 100);
    }

    @Override public void show() {
        backdrop.select(set, difficulty);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (key == Input.Keys.ENTER || key == Input.Keys.SPACE || key == Input.Keys.ESCAPE) {
                    goSongs(); return true;
                }
                if (key == Input.Keys.R) { goRetry(); return true; }
                return false;
            }
        });
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        float x = layout.contentX(), w = layout.contentWidth();
        float top = layout.height() - 108;
        float panelH = Math.min(500, layout.height() - 200);
        float panelY = top - panelH;
        retry.bounds(x, 20, 156, 52);
        songs.bounds(x + w - 205, 20, 205, 52);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
            if (retry.hit(px, py)) { goRetry(); return; }
            if (songs.hit(px, py)) { goSongs(); return; }
        }
        view.clear();
        backdrop.draw(view, delta);
        view.beginShapes();
        view.box(0, 0, layout.width(), layout.height(), 0, UiTheme.VEIL);
        view.box(0, layout.height() - 84, layout.width(), 84, 0, UiTheme.SURFACE);
        view.box(0, 0, layout.width(), 88, 0, UiTheme.SURFACE);
        view.box(x, panelY, w, panelH, UiTheme.RADIUS, UiTheme.SURFACE);
        view.box(x, panelY + panelH - 5, w, 5, 0, UiTheme.ACCENT);
        float innerX = x + 28;
        float statsY = panelY + 35;
        float statGap = 8;
        float statW = (w - 56 - statGap * 3) / 4;
        for (int i = 0; i < 4; i++) view.box(innerX + i * (statW + statGap), statsY, statW, 90,
                UiTheme.RADIUS, UiTheme.SURFACE_RAISED);
        retry.drawShape(view, layout, delta);
        songs.drawShape(view, layout, delta);
        view.endShapes();
        view.beginText();
        view.text("RESULTS", x + 4, layout.height() - 30, w - 8, UiTheme.HEADING, UiTheme.TEXT);
        if (runMode == GameplayRunMode.DEBUG_AUTO)
            view.text("AUTO / DEBUG", x + w - 142, layout.height() - 28, 138, UiTheme.META, UiTheme.ACCENT);
        view.text(set.title() + "  /  " + difficulty.version(), x + 28, top - 35, w - 56, UiTheme.TITLE, UiTheme.TEXT);
        view.text(set.artist() + "  ·  mapped by " + set.creator(), x + 28, top - 61,
                w - 56, UiTheme.META, UiTheme.MUTED);
        view.text("SCORE", x + 30, top - 120, 230, UiTheme.META, UiTheme.MUTED);
        view.text(scoreText, x + 26, top - 183,
                w * 0.62f, UiTheme.SCORE, UiTheme.TEXT);
        float rightX = x + w * 0.67f;
        view.text("ACCURACY", rightX, top - 115, w * 0.28f, UiTheme.META, UiTheme.MUTED);
        view.text(accuracyText, rightX, top - 153,
                w * 0.29f, UiTheme.TITLE, UiTheme.ACCENT);
        view.text("MAX COMBO", rightX, top - 202, w * 0.28f, UiTheme.META, UiTheme.MUTED);
        view.text(score.maxCombo() + "x", rightX, top - 240, w * 0.28f, UiTheme.TITLE, UiTheme.TEXT);
        for (int i = 0; i < 4; i++) {
            float sx = innerX + i * (statW + statGap);
            view.text(JUDGEMENT_LABELS[i], sx + 14, statsY + 68, statW - 28, UiTheme.META,
                    i == 3 ? UiTheme.ERROR : UiTheme.MUTED);
            view.text(Integer.toString(switch (i) {
                case 0 -> score.count300();
                case 1 -> score.count100();
                case 2 -> score.count50();
                default -> score.misses();
            }), sx + 14, statsY + 34, statW - 28, UiTheme.TITLE,
                    UiTheme.TEXT, Align.left);
        }
        retry.drawText(view); songs.drawText(view);
        view.endText();
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    @Override public void dispose() { backdrop.close(); }
    private void goRetry() { outgoing.request(() -> game.navigate(new GameplayScreen(game, set, difficulty, runMode))); }
    private void goSongs() { outgoing.request(() -> game.navigate(new SongSelectScreen(game, set.id(), set.difficulties().indexOf(difficulty)))); }

    public GameplayRunMode runMode() { return runMode; }
}
