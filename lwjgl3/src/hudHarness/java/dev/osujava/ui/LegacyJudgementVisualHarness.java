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
import dev.osujava.gameplay.*;
import dev.osujava.skin.OsuSkinAssets;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Procedural labelled skin fixtures, actual GameplayRenderer, immutable fixed-clock snapshots.
 * PNGs and fixtures are generated exclusively under the supplied output directory, never the library.
 */
public final class LegacyJudgementVisualHarness extends ApplicationAdapter {
    private static final int[] AGES = {0, 60, 120, 144, 168, 500, 800, 1100, 1500, 60};
    private record Scenario(String name, Path skin, Judgement result, JudgementVisual.Kind kind) { }
    private final Path output;
    private final List<Scenario> scenarios = new ArrayList<>();
    private BitmapFont font;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private OsuSkinAssets assets;
    private GameplayRenderer renderer;
    private int scenarioIndex, ageIndex;
    private byte[] sixtyMsPixels;
    private OsuJavaGame game;
    private LegacyJudgementVisualHarness(Path output) { this.output = output; }

    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java legacy judgement fixed clock capture");
        config.setWindowedMode(1024, 768); config.setForegroundFPS(30);
        new Lwjgl3Application(new LegacyJudgementVisualHarness(Path.of(args[0])), config);
    }
    @Override public void create() {
        font = new BitmapFont(); batch = new SpriteBatch(); shapes = new ShapeRenderer();
        game = new OsuJavaGame(null) {
            @Override public BitmapFont font() { return font; }
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
        };
        try {
            Files.createDirectories(output);
            Path old = fixture("static-v1", 1, false, false, 1);
            Path modern = fixture("static-v2", 2, false, false, 1);
            Path animated = fixture("animated-v2", 2, true, false, 1);
            Path particle = fixture("particle-v2", 2, false, true, 1);
            Path animatedParticle = fixture("animated-particle-v2", 2, true, true, 1);
            Path dense = fixture("static-2x-v2", 2, false, false, 2);
            for (Judgement result : Judgement.values()) scenarios.add(new Scenario(result.name().toLowerCase() + "-static", modern, result, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("miss-v1", old, Judgement.MISS, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("animated-300", animated, Judgement.HIT300, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("animated-miss", animated, Judgement.MISS, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("particle-300", particle, Judgement.HIT300, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("animated-particle-300", animatedParticle, Judgement.HIT300, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("density-2x-300", dense, Judgement.HIT300, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("no-skin", null, Judgement.HIT300, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("tail-miss", modern, Judgement.MISS, JudgementVisual.Kind.SLIDER_TAIL));
            scenarios.add(new Scenario("tail-point-v1", old, Judgement.HIT300, JudgementVisual.Kind.SLIDER_TAIL));
            scenarios.add(new Scenario("tail-hit-v2-empty", modern, Judgement.HIT300, JudgementVisual.Kind.SLIDER_TAIL));
            // One result provided, another missing, and a corrupt asset must not produce any judgement visual.
            Path partial = output.resolve("fixtures/partial"); Files.createDirectories(partial);
            image(partial.resolve("hit300.png"), "300", 0x64dbfa, 1);
            Files.writeString(partial.resolve("hit50.png"), "not a PNG");
            scenarios.add(new Scenario("partial-300", partial, Judgement.HIT300, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("partial-100-empty", partial, Judgement.HIT100, JudgementVisual.Kind.CIRCLE));
            scenarios.add(new Scenario("broken-50-empty", partial, Judgement.HIT50, JudgementVisual.Kind.CIRCLE));
            selectScenario();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private Path fixture(String name, int version, boolean animated, boolean particle, int density) throws Exception {
        Path path = output.resolve("fixtures/" + name); Files.createDirectories(path);
        Files.writeString(path.resolve("skin.ini"), "[General]\nVersion: " + version + "\nAnimationFramerate: 2\n");
        String suffix = density == 2 ? "@2x" : "";
        String[] names = {"hit300", "hit100", "hit50", "hit0", "sliderendmiss", "sliderpoint10"};
        String[] labels = {"300", "100", "50", "MISS", "END X", "10"};
        int[] colours = {0x64dbfa, 0x6cf576, 0xf5ce6c, 0xf56c89, 0xf56c89, 0xffffff};
        for (int n = 0; n < names.length; n++) {
            image(path.resolve(names[n] + suffix + ".png"), labels[n], colours[n], density);
            if (animated && n < 4) for (int frame = 0; frame < 12; frame++)
                image(path.resolve(names[n] + "-" + frame + suffix + ".png"), labels[n] + " f" + frame, colours[n], density);
        }
        if (particle) particle(path.resolve("particle300.png"));
        return path;
    }
    private void image(Path path, String label, int rgb, int density) throws Exception {
        var image = new BufferedImage(160 * density, 64 * density, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); g.scale(density, density);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28)); g.setColor(new java.awt.Color(rgb));
        var metrics = g.getFontMetrics(); g.drawString(label, (160 - metrics.stringWidth(label)) / 2, 42);
        g.dispose(); ImageIO.write(image, "png", path.toFile());
    }
    private void particle(Path path) throws Exception {
        var image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        // Skin texture fixture, not a substitute renderer effect: 150 copies use the loaded PNG.
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            double d = Math.hypot(x - 5.5, y - 5.5);
            int alpha = (int) (255 * Math.max(0, 1 - d / 6));
            image.setRGB(x, y, alpha << 24 | 0x64dbfa);
        }
        ImageIO.write(image, "png", path.toFile());
    }
    private void selectScenario() {
        if (renderer != null) renderer.dispose(); if (assets != null) assets.dispose();
        assets = new OsuSkinAssets(scenarios.get(scenarioIndex).skin);
        renderer = new GameplayRenderer(game, GameplayVisualConfig.defaults(), assets);
    }
    @Override public void render() {
        var scene = scenarios.get(scenarioIndex);
        int age = AGES[ageIndex]; long now = 1000 + age;
        var matrix = new Matrix4().setToOrtho2D(0, 0, 1024, 768);
        batch.setProjectionMatrix(matrix); shapes.setProjectionMatrix(matrix);
        var visual = new JudgementVisual(256, 192, 32, scene.result, 1000, scene.kind, 0, 42);
        var unoccluded = new JudgementVisual(128, 192, 32, scene.result, 1000, scene.kind, 0, 42);
        // An overlapping HitObject makes the old/new proxy difference visible in every capture.
        var circle = new HitCircleVisual(266, 192, 32, 32, now + 300, 1200, 7, 0);
        var score = new ScoreState(0, 0, 0, 0, 0, 0, 0, 1);
        var state = new GameplayState(now, List.of(circle), List.of(), List.of(), score, false, List.of(unoccluded, visual));
        renderer.render(null, null, state, PlayfieldViewport.fit(1024, 768), null, "");
        drawLabel(scene, age);
        Pixmap capture = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        byte[] pixels = new byte[capture.getPixels().remaining()];
        capture.getPixels().duplicate().get(pixels);
        if (ageIndex == 1) sixtyMsPixels = pixels;
        if (ageIndex == AGES.length - 1 && !java.util.Arrays.equals(sixtyMsPixels, pixels))
            throw new AssertionError("Seek changed fixed-time pixels in " + scene.name);
        try { PixmapIO.writePNG(Gdx.files.absolute(output.resolve(scene.name + "-" + age + "ms.png").toString()), capture, -1, true); }
        finally { capture.dispose(); }
        if (assets.judgement(dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Result.from(visual)) == null) {
            var empty = new GameplayState(now, List.of(circle), List.of(), List.of(), score, false, List.of());
            renderer.render(null, null, empty, PlayfieldViewport.fit(1024, 768), null, "");
            drawLabel(scene, age);
            Pixmap baseline = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try {
                byte[] baselinePixels = new byte[baseline.getPixels().remaining()];
                baseline.getPixels().duplicate().get(baselinePixels);
                if (!java.util.Arrays.equals(pixels, baselinePixels))
                    throw new AssertionError("Missing skin asset still draws a judgement in " + scene.name + " at " + age);
            } finally { baseline.dispose(); }
        }
        if (++ageIndex == AGES.length) {
            ageIndex = 0;
            if (++scenarioIndex == scenarios.size()) { Gdx.app.exit(); return; }
            selectScenario();
        }
    }
    private void drawLabel(Scenario scene, int age) {
        batch.begin(); font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        font.draw(batch, scene.name + " +" + age + "ms (fixed clock; left: unobstructed, right: overlapping HitObject)", 30, 735); batch.end();
    }
    @Override public void dispose() {
        renderer.dispose(); assets.dispose(); font.dispose(); batch.dispose(); shapes.dispose();
    }
}
