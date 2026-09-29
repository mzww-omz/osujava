package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.ScoreState;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.score.ResultsSnapshot;
import dev.osujava.score.ScoreDetails;
import dev.osujava.skin.LegacyHudLayout;
import dev.osujava.skin.ResultsSkinAssets;
import dev.osujava.skin.ResultsSkinAssets.Image;
import dev.osujava.ui.theme.BeatmapBackdrop;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

/** Local NoMod ranking view. Ruleset owns values; this screen owns assets and presentation only. */
public final class ResultsScreen extends ScreenAdapter {
    private static final Image[] HITS = {Image.HIT300, Image.HIT100, Image.HIT50, Image.GEKI, Image.KATU, Image.MISS};
    private static final Color VEIL = new Color(0, 0, 0, .35f), HEADER = new Color(0, 0, 0, .55f);
    private static final Color HEALTHY = new Color(154 / 255f, 205 / 255f, 50 / 255f, 1);
    private final OsuJavaGame game;
    private final BeatmapSet set;
    private final BeatmapDifficulty difficulty;
    private final ResultsSnapshot snapshot;
    private final ResultsPresentation values;
    private final ResultsHealthGraph graph;
    private final UiView view;
    private final UiNavigation outgoing = new UiNavigation();
    private final BeatmapBackdrop backdrop = new BeatmapBackdrop();
    private final ResultsAnimation animation = new ResultsAnimation();
    private final Matrix4 projection = new Matrix4();
    private final Random random = new Random();
    private final String date, timingText;
    private ResultsSkinAssets skin;
    private ResultsLayout layout;

