package dev.osujava.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import dev.osujava.OsuJavaGame;
import dev.osujava.gameplay.*;
import dev.osujava.skin.OsuSkinAssets;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Real renderer / OpenGL capture at fixed gameplay times. Writes only to the requested output directory.
 * Run: ./gradlew :lwjgl3:hudVisualHarness -PhudOutput=/tmp/hud [-PhudSkin=/path/to/skin]
 */
public final class LegacyHudVisualHarness extends ApplicationAdapter {
    private final Path skin, output;
    private BitmapFont font;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private OsuSkinAssets assets;
    private GameplayHudRenderer renderer;
    private int index;
    private record Scene(String name, long time, ScoreState score, LegacyHudVisual hud) { }
    private List<Scene> scenes;

    private LegacyHudVisualHarness(Path skin, Path output) { this.skin = skin; this.output = output; }

    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"))
            Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java legacy HUD fixed clock verification");
        config.setWindowedMode(1024, 768);
        config.setForegroundFPS(30);
        new Lwjgl3Application(new LegacyHudVisualHarness(args.length > 1 ? Path.of(args[1]) : null,
                Path.of(args[0])), config);
    }

    @Override public void create() {
        font = new BitmapFont(); batch = new SpriteBatch(); shapes = new ShapeRenderer();
        var game = new OsuJavaGame(null, skin) {
            @Override public BitmapFont font() { return font; }
        };
        assets = new OsuSkinAssets(skin);
        renderer = new GameplayHudRenderer(game, GameplayVisualConfig.defaults(), assets);
        var list = new java.util.ArrayList<Scene>();
        list.add(settled("zero-100-percent", 0, 0, 1));
        for (int combo : new int[]{1, 9, 10, 123}) list.add(settled("combo-" + combo, 12345678, combo, .9912));
        var animation = new LegacyHudAnimation();
        animation.changed(score(10000, 9, 1), 0);
        animation.changed(score(20000, 10, .9912), 1000);
        for (int age : new int[]{0, 25, 160, 210, 260, 500}) {
            long time = 1000 + age;
            list.add(new Scene("increment-" + age + "ms", time, score(20000, 10, .9912), animation.at(time)));
        }
        animation.changed(score(20000, 0, .98), 2000);
        for (int age : new int[]{0, 100, 200, 300}) list.add(new Scene("reset-" + age + "ms", 2000 + age,
                score(20000, 0, .98), animation.at(2000 + age)));
        scenes = List.copyOf(list);
    }

    private static ScoreState score(long value, int combo, double accuracy) {
        return new ScoreState(value, combo, combo, 0, 0, 0, 0, accuracy);
    }
    private static Scene settled(String name, long value, int combo, double accuracy) {
        var score = score(value, combo, accuracy);
        return new Scene(name, 3000, score, LegacyHudVisual.immediate(score));
    }

    @Override public void render() {
        if (index == scenes.size()) { Gdx.app.exit(); return; }
        var scene = scenes.get(index++);
        var projection = new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setProjectionMatrix(projection); shapes.setProjectionMatrix(projection);
        Gdx.gl.glClearColor(.055f, .075f, .1f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        var state = new GameplayState(scene.time, List.of(), List.of(), List.of(), scene.score, false,
                List.of(), List.of(), scene.hud, new LegacySongProgress(0, 1000, 5000));
        shapes.begin(ShapeRenderer.ShapeType.Filled); renderer.drawSongProgress(shapes, state); shapes.end();
        batch.begin(); renderer.draw(batch, state,
                PlayfieldViewport.fit(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()), ""); batch.end();
        try (var ignored = new Capture(Pixmap.createFromFrameBuffer(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight()))) {
            PixmapIO.writePNG(Gdx.files.absolute(output.resolve(scene.name + ".png").toString()), ignored.pixmap, -1, true);
        }
    }
    private record Capture(Pixmap pixmap) implements AutoCloseable {
        @Override public void close() { pixmap.dispose(); }
    }
    @Override public void dispose() { assets.dispose(); font.dispose(); batch.dispose(); shapes.dispose(); }
}
