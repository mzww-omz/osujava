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
                for (String name : List.of("greylooks", "greylooks-initial", "greylooks-set-selected", "greylooks-difficulty-selected",
                        "greylooks-hover", "greylooks-after-wheel", "greylooks-random", "greylooks-random-hover",
                        "greylooks-slow-scroll", "greylooks-fast-scroll", "greylooks-scroll-reverse",
                        "greylooks-expanded-many-first", "greylooks-expanded-many-last", "greylooks-expanded-single",
                        "greylooks-collapse-many", "greylooks-first-item", "greylooks-last-item", "greylooks-large-library", "missing", "row-only", "top-only", "bottom-only",
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
        int[] pointer = {40,scene.height / 2};
        if (scene.name.equals("greylooks-random-hover")) { pointer[0] = Math.round(344 * layout.scale()); pointer[1] = scene.height - Math.round(19 * layout.scale()); }
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor[0] = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor[0];
            case "getX" -> pointer[0]; case "getY" -> pointer[1];
            case "isButtonJustPressed" -> clicked[0];
            default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
        var library = new BeatmapLibrary();
        int setCount = scene.name.equals("greylooks-large-library") ? 1000 : 7;
        for (int i = 0; i < setCount; i++) {
            String title = setCount > 7 ? String.format(Locale.ROOT,"Local song %03d",i) : "Local song " + i;
            List<BeatmapDifficulty> diffs = new ArrayList<>();
            int difficultyCount = scene.name.equals("greylooks-expanded-single") ? 1
                    : (scene.name.contains("many") && i == 3) ? 16 : 4;
            for (int difficulty = 0; difficulty < difficultyCount; difficulty++) diffs.add(new BeatmapDifficulty(
                    title,"Local artist","Harness","Difficulty " + (difficulty + 1),0,"","",DifficultySettings.defaults(),
                    List.of(new TimingPoint(0,500,4,0,0,100,true,0)),List.of(),null,artwork));
            library.add(new BeatmapSet("set" + i,title,"Local artist","Harness",null,artwork,diffs,List.of()));
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
        String preferredSet = scene.name.equals("greylooks-first-item") ? "set0"
                : scene.name.equals("greylooks-last-item") ? "set6"
                : scene.name.startsWith("greylooks-expanded") ? "set2" : "set3";
        int preferredDifficulty = scene.name.equals("greylooks-first-item") ? 0
                : (scene.name.equals("greylooks-last-item") || scene.name.startsWith("greylooks-expanded")) ? 3 : 1;
        var screen = new SongSelectScreen(game,preferredSet,preferredDifficulty,assets);
        var fb = new FrameBuffer(Pixmap.Format.RGBA8888,scene.width * scene.density,scene.height * scene.density,false);
        try {
            screen.show(); screen.resize(scene.width,scene.height);
            fb.begin();
            for (int frame = 0; frame < 40; frame++) screen.render(1f / 60);
            String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + scene.name;
            switch (scene.name) {
                case "greylooks-set-selected" -> {
                    pointerRow(screen,2,-1,pointer,layout,scene.height);
                    clicked[0] = true; screen.render(1f/60); clicked[0] = false;
                    // A second click after expansion must not trigger Play.
                    pointerRow(screen,2,0,pointer,layout,scene.height);
                    clicked[0] = true; screen.render(1f/60); clicked[0] = false;
                    if (pending(screen)) throw new AssertionError("Set double click played: " + name);
                }
                case "greylooks-difficulty-selected" -> {
                    pointerRow(screen,3,2,pointer,layout,scene.height);
                    clicked[0] = true; screen.render(1f/60); clicked[0] = false;
                    if (pending(screen)) throw new AssertionError("Unselected difficulty played: " + name);
                }
                case "greylooks-hover" -> pointerRow(screen,3,2,pointer,layout,scene.height);
                case "greylooks-after-wheel" -> {
                    pointerRow(screen,3,1,pointer,layout,scene.height);
                    var before = carousel(screen).rows();
                    if (!processor[0].scrolled(0,1)) throw new AssertionError("Wheel lost: " + name);
                    if (carousel(screen).rows() != before) throw new AssertionError("Wheel rebuilt selection: " + name);
                }
                case "greylooks-random" -> processor[0].keyDown(Input.Keys.F2);
                case "greylooks-expanded-many-first", "greylooks-expanded-many-last", "greylooks-expanded-single" -> {
                    pointerRow(screen,3,-1,pointer,layout,scene.height);
                    clicked[0] = true; screen.render(0); clicked[0] = false;
                    if (scene.name.endsWith("last")) for (int i = 0; i < 15; i++) processor[0].keyDown(Input.Keys.RIGHT);
                    long children = carousel(screen).rows().stream().filter(r -> r.entry.setIndex() == 3 && r.entry.difficultyIndex() >= 0).count();
                    if (children != (scene.name.endsWith("single") ? 1 : 16)) throw new AssertionError("Expansion input missed: " + name);
                    pointer[0] = 40;
                }
                case "greylooks-collapse-many" -> { processor[0].keyDown(Input.Keys.PAGE_DOWN); pointer[0] = 40; }
                case "greylooks-slow-scroll", "greylooks-fast-scroll", "greylooks-scroll-reverse", "greylooks-large-library" ->
                    pointerRow(screen,3,1,pointer,layout,scene.height);
            }
            if (scene.name.equals("greylooks-hover") || scene.name.equals("greylooks-after-wheel")
                    || scene.name.equals("greylooks-random") || scene.name.endsWith("selected")
                    || scene.name.contains("scroll") || scene.name.contains("expanded")
                    || scene.name.contains("collapse") || scene.name.equals("greylooks-large-library")) {
                for (int frame=1;frame<=60;frame++) {
                    if (scene.name.equals("greylooks-slow-scroll") && frame <= 24) processor[0].scrolled(0,.08f);
                    if ((scene.name.equals("greylooks-fast-scroll") || scene.name.equals("greylooks-large-library")) && frame <= 12)
                        processor[0].scrolled(0,2);
                    if (scene.name.equals("greylooks-scroll-reverse") && frame <= 12) processor[0].scrolled(0,frame <= 6 ? 2 : -2);
                    screen.render(1f/60);
                    assertRenderedBounds(screen,layout);
                    if (frame == 1 || frame == 4 || frame == 10 || frame == 20 || frame == 40 || frame == 60)
                        capture(fb,name + "-frame-" + String.format(Locale.ROOT,"%02d",frame));
                }
            }
            capture(fb,name);
            assertRenderedBounds(screen,layout);
            assertScrollSettled(screen,name);
            if (scene.name.equals("greylooks-large-library")) profileMotion(carousel(screen),name);
            // Wheel scenes intentionally leave selection behind. A real difficulty change restores it.
            if (scene.name.contains("scroll") || scene.name.equals("greylooks-large-library")) {
                processor[0].keyDown(Input.Keys.RIGHT); pointer[0] = 40;
                for (int frame = 0; frame < 90; frame++) screen.render(1f/60);
            }
            // Position the pointer on the current selected row for the existing input smoke checks.
            var model = carousel(screen);
            var selectedRow = model.rows().stream().max(Comparator.comparingDouble(r -> r.selectedAmount)).orElseThrow();
            pointerRow(screen,selectedRow.entry.setIndex(),selectedRow.entry.difficultyIndex(),pointer,layout,scene.height);
            // Navigation and search still work even with malformed/missing visual assets.
            if (!processor[0].scrolled(0,1)) throw new AssertionError("Wheel lost: " + name);
            for (int key : new int[]{Input.Keys.UP,Input.Keys.DOWN,Input.Keys.PAGE_UP,Input.Keys.PAGE_DOWN,Input.Keys.LEFT,Input.Keys.RIGHT,Input.Keys.F2})
                if (!processor[0].keyDown(key)) throw new AssertionError("Key lost: " + name + " / " + key);
            screen.render(.05f);
            pointer[0] = Math.round((layout.width() - 100) * layout.scale());
            pointer[1] = Math.round(45 * layout.scale());
            clicked[0] = true; screen.render(0); clicked[0] = false;
            for (char c : (setCount > 7 ? "Local song 002" : "Local song 2").toCharArray()) if (!processor[0].keyTyped(c)) throw new AssertionError("Search lost: " + name);
            processor[0].keyDown(Input.Keys.ENTER);
            pointer[0] = Math.round(344 * layout.scale()); pointer[1] = scene.height - Math.round(19 * layout.scale());
            clicked[0] = true; screen.render(0); clicked[0] = false;
            // Exactly one matching Set must remain, including after clicking Random.
            try {
                var selected = SongSelectScreen.class.getDeclaredField("selectedSetIndex"); selected.setAccessible(true);
                if (selected.getInt(screen) != 2) throw new AssertionError("Search/Random selection changed: " + name);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            // Selected difficulty re-click also reaches the production Play transition.
            pointer[0] = 40;
            for (int frame=0;frame<60;frame++) screen.render(1f/60);
            model = carousel(screen);
            selectedRow = model.rows().stream().max(Comparator.comparingDouble(r -> r.selectedAmount)).orElseThrow();
            pointerRow(screen,selectedRow.entry.setIndex(),selectedRow.entry.difficultyIndex(),pointer,layout,scene.height);
            clicked[0] = true; screen.render(0); clicked[0] = false;
            if (!pending(screen)) throw new AssertionError("Selected re-click did not play: " + name);
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
    private void capture(FrameBuffer fb, String name) {
        Pixmap capture = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
        PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),capture,-1,true);
        capture.dispose();
    }
    private SongSelectCarousel carousel(SongSelectScreen screen) {
        try {
            var field = SongSelectScreen.class.getDeclaredField("carousel"); field.setAccessible(true);
            return (SongSelectCarousel) field.get(screen);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private boolean pending(SongSelectScreen screen) {
        try {
            var field = SongSelectScreen.class.getDeclaredField("outgoing"); field.setAccessible(true);
            return ((UiNavigation)field.get(screen)).pending();
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void pointerRow(SongSelectScreen screen, int set, int diff, int[] pointer, UiLayout layout, int height) {
        var model = carousel(screen);
        var row = model.rows().stream().filter(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == diff).findFirst().orElseThrow();
        pointer[0] = Math.round((model.renderX(row,layout.width()) + 150) * layout.scale());
        pointer[1] = height - Math.round((model.renderY(row,layout.height() - 62) + model.rowHeight()/2) * layout.scale());
    }
    private void profileMotion(SongSelectCarousel source, String name) {
        var model = new SongSelectCarousel();
        model.content(source.rows().stream().map(r -> r.entry).toList(),620,76,72,source.rows().getFirst().entry.key());
        long total = 0, maximum = 0;
        for (int frame = 0; frame < 720; frame++) {
            model.scrollBy(frame < 360 ? 130 : -130);
            long start = System.nanoTime(); model.advance(1f/60,null); long elapsed = System.nanoTime() - start;
            if (frame >= 120) { total += elapsed; maximum = Math.max(maximum,elapsed); }
        }
        System.out.printf(Locale.ROOT,"Motion profile %s: %d rows, mean %.3f ms, max %.3f ms (600 samples)%n",
                name,model.rows().size(),total / 600.0 / 1_000_000,maximum / 1_000_000.0);
    }
    /** Production draw snapshots must exactly match Carousel output even during overlapping motion. */
    private void assertRenderedBounds(SongSelectScreen screen, UiLayout layout) {
        try {
            var field = SongSelectScreen.class.getDeclaredField("visibleRows"); field.setAccessible(true);
            var model = carousel(screen);
            for (Object snapshot : (List<?>) field.get(screen)) {
                var type = snapshot.getClass();
                int set = (int) value(type,snapshot,"setIndex"), diff = (int) value(type,snapshot,"difficultyIndex");
                var row = model.rows().stream().filter(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == diff).findFirst().orElseThrow();
                float x = (float) value(type,snapshot,"x"), y = (float) value(type,snapshot,"y");
                if (Math.abs(x - model.renderX(row,layout.width())) > .001f
                        || Math.abs(y - model.renderY(row,layout.height() - 62)) > .001f)
                    throw new AssertionError("Draw snapshot diverged from motion bounds");
                if (!Float.isFinite(x) || !Float.isFinite(y) || x < layout.width() * .52f || x > layout.width() * .74f)
                    throw new AssertionError("Invalid row bounds");
            }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private Object value(Class<?> type, Object snapshot, String name) throws ReflectiveOperationException {
        var method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(snapshot);
    }
    private void assertScrollSettled(SongSelectScreen screen, String name) {
        var model = carousel(screen);
        if (Math.abs(model.scrollOffset() - model.scrollTarget()) > 1) throw new AssertionError("Scroll not settled: " + name);
        if (model.scrollOffset() < 0 || model.scrollOffset() > model.maxScroll()) throw new AssertionError("Invalid scroll: " + name);
    }
    @Override public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); smooth.close(); }
}
