package dev.osujava.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.skin.*;
import dev.osujava.ui.theme.*;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

/** Production SongSelect captures using real PNG decoding and explicit 1x/2x framebuffers. */
public final class SongSelectVisualHarness extends ApplicationAdapter {
    private record Scene(int width, int height, int density, String name) { }
    private final List<Scene> scenes = new ArrayList<>();
    private final Path output;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SmoothUiFont smooth;
    private Path artwork;
    private int index;

    private SongSelectVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java SongSelect capture"); config.setWindowedMode(1280,720); config.setForegroundFPS(30);
        new Lwjgl3Application(new SongSelectVisualHarness(Path.of(args[0])), config);
    }

    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer(); font = new BitmapFont(); smooth = new SmoothUiFont();
        try {
            Files.createDirectories(output);
            artwork = output.resolve("artwork.png");
            png(artwork, 800, 200); // Wide image exercises thumbnail cover cropping.
            for (String name : List.of("missing", "row-only", "top-only", "bottom-only", "normal-only", "high-only",
                    "broken", "tiny", "unusual", "old", "latest", "malformed-colours")) {
                Path dir = Files.createDirectories(output.resolve("fixtures").resolve(name));
                Files.writeString(dir.resolve("skin.ini"), "[General]\nVersion: " + (name.equals("old") ? "1.0" : "latest")
                        + "\n[Colours]\nSongSelectActiveText: " + (name.equals("malformed-colours") ? "bad,256,-1" : "20,20,20")
                        + "\nSongSelectInactiveText: 255,255,255\n");
                if (name.equals("missing")) continue;
                if (name.equals("broken")) {
                    for (var image : SongSelectSkinAssets.Image.values()) Files.writeString(dir.resolve(image.basename + ".png"), "broken PNG");
                    continue;
                }
                for (var image : SongSelectSkinAssets.Image.values()) {
                    if (name.equals("row-only") && image != SongSelectSkinAssets.Image.MENU_BUTTON_BACKGROUND
                            || name.equals("top-only") && image != SongSelectSkinAssets.Image.TOP
                            || name.equals("bottom-only") && image != SongSelectSkinAssets.Image.BOTTOM) continue;
                    String suffix = name.equals("high-only") ? "@2x" : "";
                    int w = name.equals("tiny") ? 1 : name.equals("unusual") ? 2048 : 256;
                    int h = name.equals("tiny") || name.equals("unusual") ? 1 : 64;
                    png(dir.resolve(image.basename + suffix + ".png"), w, h);
                }
            }
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("greylooks", "greylooks-hover", "missing", "row-only", "top-only", "bottom-only",
                        "normal-only", "high-only", "broken", "tiny", "unusual", "old", "latest", "malformed-colours"))
                    scenes.add(new Scene(size[0],size[1],size[2],name));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void png(Path path, int w, int h) {
        var pixmap = new Pixmap(w,h,Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            pixmap.setColor(.6f + .4f * x / w,.7f,.85f,1); pixmap.drawLine(x,0,x,h-1);
        }
        PixmapIO.writePNG(Gdx.files.absolute(path.toString()),pixmap); pixmap.dispose();
    }

    @Override public void render() {
        Scene scene = scenes.get(index);
        Graphics actualGraphics = Gdx.graphics; Input actualInput = Gdx.input;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a) -> switch(m.getName()) {
            case "getWidth" -> scene.width; case "getHeight" -> scene.height;
            case "getBackBufferWidth" -> scene.width * scene.density; case "getBackBufferHeight" -> scene.height * scene.density;
            default -> m.invoke(actualGraphics,a);
        });
        InputProcessor[] processor = {null};
        boolean[] clicked = {false};
        UiLayout layout = UiLayout.fromPixels(scene.width,scene.height);
        int[] pointer = {scene.width * 4 / 5,scene.height / 2};
        if (scene.name.endsWith("hover")) { pointer[0] = Math.round(344 * layout.scale()); pointer[1] = scene.height - Math.round(19 * layout.scale()); }
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor[0] = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor[0];
            case "getX" -> pointer[0]; case "getY" -> pointer[1];
            case "isButtonJustPressed" -> clicked[0];
            default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
        var library = new BeatmapLibrary();
        for (int i = 0; i < 7; i++) {
            List<BeatmapDifficulty> diffs = new ArrayList<>();
            for (String version : List.of("Easy","Normal","Hard","Expert")) diffs.add(new BeatmapDifficulty(
                    "Local song " + i,"Local artist","Harness",version,0,"","",DifficultySettings.defaults(),
                    List.of(new TimingPoint(0,500,4,0,0,100,true,0)),List.of(),null,artwork));
            library.add(new BeatmapSet("set" + i,"Local song " + i,"Local artist","Harness",null,artwork,diffs,List.of()));
        }
        var game = new OsuJavaGame(null,null) {
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
            @Override public SmoothUiFont smoothFont() { return smooth; }
            @Override public BeatmapLibrary library() { return library; }
            @Override public dev.osujava.ruleset.osu.OsuRuleset osuRuleset() { return new dev.osujava.ruleset.osu.OsuRuleset(); }
        };
        var resolver = scene.name.startsWith("greylooks") ? SkinAssetResolver.withBundledDefault(null,null)
                : new SkinAssetResolver(output.resolve("fixtures").resolve(scene.name));
        var assets = new SongSelectSkinAssets(resolver);
        var screen = new SongSelectScreen(game,"set3",1,assets);
        var fb = new FrameBuffer(Pixmap.Format.RGBA8888,scene.width * scene.density,scene.height * scene.density,false);
        try {
            screen.show(); screen.resize(scene.width,scene.height);
            fb.begin();
            for (int frame = 0; frame < 40; frame++) screen.render(1f / 60);
            Pixmap capture = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
            String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + scene.name;
            PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),capture,-1,true);
            capture.dispose();
            // Navigation and search still work even with malformed/missing visual assets.
            if (!scene.name.endsWith("hover") && !processor[0].scrolled(0,1)) throw new AssertionError("Wheel lost: " + name);
            for (int key : new int[]{Input.Keys.UP,Input.Keys.DOWN,Input.Keys.PAGE_UP,Input.Keys.PAGE_DOWN,Input.Keys.LEFT,Input.Keys.RIGHT,Input.Keys.F2})
                if (!processor[0].keyDown(key)) throw new AssertionError("Key lost: " + name + " / " + key);
            screen.render(.05f);
            pointer[0] = Math.round((layout.width() - 100) * layout.scale());
            pointer[1] = Math.round(45 * layout.scale());
            clicked[0] = true; screen.render(0); clicked[0] = false;
            for (char c : "Local song 2".toCharArray()) if (!processor[0].keyTyped(c)) throw new AssertionError("Search lost: " + name);
            processor[0].keyDown(Input.Keys.ENTER);
            pointer[0] = Math.round(344 * layout.scale()); pointer[1] = scene.height - Math.round(19 * layout.scale());
            clicked[0] = true; screen.render(0); clicked[0] = false;
            // Exactly one matching Set must remain, including after clicking Random.
            try {
                var selected = SongSelectScreen.class.getDeclaredField("selectedSetIndex"); selected.setAccessible(true);
                if (selected.getInt(screen) != 2) throw new AssertionError("Search/Random selection changed: " + name);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            fb.end();
        } finally {
            screen.dispose(); screen.dispose();
            for (var image : SongSelectSkinAssets.Image.values()) if (assets.get(image) != null) throw new AssertionError("Texture retained");
            fb.dispose(); Gdx.graphics = actualGraphics; Gdx.input = actualInput;
        }
        if (++index == scenes.size()) {
            System.out.println("SongSelect harness: " + index + " captures + navigation/disposal checks passed: " + output);
            Gdx.app.exit();
        }
    }
    @Override public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); smooth.close(); }
}
