package dev.osujava.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import dev.osujava.OsuJavaGame;
import dev.osujava.audio.AudioVolumes.Channel;
import dev.osujava.beatmap.*;
import dev.osujava.gameplay.ScoreState;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.ruleset.osu.OsuRuleset;
import dev.osujava.ui.theme.SmoothUiFont;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in captures of the actual four screens, including 2x backbuffers and arc endpoints. */
public final class VolumeHudVisualHarness extends ApplicationAdapter {
    private record Scene(String screen, int width, int height, int density, float volume) { }
    private final List<Scene> scenes = new ArrayList<>();
    private final Path output;
    private OsuJavaGame game;
    private VolumeHudRenderer overlay;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SmoothUiFont smooth;
    private BeatmapSet set;
    private int index;
    private VolumeHudVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java volume HUD capture"); config.setWindowedMode(1280,720); config.setForegroundFPS(30);
        new Lwjgl3Application(new VolumeHudVisualHarness(Path.of(args[0])), config);
    }
    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer(); font = new BitmapFont(); smooth = new SmoothUiFont();
        var library = new BeatmapLibrary(); var ruleset = new OsuRuleset();
        game = new OsuJavaGame(null, null) {
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
            @Override public SmoothUiFont smoothFont() { return smooth; }
            @Override public BeatmapLibrary library() { return library; }
            @Override public OsuRuleset osuRuleset() { return ruleset; }
        };
        overlay = new VolumeHudRenderer(game);
        var difficulty = new BeatmapDifficulty("Volume HUD", "Local fixture", "Harness", "Normal",0,"","",
                DifficultySettings.defaults(),List.of(),List.of(new HitObject(256,192,1000,HitObject.Type.CIRCLE,1,0),
                        new HitObject(400,300,100000,HitObject.Type.CIRCLE,1,0)),null,null);
        set = new BeatmapSet("volume-fixture",difficulty.title(),difficulty.artist(),difficulty.creator(),null,null,List.of(difficulty),List.of());
        library.add(set);
        for (String screen : new String[]{"main-menu","song-select","gameplay","results"}) {
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{2560,1440,1},{1280,720,2}})
                scenes.add(new Scene(screen,size[0],size[1],size[2],.5f));
        }
        for (float volume : new float[]{0,1}) scenes.add(new Scene("song-select",1280,720,1,volume));
        try { Files.createDirectories(output); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private Screen screen(Scene scene) {
        return switch(scene.screen) {
            case "main-menu" -> new MainMenuScreen(game);
            case "song-select" -> new SongSelectScreen(game);
            case "gameplay" -> new GameplayScreen(game,set,set.difficulties().getFirst());
            case "results" -> new ResultsScreen(game,set,set.difficulties().getFirst(),new ScoreState(10000,10,10,10,0,0,0,1));
            default -> throw new IllegalArgumentException(scene.screen);
        };
    }
    @Override public void render() {
        Scene scene = scenes.get(index); Graphics actual = Gdx.graphics;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a) ->
                switch(m.getName()) {
                    case "getWidth" -> scene.width; case "getHeight" -> scene.height;
                    case "getBackBufferWidth" -> scene.width * scene.density;
                    case "getBackBufferHeight" -> scene.height * scene.density;
                    default -> m.invoke(actual,a);
                });
        var framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888,scene.width * scene.density,scene.height * scene.density,false);
        Screen screen = screen(scene);
        try {
            game.navigate(screen);
            if (!(Gdx.input.getInputProcessor() instanceof InputMultiplexer)) throw new AssertionError("Missing global input");
            game.audioVolumes().set(Channel.MASTER,scene.volume);
            game.audioVolumes().set(Channel.MUSIC,.2f); game.audioVolumes().set(Channel.EFFECT,.2f);
            framebuffer.begin();
            // Settle existing entrance transitions, then draw the HUD over the same unchanged framebuffer.
            for (int i = 0; i < 30; i++) screen.render(.016f);
            var projectionBefore = new com.badlogic.gdx.math.Matrix4(batch.getProjectionMatrix());
            Pixmap before = Pixmap.createFromFrameBuffer(0,0,scene.width * scene.density,scene.height * scene.density);
            game.volumeHud().open(); game.volumeHud().advance(.12f);
            overlay.draw(game.volumeHud());
            if (!Arrays.equals(projectionBefore.val,batch.getProjectionMatrix().val)) throw new AssertionError("Overlay changed screen projection");
            Pixmap after = Pixmap.createFromFrameBuffer(0,0,scene.width * scene.density,scene.height * scene.density);
            try {
                // HUD never dims or redraws the left half of the game.
                for (int y = 0; y < after.getHeight(); y += 17) for (int x = 0; x < after.getWidth()/2; x += 17)
                    if (before.getPixel(x,y) != after.getPixel(x,y)) throw new AssertionError("Overlay dimmed the screen");
                String name = scene.screen + "-" + scene.width + "x" + scene.height + "-" + scene.density + "x-" + Math.round(scene.volume*100);
                PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),after,-1,true);
            } finally { before.dispose(); after.dispose(); }
            framebuffer.end();
        } finally { framebuffer.dispose(); Gdx.graphics = actual; }
        if (++index == scenes.size()) {
            System.out.println("Volume HUD harness: " + index + " captures; screen/input/projection/background checks passed: " + output);
            Gdx.app.exit();
        }
    }
    @Override public void dispose() {
        if (game.getScreen() != null) game.getScreen().dispose();
        batch.dispose(); shapes.dispose(); font.dispose(); smooth.close();
    }
}
