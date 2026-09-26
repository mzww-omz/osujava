package dev.osujava.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.gameplay.*;
import dev.osujava.ruleset.osu.*;
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.skin.OsuSkinAssets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual foreground renderer, procedural asymmetric PNG fixtures, scripted input, fixed GameClock.
 * Each capture replays twice and asserts identical framebuffer bytes. Output never enters Library/git.
 */
public final class LegacyCursorVisualHarness extends ApplicationAdapter {
    private enum History { STATIC, PRESS, STRAIGHT, CURVE, FAST, SLOW, AUTO, AUTO_30, AUTO_60 }
    private record Scene(String name, Path skin, History history, int now) { }
    private final Path output;
    private final List<Scene> scenes = new ArrayList<>();
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private OsuJavaGame game;
    private GameplayCursorVisibility visibility;
    private int index;
    private LegacyCursorVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration(); config.setTitle("osu!java fixed-clock cursor capture");
        config.setWindowedMode(1024, 768); config.setForegroundFPS(30);
        new Lwjgl3Application(new LegacyCursorVisualHarness(Path.of(args[0])), config);
    }
    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer(); font = new BitmapFont();
        game = new OsuJavaGame(null) {
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
        };
        visibility = new GameplayCursorVisibility(Gdx.graphics);
        visibility.show(); visibility.hide(); visibility.show(); // Exercise native lifecycle too.
        try {
            Files.createDirectories(output);
            Path still = fixture("static", true, false, true, true, false, 1);
            Path rotating = fixture("rotating", true, true, true, true, false, 1);
            Path corner = fixture("top-left", false, true, true, true, true, 1);
            Path noExpand = fixture("no-expand", true, false, false, true, true, 1);
            Path connected = fixture("connected", true, true, true, true, true, 1);
            Path disjoint = fixture("disjoint", true, true, true, false, true, 1);
            Path noTrailRotate = fixture("trail-fixed", true, true, true, true, true, 1, false);
            Path dense = fixture("density-2x", true, true, true, true, true, 2);
            scenes.add(new Scene("skin-static", still, History.STATIC, 2500));
            for (int time : new int[]{0, 2500, 5000, 10000}) scenes.add(new Scene("rotating-" + time, rotating, History.STATIC, time));
            scenes.add(new Scene("cursor-centre-0", corner, History.STATIC, 2500));
            scenes.add(new Scene("cursor-expand-0", noExpand, History.PRESS, 1100));
            for (int time : new int[]{950, 1000, 1050, 1100, 1250, 1300})
                scenes.add(new Scene("press-release-" + time, still, History.PRESS, time));
            scenes.add(new Scene("cursormiddle-transform", connected, History.PRESS, 1100));
            scenes.add(new Scene("no-cursormiddle", disjoint, History.PRESS, 1100));
            scenes.add(new Scene("connected-straight", connected, History.STRAIGHT, 2500));
            scenes.add(new Scene("connected-curve", connected, History.CURVE, 2500));
            scenes.add(new Scene("disjoint-movement", disjoint, History.CURVE, 2500));
            scenes.add(new Scene("fast-movement", connected, History.FAST, 2500));
            scenes.add(new Scene("slow-movement", connected, History.SLOW, 3000));
            scenes.add(new Scene("cursor-trail-rotate-0", noTrailRotate, History.STRAIGHT, 2500));
            scenes.add(new Scene("cursor-trail-rotate-1", connected, History.STRAIGHT, 2500));
            scenes.add(new Scene("density-2x", dense, History.CURVE, 2500));
            scenes.add(new Scene("no-skin-fallback", null, History.CURVE, 2500));
            scenes.add(new Scene("skin-cursor-no-trail", rotating, History.CURVE, 2500));
            // Trail-only skin must combine safely with the vector fallback cursor, using no cursor provider.
            Path onlyTrail = output.resolve("fixtures/trail-only"); Files.createDirectories(onlyTrail);
            image(onlyTrail.resolve("cursortrail.png"), "cursortrail", 1);
            scenes.add(new Scene("fallback-cursor-skin-trail", onlyTrail, History.CURVE, 2500));
            scenes.add(new Scene("debug-auto-cursor", connected, History.AUTO, 1600));
            scenes.add(new Scene("debug-auto-30hz", connected, History.AUTO_30, 1600));
            scenes.add(new Scene("debug-auto-60hz", connected, History.AUTO_60, 1600));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private Path fixture(String name, boolean centre, boolean rotate, boolean expand, boolean middle, boolean trail, int density) throws Exception {
        return fixture(name, centre, rotate, expand, middle, trail, density, true);
    }
    private Path fixture(String name, boolean centre, boolean rotate, boolean expand, boolean middle, boolean trail, int density, boolean trailRotate) throws Exception {
        Path path = output.resolve("fixtures/" + name); Files.createDirectories(path);
        Files.writeString(path.resolve("skin.ini"), "[General]\nCursorCentre: " + (centre ? 1 : 0)
                + "\nCursorRotate: " + (rotate ? 1 : 0) + "\nCursorExpand: " + (expand ? 1 : 0)
                + "\nCursorTrailRotate: " + (trailRotate ? 1 : 0));
        for (String piece : new String[]{"cursor", "cursormiddle", "cursortrail"}) {
            if (piece.equals("cursormiddle") && !middle || piece.equals("cursortrail") && !trail) continue;
            image(path.resolve(piece + (density == 2 ? "@2x" : "") + ".png"), piece, density);
            if (density == 2) {
                var wrong = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
                var g = wrong.createGraphics(); g.setColor(java.awt.Color.RED); g.fillRect(0, 0, 160, 160); g.dispose();
                ImageIO.write(wrong, "png", path.resolve(piece + ".png").toFile());
            }
        }
        return path;
    }
    private void image(Path path, String piece, int density) throws Exception {
        int w = piece.equals("cursor") ? 64 : piece.equals("cursormiddle") ? 12 : 24;
        int h = piece.equals("cursor") ? 48 : 12;
        var image = new BufferedImage(w * density, h * density, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); g.scale(density, density);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (piece.equals("cursor")) {
            g.setColor(new java.awt.Color(255, 213, 71)); g.setStroke(new java.awt.BasicStroke(3));
            g.drawOval(11, 3, 42, 42); g.setColor(new java.awt.Color(75, 220, 255));
            g.fillPolygon(new int[]{32, 62, 32, 42}, new int[]{6, 24, 42, 24}, 4);
        } else if (piece.equals("cursormiddle")) {
            g.setColor(new java.awt.Color(255, 85, 170)); g.fillRect(1, 1, 10, 10);
            g.setColor(java.awt.Color.WHITE); g.fillRect(4, 4, 4, 4);
        } else {
            g.setColor(new java.awt.Color(64, 180, 230, 140)); g.fillOval(0, 0, 24, 12);
            g.setColor(new java.awt.Color(100, 220, 255, 210)); g.fillOval(4, 3, 16, 6);
        }
        g.dispose(); ImageIO.write(image, "png", path.toFile());
    }
    private LegacyCursorVisual replay(Scene scene, GameplayCursorRenderer cursorRenderer) {
        var v = cursorRenderer.createVisual();
        if (scene.history == History.STATIC || scene.history == History.PRESS) {
            v.input(0, new GameplaySession.PointerState(256, 192, false), false);
            if (scene.history == History.PRESS) {
                if (scene.now >= 1000) v.input(1000, new GameplaySession.PointerState(256, 192, true), true);
                if (scene.now >= 1200) v.input(1200, new GameplaySession.PointerState(256, 192, false), false);
            }
        } else if (scene.history == History.AUTO || scene.history == History.AUTO_30 || scene.history == History.AUTO_60) {
            var clock = new FixedClock();
            var slider = new HitObject(160, 192, 1000, HitObject.Type.SLIDER, 2, 0,
                    new SliderData(List.of(new SliderData.Segment(SliderData.CurveType.BEZIER, 0,
                            List.of(new BeatmapPoint(160, 192), new BeatmapPoint(270, 80), new BeatmapPoint(400, 220)))), 0, 280));
            var difficulty = new BeatmapDifficulty("Cursor", "Harness", "Local", "Auto", 0, "", "",
                    new DifficultySettings(5, 5, 5, 5, 1.4, 1),
                    List.of(new TimingPoint(0, 500, 4, 0, 0, 100, true, 0)), List.of(slider), null, null);
            var actual = new OsuGameplaySession(difficulty, clock, JudgementWindows.fromOverallDifficulty(5));
            var session = new CursorTrackingSession(actual, clock, v);
            var auto = new DebugAutoPlayer(difficulty, clock, session);
            int step = scene.history == History.AUTO_30 ? 33 : scene.history == History.AUTO_60 ? 16 : 5;
            for (int t = 0; ; t = Math.min(scene.now, t + step)) {
                clock.now = t; auto.update(); session.update(); auto.afterSessionUpdate(); v.advance(t);
                if (v.positioned() && (v.cursor(t).x() != auto.cursorX() || v.cursor(t).y() != auto.cursorY()))
                    throw new AssertionError("Auto source diverged from the Session input");
                if (t == scene.now) {
                    var path = new SliderPath(slider.x(), slider.y(), slider.sliderData());
                    var timing = SliderTiming.calculate(difficulty, slider, path);
                    var expected = path.positionAt(timing.progressAt(t));
                    if (Math.hypot(auto.cursorX() - expected.x(), auto.cursorY() - expected.y()) > 1e-6)
                        throw new AssertionError("Auto sampling changed position at common absolute time");
                    break;
                }
            }
        } else {
            for (int t = 0; t <= scene.now - 2200; t += 5) {
                double x = 100 + t * .65, y = 192;
                if (scene.history == History.CURVE) y += Math.sin(t / 90.0) * 80;
                if (scene.history == History.FAST) { x = -100 + t * 2.4; y += Math.sin(t / 100.0) * 30; }
                if (scene.history == History.SLOW) { x = 200 + t * .04; y += Math.sin(t / 600.0) * 10; }
                v.input(2200 + t, new GameplaySession.PointerState(x, y, t < 200), t == 0);
            }
        }
        v.advance(scene.now); return v;
    }
    @Override public void render() {
        var scene = scenes.get(index);
        var projection = new Matrix4().setToOrtho2D(0, 0, 1024, 768);
        batch.setProjectionMatrix(projection); shapes.setProjectionMatrix(projection);
        try (var resources = new Resources(scene.skin)) {
            var cursorRenderer = new GameplayCursorRenderer(resources.assets);
            byte[] first = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                var visual = replay(scene, cursorRenderer);
                var piece = visual.cursor(scene.now);
                var circle = new HitCircleVisual(piece.x(), piece.y(), 32, 32, scene.now + 300, 1200, 7, 0);
                var score = new ScoreState(0, 0, 0, 0, 0, 0, 0, 1);
                var state = new GameplayState(scene.now, List.of(circle), score, false);
                var viewport = PlayfieldViewport.fit(1024, 768);
                resources.renderer.render(null, null, state, viewport, null, "Cursor is above HitObjects and HUD");
                shapes.begin(ShapeRenderer.ShapeType.Line); shapes.setColor(Color.WHITE);
                float px = viewport.toScreenX(piece.x()), py = viewport.toScreenY(piece.y());
                shapes.line(px - 65, py, px + 65, py); shapes.line(px, py - 65, px, py + 65); shapes.end();
                cursorRenderer.draw(batch, shapes, visual, scene.now, viewport);
                batch.begin(); font.setColor(Color.WHITE);
                font.draw(batch, scene.name + " | t=" + scene.now + "ms | parts=" + visual.parts().size() + " | repeat pixels checked", 24, 738); batch.end();
                Pixmap capture = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                try {
                    byte[] pixels = new byte[capture.getPixels().remaining()]; capture.getPixels().duplicate().get(pixels);
                    if (attempt == 0) {
                        first = pixels;
                        PixmapIO.writePNG(Gdx.files.absolute(output.resolve(scene.name + ".png").toString()), capture, -1, true);
                    } else if (!Arrays.equals(first, pixels)) throw new AssertionError("Replay changed pixels: " + scene.name);
                } finally { capture.dispose(); }
            }
        }
        if (++index == scenes.size()) { System.out.println("Cursor harness: " + index + " captures, all replay pixel checks passed: " + output); Gdx.app.exit(); }
    }
    private final class Resources implements AutoCloseable {
        final OsuSkinAssets assets;
        final GameplayRenderer renderer;
        Resources(Path skin) { assets = new OsuSkinAssets(skin); renderer = new GameplayRenderer(game, GameplayVisualConfig.defaults(), assets); }
        @Override public void close() { renderer.dispose(); assets.dispose(); }
    }
    private static final class FixedClock implements GameClock {
        long now;
        @Override public long nowMs() { return now; }
    }
    @Override public void dispose() { visibility.close(); batch.dispose(); shapes.dispose(); font.dispose(); }
}