    public ResultsScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty, ScoreState score) {
        this(game, set, difficulty, score, GameplayRunMode.MANUAL);
    }
    public ResultsScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty, ScoreState score, GameplayRunMode runMode) {
        this(game, set, difficulty, new ResultsSnapshot(score, ScoreDetails.LEGACY, System.currentTimeMillis(), runMode, false));
    }
    public ResultsScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty, ResultsSnapshot snapshot) {
        this.game = game; this.set = set; this.difficulty = difficulty; this.snapshot = snapshot;
        values = ResultsPresentation.of(snapshot);
        graph = new ResultsHealthGraph(snapshot.details().health());
        view = new UiView(game);
        date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(snapshot.playedAt()));
        var timing = values.timing();
        timingText = timing == null ? null : String.format(Locale.ROOT,
                "Accuracy: %.2f ms - %.2f ms\nUnstable Rate: %.2f", timing.negativeMean(), timing.nonnegativeMean(), timing.unstableRate());
    }

    @Override public void show() {
        if (skin == null) skin = new ResultsSkinAssets(game.skinDirectory(), game.skinFallbackDirectory());
        backdrop.select(set, difficulty);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (key == Input.Keys.ESCAPE) { goSongs(); return true; }
                if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) { animation.skip(); return true; }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                if (button != Input.Buttons.LEFT || layout == null || outgoing.pending()) return false;
                float px = layout.pointer(x), py = layout.pointer(y);
                if (hit(Image.RETRY, layout.width(), 360, 1, .5f, px, py)) { animation.skip(); goRetry(); return true; }
                if (hit(Image.BACK, 0, 480, 0, 1, px, py)) { goSongs(); return true; }
                return false;
            }
        });
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        animation.advance(delta);
        view.prepare(); view.clear(); backdrop.draw(view, delta);
        layout = ResultsLayout.fit(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), skin.legacy());
        projection.setToOrtho2D(0, 0, layout.width(), 480);
        game.batch().setProjectionMatrix(projection); game.shapes().setProjectionMatrix(projection);
        view.beginShapes();
        view.box(0, 0, layout.width(), 480, 0, VEIL);
        view.box(0, 430, layout.width(), 50, 0, HEADER);
        view.endShapes();
        game.batch().begin();
        draw(Image.PANEL, 0, layout.panelY(), 1, 1, 0, 0);
        draw(Image.GRAPH, 160, layout.graphY(), 1, 1, 0, 0);
        numbers(animation.score(values.score(), random), 220, 94, layout.scoreScale(), 1, .5f, .5f, true);
        draw(Image.COMBO, 5, layout.labelY(), 1, animation.item(6, false), 0, 0);
        draw(Image.ACCURACY, 182, layout.labelY(), 1, animation.item(7, false), 0, 0);
        float comboAlpha = animation.item(6, true), accuracyAlpha = animation.item(7, true);
        numbers(values.combo(), 15 - 40 * (1 - comboAlpha), 330, 1.12f, comboAlpha, 0, 0, false);
        numbers(values.accuracy(), 194 - 40 * (1 - accuracyAlpha), 330, 1.12f, accuracyAlpha, 0, 0, false);
        for (int i = 0; i < HITS.length; i++) {
            float number = animation.item(i, true);
            numbers(values.counts().get(i), layout.hitX(i) + 40 - 40 * (1 - number), layout.countY(i), 1.12f, number, 0, 0, false);
        }
        game.batch().end();
        view.beginShapes();
        float inset = skin.legacy() ? 10 : 5;
        for (var line : graph.reveal(animation.graph())) {
            game.shapes().setColor(line.healthy() ? HEALTHY : Color.RED);
            game.shapes().rectLine(160 + inset + line.x1(), 480 - layout.graphY() - inset - line.y1(),
                    160 + inset + line.x2(), 480 - layout.graphY() - inset - line.y2(), 1);
        }
        view.endShapes();
        game.batch().begin();
        for (int i = 0; i < HITS.length; i++) {
            float image = animation.item(i, false);
            draw(HITS[i], layout.hitX(i), layout.hitY(i), 1 - .5f * image, image, .5f, .5f);
        }
        float grade = animation.grade(values.perfect());
        draw(values.grade(), layout.width() - 120, layout.gradeY(), 2 - grade, grade, .5f, .5f);
        if (values.grade() == Image.SS || values.grade() == Image.S || values.grade() == Image.A || values.grade() == Image.B) {
            float glow = animation.gradeGlow(values.perfect());
            game.batch().setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            draw(values.grade(), layout.width() - 120, layout.gradeY(), 1 + .05f * (1 - glow), glow, .5f, .5f);
            game.batch().setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        draw(Image.RETRY, layout.width(), 360, 1, 1, 1, .5f);
        if (values.perfect()) draw(Image.PERFECT, layout.perfectX(), 430, 1, animation.item(8, false), .5f, .5f);
        draw(Image.TITLE, layout.width() - 20, 0, 1, 1, 1, 0);
        text(set.artist() + " - " + set.title() + " [" + difficulty.version() + "]", 5, 5, layout.width() - 165, .75f);
        text("Beatmap by " + set.creator(), 5, 20, layout.width() - 165, .6f);
        text(date + (snapshot.runMode() == GameplayRunMode.DEBUG_AUTO ? "  AUTO / DEBUG" : ""), 5, 34, layout.width() - 165, .6f);
        draw(Image.BACK, 0, 480, 1, 1, 0, 1);
        game.batch().end();
        drawTimingTooltip();
        game.batch().setColor(Color.WHITE); game.font().setColor(Color.WHITE); game.font().getData().setScale(1);
        if (outgoing.pending()) {
            view.beginShapes(); view.box(0, 0, layout.width(), 480, 0, new Color(0, 0, 0, outgoing.opacity())); view.endShapes();
        }
    }

    private void drawTimingTooltip() {
        if (timingText == null || animation.item(7, true) < 1) return;
        float x = layout.pointer(Gdx.input.getX()), y = layout.pointer(Gdx.input.getY());
        if (x < 182 || x > 380 || y < layout.labelY() || y > 360) return;
        float left = Math.min(x + 8, layout.width() - 245), top = Math.min(y + 12, 432);
        view.beginShapes(); view.box(left, 480 - top - 43, 240, 43, 0, new Color(0, 0, 0, .92f)); view.endShapes();
        game.batch().begin(); text(timingText, left + 6, top + 6, 228, .7f); game.batch().end();
    }

    private void draw(Image image, float x, float y, float scale, float alpha, float originX, float originY) {
        var asset = skin.get(image);
        if (asset == null || alpha <= 0) return;
        float w = asset.width() * ResultsLayout.IMAGE_SCALE * scale, h = asset.height() * ResultsLayout.IMAGE_SCALE * scale;
        game.batch().setColor(1, 1, 1, alpha);
        game.batch().draw(asset.texture(), x - originX * w, 480 - y - (1 - originY) * h, w, h);
        game.batch().setColor(Color.WHITE);
    }
    private boolean hit(Image image, float x, float y, float originX, float originY, float px, float py) {
        var asset = skin.get(image);
        if (asset == null) return false;
        float w = asset.width() * ResultsLayout.IMAGE_SCALE, h = asset.height() * ResultsLayout.IMAGE_SCALE;
        float left = x - originX * w, top = y - originY * h;
        return px >= left && px <= left + w && py >= top && py <= top + h;
    }
    private void numbers(String value, float x, float y, float scale, float alpha, float ox, float oy, boolean fixed) {
        if (alpha <= 0) return;
        if (!skin.hasText(value)) {
            game.font().setColor(1, 1, 1, alpha);
            text(value, x, y, 170, scale);
            game.font().setColor(Color.WHITE);
            return;
        }
        var glyphs = LegacyHudLayout.create(value, c -> {
            var glyph = skin.glyph(c); return glyph == null ? null : new LegacyHudLayout.Size(glyph.width(), glyph.height());
        }, fixed && !skin.legacy() ? -2 : skin.overlap(), fixed);
        float unit = scale * ResultsLayout.IMAGE_SCALE;
        float left = x - ox * glyphs.width() * unit, top = y - oy * glyphs.height() * unit;
        game.batch().setColor(1, 1, 1, alpha);
        for (var glyph : glyphs.glyphs()) game.batch().draw(skin.glyph(glyph.character()).texture(),
                left + glyph.x() * unit, 480 - top - (glyph.y() + glyph.height()) * unit,
                glyph.width() * unit, glyph.height() * unit);
        game.batch().setColor(Color.WHITE);
    }
    private void text(String value, float left, float top, float width, float scale) {
        game.font().getData().setScale(scale);
        game.font().draw(game.batch(), value, left, 480 - top, 0, value.length(), width, Align.left, false, "...");
    }
    @Override public void dispose() { backdrop.close(); if (skin != null) { skin.dispose(); skin = null; } }
    private void goRetry() { outgoing.request(() -> game.navigate(new GameplayScreen(game, set, difficulty, snapshot.runMode()))); }
    private void goSongs() { outgoing.request(() -> game.navigate(new SongSelectScreen(game, set.id(), set.difficulties().indexOf(difficulty)))); }
    public GameplayRunMode runMode() { return snapshot.runMode(); }
}
