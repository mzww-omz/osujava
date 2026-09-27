package dev.osujava.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.ui.theme.*;
import java.nio.file.*;
import java.util.*;

/** Captures the production MainMenuScreen into explicit density-aware framebuffers; never uses Library disk data. */
public final class MainMenuVisualHarness extends ApplicationAdapter {
    private record Scene(int width, int height, int density, boolean background, String state, float time) { }
    private final List<Scene> scenes = new ArrayList<>();
    private final Path output;
    private OsuJavaGame game;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SmoothUiFont smooth;
    private Path artwork;
    private int index;
    private MainMenuVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java Main Menu capture"); config.setWindowedMode(1024,768); config.setForegroundFPS(30);
        new Lwjgl3Application(new MainMenuVisualHarness(Path.of(args[0])), config);
    }
    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer(); font = new BitmapFont(); smooth = new SmoothUiFont();
        var library = new BeatmapLibrary();
        game = new OsuJavaGame(null) {
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
            @Override public SmoothUiFont smoothFont() { return smooth; }
            @Override public BeatmapLibrary library() { return library; }
        };
        try {
            java.nio.file.Files.createDirectories(output);
            // Reproducible cover fixture: wide landscape, bright sky, fine vertical detail.
            var pixmap = new Pixmap(1600,900, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 900; y++) for (int x = 0; x < 1600; x++) {
                float t = y / 900f;
                boolean mountain = y > 510 + 95 * Math.sin(x / 220.0) + 45 * Math.cos(x / 97.0);
                pixmap.setColor(mountain ? .12f : .28f + t * .22f, mountain ? .21f : .42f + t * .18f,
                        mountain ? .26f : .57f + t * .1f, 1);
                pixmap.drawPixel(x,y);
            }
            artwork = output.resolve("artwork-fixture.png");
            PixmapIO.writePNG(Gdx.files.absolute(artwork.toString()),pixmap); pixmap.dispose();
            for (int[] size : new int[][]{{1024,768,1},{1280,720,1},{1920,1080,1},{600,800,1},{1280,720,2},{600,800,2}})
                for (boolean bg : new boolean[]{false,true})
                    for (String state : new String[]{"initial","cookie-enter","play-enter","exit-enter","revealed","play-hover","exit-hover","cookie-hover","cookie-pressed"}) {
                        float time = switch(state) { case "initial" -> 0; case "cookie-enter" -> .07f;
                            case "play-enter" -> .16f; case "exit-enter" -> .26f; default -> 1.25f; };
                        scenes.add(new Scene(size[0],size[1],size[2],bg,state,time));
                    }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private BeatmapSet set(Scene scene) {
        Path bg = scene.background ? artwork : null;
        var diff = new BeatmapDifficulty("A long ambient title for responsive typography", "Local artist", "Harness", "Normal", 0, "", "",
                new DifficultySettings(5,5,5,5,1.4,1), List.of(), List.of(), null, bg);
        return new BeatmapSet("fixture",diff.title(),diff.artist(),"Harness",null,bg,List.of(diff),List.of());
    }
    @Override public void render() {
        Scene scene = scenes.get(index);
        Graphics actual = Gdx.graphics;
        // UiView deliberately uses logical window dimensions; FBO dimensions emulate 1x/2x backbuffers.
        Gdx.graphics = (Graphics) java.lang.reflect.Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},
                (proxy,method,args) -> switch(method.getName()) {
                    case "getWidth" -> scene.width; case "getHeight" -> scene.height;
                    case "getBackBufferWidth" -> scene.width * scene.density;
                    case "getBackBufferHeight" -> scene.height * scene.density;
                    default -> method.invoke(actual,args);
                });
        var fb = new FrameBuffer(Pixmap.Format.RGBA8888,scene.width * scene.density,scene.height * scene.density,false);
        game.library().add(set(scene));
        var screen = new MainMenuScreen(game, () -> "12:34",set(scene), () -> {}, () -> {});
        try {
            MainMenuLayout m = MainMenuLayout.from(UiLayout.fromPixels(scene.width,scene.height));
            float x = -100, y = -100;
            if (scene.state.startsWith("cookie-") && !scene.state.equals("cookie-enter")) { x = m.cx(); y = m.cy(); }
            if (scene.state.equals("play-hover") || scene.state.equals("exit-hover")) {
                int row = scene.state.equals("play-hover") ? 0 : 1;
                x = m.labelX() + 20; y = m.rowY(row) + m.rowHeight() / 2;
            }
            byte[] first = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                fb.begin();
                screen.capture(scene.time,x,y,scene.state.equals("cookie-pressed"));
                Pixmap capture = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
                byte[] pixels = new byte[capture.getPixels().remaining()]; capture.getPixels().duplicate().get(pixels);
                String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + (scene.background ? "artwork-" : "fallback-") + scene.state;
                if (attempt == 0) { first = pixels; PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),capture,-1,true); }
                else if (!Arrays.equals(first,pixels)) throw new AssertionError("Pixels differ: " + name);
                capture.dispose(); fb.end();
            }
            if (scene.state.equals("revealed") && scene.background) checkInteraction(scene);
        } finally { screen.dispose(); fb.dispose(); Gdx.graphics = actual; }
        if (++index == scenes.size()) {
            System.out.println("Main Menu harness: " + index + " captures, all repeated framebuffer bytes identical; 42 Screen interaction checks passed: " + output);
            Gdx.app.exit();
        }
    }
    /** Full Screen input/render path with scripted physical pointer and key input. */
    private void checkInteraction(Scene scene) {
        Input actual = Gdx.input;
        UiLayout ui = UiLayout.fromPixels(scene.width, scene.height);
        MainMenuLayout m = MainMenuLayout.from(ui);
        for (int action = 0; action < 7; action++) {
            int[] calls = new int[2];
            var screen = new MainMenuScreen(game, () -> "12:34", set(scene), () -> calls[0]++, () -> calls[1]++);
            float x = action == 0 ? m.cx() : m.labelX() + 15;
            float y = action == 0 ? m.cy() : m.rowY(action == 2 ? 1 : 0) + m.rowHeight() / 2;
            boolean[] clicked = {false};
            InputProcessor[] processor = {null};
            Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> Math.round(x * ui.scale());
                        case "getY" -> Math.round((ui.height() - y) * ui.scale());
                        case "isButtonPressed", "isButtonJustPressed" -> clicked[0];
                        case "setInputProcessor" -> { processor[0] = (InputProcessor) args[0]; yield null; }
                        default -> method.invoke(actual, args);
                    });
            try {
                screen.show(); screen.render(.4f);
                if (action < 3) { clicked[0] = true; screen.render(0); }
                else processor[0].keyDown(new int[]{Input.Keys.P, Input.Keys.ENTER, Input.Keys.SPACE, Input.Keys.ESCAPE}[action - 3]);
                clicked[0] = false; screen.render(.13f);
                int exit = action == 2 || action == 6 ? 1 : 0;
                if (calls[1] != exit || calls[0] != 1 - exit) throw new AssertionError("Screen interaction failed: " + action + " at " + scene);
            } finally { screen.dispose(); Gdx.input = actual; }
        }
    }
    @Override public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); smooth.close(); }
}
