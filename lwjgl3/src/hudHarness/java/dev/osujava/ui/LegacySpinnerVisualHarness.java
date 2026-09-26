package dev.osujava.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.gameplay.*;
import dev.osujava.ruleset.osu.OsuGameplaySession;
import dev.osujava.skin.OsuSkinAssets;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Actual GameplayRenderer, procedural transparent fixtures, absolute clock snapshots and input replay. */
public final class LegacySpinnerVisualHarness extends ApplicationAdapter {
    private record Scenario(String name, Path skin, double progress, long now, long completed, int width, int height, boolean replay) { }
    private final List<Scenario> scenes = new ArrayList<>();
    private final Path output;
    private SpriteBatch batch; private ShapeRenderer shapes; private BitmapFont font;
    private OsuJavaGame game; private OsuSkinAssets assets; private GameplayRenderer renderer;
    private int index; private boolean resizing; private final List<String> results = new ArrayList<>();
    private LegacySpinnerVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration(); config.setTitle("Legacy Spinner fixed clock captures");
        config.setWindowedMode(1024, 768); config.setForegroundFPS(30);
        new Lwjgl3Application(new LegacySpinnerVisualHarness(Path.of(args[0])), config);
    }
    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer(); font = new BitmapFont();
        game = new OsuJavaGame(null) {
            @Override public BitmapFont font() { return font; }
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
        };
        try {
            Files.createDirectories(output);
            Path oldBlink = fixture("old-blink", true, true, false, 1, false);
            Path oldNoBlink = fixture("old-no-blink", true, true, true, 1, false);
            Path modern = fixture("new-middle2", false, true, false, 1, false);
            Path noMiddle = fixture("new-no-middle2", false, false, false, 1, false);
            Path dense = fixture("new-2x", false, true, false, 2, false);
            Path partial = fixture("old-root-only", true, false, false, 1, true);
            for (Path skin : List.of(oldBlink, oldNoBlink, modern, noMiddle)) {
                String prefix = skin.getFileName().toString();
                for (double p : new double[]{0, .25, .5, .9, 1}) add(prefix + "-p" + (int)(p * 100), skin, p, 3000, p == 1 ? 2900 : Long.MIN_VALUE);
                for (long t : new long[]{400, 600, 800, 900, 1000, 2999, 3000, 3120, 3240, 3320, 3400, 4600, 4800, 4975, 5000, 5120, 5240})
                    add(prefix + "-t" + t, skin, t < 3000 ? .9 : 1, t, 3000);
                add(prefix + "-bonus-flash", skin, 1, 3600, 3000);
                add(prefix + "-maximum-bonus", skin, 1, 4200, 3000);
                scenes.add(new Scenario(prefix + "-input-replay", skin, 0, 3800, Long.MIN_VALUE, 1024, 768, true));
            }
            Path both = fixture("old-and-new-roots", true, true, true, 1, false);
            image(both, "spinner-top", 512, 512, 1, 0xff00ffff, "MUST NOT DRAW");
            add("background-wins", both, .5, 3000, Long.MIN_VALUE);
            Path newPartial = fixture("new-root-only", false, false, false, 1, true);
            add("new-missing-subpieces", newPartial, .5, 3000, Long.MIN_VALUE);
            add("density-2x", dense, .5, 3000, Long.MIN_VALUE);
            add("missing-subpieces", partial, .5, 3000, Long.MIN_VALUE);
            add("fallback-vector", null, .5, 3000, Long.MIN_VALUE);
            scenes.add(new Scenario("wide-16-9", modern, .5, 3000, Long.MIN_VALUE, 1280, 720, false));
            scenes.add(new Scenario("tall-aspect", oldBlink, .25, 3000, Long.MIN_VALUE, 600, 800, false));
            select();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private void add(String name, Path skin, double progress, long now, long complete) {
        scenes.add(new Scenario(name, skin, progress, now, complete, 1024, 768, false));
    }
    private Path fixture(String name, boolean old, boolean middle2, boolean noBlink, int density, boolean partial) throws Exception {
        Path path = output.resolve("fixtures/" + name); Files.createDirectories(path);
        Files.writeString(path.resolve("skin.ini"), "[General]\nSpinnerNoBlink: " + (noBlink ? 1 : 0) + "\n[Colours]\nSpinnerBackground: 100,130,170\n");
        if (old) image(path, "spinner-background", 1024, 768, density, 0x88707070, "BACKGROUND");
        else image(path, "spinner-top", 512, 512, density, 0xbbe8df81, "TOP");
        if (partial) return path;
        if (old) {
            image(path, "spinner-circle", 512, 512, density, 0xaaf0f0f0, "CIRCLE");
            image(path, "spinner-metre", 100, 692, density, 0xdd67e090, "METRE");
        } else {
            image(path, "spinner-bottom", 550, 550, density, 0x999ccfe0, "BOTTOM");
            image(path, "spinner-glow", 600, 600, density, 0x447fffff, "GLOW");
            if (middle2) image(path, "spinner-middle2", 210, 210, density, 0xbb9cd894, "MIDDLE2");
            image(path, "spinner-middle", 110, 110, density, 0xffeeeeee, "MIDDLE");
        }
        image(path, "spinner-approachcircle", 512, 512, density, 0xbbffffff, "APPROACH");
        image(path, "spinner-spin", 250, 70, density, 0xffeeeeee, "SPIN!");
        image(path, "spinner-clear", 250, 70, density, 0xffeeeeee, "CLEAR!");
        image(path, "spinner-rpm", 280, 50, density, 0xaa333366, "RPM");
        for (int n = 0; n < 10; n++) image(path, "score-" + n, n == 1 ? 16 : 24, 32, density, 0xffeeeeee, "" + n);
        return path;
    }
    private void image(Path path, String name, int w, int h, int density, int argb, String label) throws Exception {
        BufferedImage image = new BufferedImage(w * density, h * density, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); g.scale(density, density); g.setColor(new java.awt.Color(argb, true));
        g.setStroke(new BasicStroke(8));
        if (name.equals("spinner-metre")) {
            for (int n = 0; n < 10; n++) g.fillRect(4, n * 69 + 5, 90, 60);
        } else if (w == h) {
            g.drawOval(8, 8, w - 16, h - 16); g.drawLine(w / 2, h / 2, w - 15, h / 2);
        } else if (name.equals("spinner-background")) g.fillRect(0, 0, w, h);
        else if (name.equals("spinner-rpm")) g.fillRoundRect(0, 0, w, h, 12, 12);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.min(28, h - 4)));
        g.drawString(label, Math.max(0, (w - g.getFontMetrics().stringWidth(label)) / 2), h / 2 + 10);
        g.dispose(); ImageIO.write(image, "png", path.resolve(name + (density == 2 ? "@2x" : "") + ".png").toFile());
    }
    private void select() {
        if (renderer != null) renderer.dispose(); if (assets != null) assets.dispose();
        var scene = scenes.get(index); assets = new OsuSkinAssets(scene.skin); renderer = new GameplayRenderer(game, GameplayVisualConfig.defaults(), assets);
        resizing = Gdx.graphics.getWidth() != scene.width || Gdx.graphics.getHeight() != scene.height;
        if (resizing) Gdx.graphics.setWindowedMode(scene.width, scene.height);
    }
    @Override public void render() {
        var scene = scenes.get(index);
        if (resizing) { resizing = false; return; }
        var state = snapshot(scene);
        draw(state); byte[] first = capture(scene.name + ".png");
        // Change time and progress, then seek/replay the exact snapshot. Real replay uses fresh Session/Input APIs.
        draw(new GameplayState(900, List.of(), List.of(), List.of(), state.score(), false));
        draw(snapshot(scene)); byte[] again = capture(null);
        if (!Arrays.equals(first, again)) throw new AssertionError("Pixel mismatch after seek/input replay: " + scene.name);
        results.add(scene.name + " pixels=identical logical=" + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
                + " framebuffer=" + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight());
        if (++index == scenes.size()) {
            try { Files.write(output.resolve("results.txt"), results); } catch (Exception e) { throw new RuntimeException(e); }
            Gdx.app.exit(); return;
        }
        select();
    }
    private GameplayState snapshot(Scenario scene) {
        if (scene.replay) {
            long[] time = {1000}; GameClock clock = () -> time[0];
            var hit = new HitObject(256, 192, 1000, HitObject.Type.SPINNER, 8, 0, null, new SpinnerData(5000));
            var diff = new BeatmapDifficulty("Spinner", "Harness", "fixture", "fixed", 0, "", "",
                    new DifficultySettings(5, 5, 0, 9, 1.4, 1), List.of(), List.of(hit), null, null);
            var session = new OsuGameplaySession(diff, clock, new JudgementWindows(50, 100, 150));
            session.press(GameInputAction.LEFT, 336, 192);
            for (int n = 1; n <= 112; n++) {
                time[0] = 1000 + 25 * n; double angle = n * Math.PI / 2;
                session.pointerMoved(256 + 80 * Math.cos(angle), 192 + 80 * Math.sin(angle)); session.update();
            }
            return session.state();
        }
        var events = List.of(new SpinnerVisual.SpinEvent(3600, 50, false, true), new SpinnerVisual.SpinEvent(4200, 100, true, true));
        var s = new SpinnerVisual(256, 192, 140, scene.progress, (scene.now - 1000) * .19, 3600 * scene.progress,
                (int)(10 * scene.progress), 10, 1000, 5000, true, scene.now >= 5000 ? Judgement.HIT300 : null,
                600, 345.999, scene.completed, 100, 0, events);
        var score = new ScoreState(0, 0, 0, 0, 0, 0, 0, 1);
        return new GameplayState(scene.now, List.of(), List.of(), List.of(s), score, false);
    }
    private void draw(GameplayState state) {
        int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        var projection = new Matrix4().setToOrtho2D(0, 0, width, height);
        batch.setProjectionMatrix(projection); shapes.setProjectionMatrix(projection);
        renderer.render(null, null, state, PlayfieldViewport.fit(width, height), null, "");
    }
    private byte[] capture(String name) {
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            byte[] pixels = new byte[pixmap.getPixels().remaining()]; pixmap.getPixels().duplicate().get(pixels);
            if (name != null) PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name).toString()), pixmap, -1, true);
            return pixels;
        } finally { pixmap.dispose(); }
    }
    @Override public void dispose() { renderer.dispose(); assets.dispose(); batch.dispose(); shapes.dispose(); font.dispose(); }
}
