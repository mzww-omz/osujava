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
    private Path artwork, portrait, wide;
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
            artwork(artwork, 800, 450);
            portrait = output.resolve("portrait.png"); artwork(portrait, 200, 800);
            wide = output.resolve("wide.png"); artwork(wide, 1600, 160);
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
            Files.createDirectories(output.resolve("fixtures/empty"));
            Path starOnly = Files.createDirectories(output.resolve("fixtures/star-high"));
            Files.writeString(starOnly.resolve("skin.ini"), "[General]\nVersion: 2.2\n");
            starPng(starOnly.resolve("star@2x.png"));
            Path corruptArtwork = output.resolve("broken-artwork.png");
            Files.writeString(corruptArtwork, "not a thumbnail PNG");
            Path starBroken = Files.createDirectories(output.resolve("fixtures/star-broken"));
            Files.writeString(starBroken.resolve("star.png"), "not a star PNG");
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("greylooks", "greylooks-initial", "greylooks-set-selected", "greylooks-difficulty-selected",
                        "greylooks-hover", "greylooks-after-wheel", "greylooks-random", "greylooks-random-hover",
                        "greylooks-slow-scroll", "greylooks-fast-scroll", "greylooks-scroll-reverse",
                        "greylooks-expanded-many-first", "greylooks-expanded-many-last", "greylooks-expanded-single",
                        "greylooks-collapse-many", "greylooks-first-item", "greylooks-last-item", "greylooks-large-library", "missing", "row-only", "top-only", "bottom-only",
                        "normal-only", "high-only", "broken", "tiny", "unusual", "old", "latest", "malformed-colours"))
                    scenes.add(new Scene(size[0],size[1],size[2],name));
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("phase2-idle", "phase2-long-english", "phase2-japanese", "phase2-chinese",
                        "phase2-korean", "phase2-symbols", "phase2-long-mapper", "phase2-long-difficulty",
                        "phase2-no-rating", "phase2-low-rating", "phase2-five-stars", "phase2-fractional",
                        "phase2-high-rating", "phase2-hover", "phase2-missing-thumbnail", "phase2-portrait",
                        "phase2-wide", "phase2-missing-star", "phase2-broken-star", "phase2-high-star",
                        "phase2-v22", "phase2-thumbnail-fade", "phase2-default-fallback", "phase2-broken-thumbnail", "phase2-single", "phase2-many", "phase2-long-set"))
                    scenes.add(new Scene(size[0],size[1],size[2],name));
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("phase25-reference-greylooks", "phase25-reference-modern",
                        "phase25-legacy-a", "phase25-legacy-b", "phase25-collapsed", "phase25-expanded",
                        "phase25-selected", "phase25-sibling", "phase25-hover", "phase25-no-motion",
                        "phase25-long-title", "phase25-unicode", "phase25-fallback", "phase25-rating-none",
                        "phase25-rating-3", "phase25-rating-5", "phase25-rating-7", "phase25-rating-high",
                        "phase25-thumbnail-missing", "phase25-thumbnail-wide", "phase25-thumbnail-tall",
                        "phase25-large-library-modern", "phase25-large-library-modern-rated", "phase25-chrome-full", "phase25-chrome-bottom-cookie", "phase25-chrome-top", "phase25-chrome-rankings"))
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

    private void artwork(Path path, int w, int h) {
        var pixels = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int y = 0; y < h; y++) {
            pixels.setColor(.06f + .12f * y / h, .13f + .12f * y / h, .22f + .18f * y / h, 1);
            pixels.drawLine(0, y, w - 1, y);
        }
        pixels.setColor(.95f, .55f, .35f, 1); pixels.fillCircle(w / 2, h / 3, Math.min(w, h) / 5);
        pixels.setColor(.19f, .40f, .50f, 1); pixels.fillTriangle(0, h, w / 2, h / 2, w, h);
        pixels.setColor(.35f, .60f, .60f, 1); pixels.fillTriangle(w / 3, h, w * 3 / 4, h / 2, w, h);
        PixmapIO.writePNG(Gdx.files.absolute(path.toString()), pixels); pixels.dispose();
    }

    private void starPng(Path path) {
        var pixels = new Pixmap(40, 40, Pixmap.Format.RGBA8888);
        pixels.setColor(1, 1, 1, 1);
        int[] x = new int[10], y = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            int radius = i % 2 == 0 ? 18 : 8;
            x[i] = 20 + (int) Math.round(Math.cos(angle) * radius);
            y[i] = 20 + (int) Math.round(Math.sin(angle) * radius);
        }
        for (int i = 0; i < 10; i++) pixels.fillTriangle(20, 20, x[i], y[i], x[(i + 1) % 10], y[(i + 1) % 10]);
        PixmapIO.writePNG(Gdx.files.absolute(path.toString()), pixels); pixels.dispose();
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
        int setCount = (scene.name.equals("greylooks-large-library") || scene.name.startsWith("phase25-large-library-modern")) ? 1000 : 7;
        boolean phase2 = scene.name.startsWith("phase2");
        var ratings = new IdentityHashMap<BeatmapDifficulty, OptionalDouble>();
        for (int i = 0; i < setCount; i++) {
            String title = setCount > 7 ? String.format(Locale.ROOT,"Local song %03d",i) : "Local song " + i;
            if (scene.name.equals("phase2-long-set") && i == 2)
                title = "Local song 2 — A Very Long English Title with Unicode 星の旅人 that extends beyond the row";
            String mapper = "Harness", version = "Difficulty ";
            Path image = artwork;
            if (phase2 && i == 3) {
                title = switch (scene.name) {
                    case "phase25-long-title", "phase2-long-english" -> "A Very Long Song Title That Keeps Going Beyond the Visible Carousel into a Beautiful Night";
                    case "phase25-unicode", "phase2-japanese" -> "夜明けの星空と夢の続きを描く物語";
                    case "phase2-chinese" -> "夜空中最亮的星与漫长旅程的回忆";
                    case "phase2-korean" -> "별빛 아래 우리들의 아름다운 이야기";
                    case "phase2-symbols" -> "Starlight ✦ ∞ → Café e\u0301 👩‍🚀";
                    default -> title;
                };
                if (scene.name.equals("phase2-long-mapper")) mapper = "A mapper with a remarkably long name 星の旅人 별빛 创作者";
                if (scene.name.equals("phase2-long-difficulty")) version = "The Never Ending Journey Across the Constellations — 星の彼方への冒険 ";
                image = switch (scene.name) {
                    case "phase25-thumbnail-missing", "phase2-missing-thumbnail" -> output.resolve("missing-artwork.png");
                    case "phase2-broken-thumbnail" -> output.resolve("broken-artwork.png");
                    case "phase25-thumbnail-tall", "phase2-portrait" -> portrait;
                    case "phase25-thumbnail-wide", "phase2-wide" -> wide;
                    default -> artwork;
                };
            }
            List<BeatmapDifficulty> diffs = new ArrayList<>();
            int difficultyCount = (scene.name.equals("greylooks-expanded-single") || scene.name.equals("phase2-single")) ? 1
                    : (scene.name.contains("many") && i == 3) ? 16 : 4;
            for (int difficulty = 0; difficulty < difficultyCount; difficulty++) {
                var diff = new BeatmapDifficulty(title,"Local artist",mapper,version + (difficulty + 1),0,"","",DifficultySettings.defaults(),
                        List.of(new TimingPoint(0,500,4,0,0,100,true,0)),List.of(),null,image);
                diffs.add(diff);
                double rating = switch (scene.name) {
                    case "phase2-low-rating" -> .65;
                    case "phase2-five-stars", "phase25-rating-5" -> 5.42;
                    case "phase25-rating-3" -> 3.35;
                    case "phase25-rating-7" -> 7.65;
                    case "phase2-high-rating", "phase25-rating-high" -> 12.84;
                    default -> difficulty == 1 ? 5.42 : new double[]{1.25, 5.42, 6.75, 10.25}[difficulty % 4];
                };
                if (phase2 && !scene.name.equals("phase2-no-rating") && !scene.name.equals("phase25-rating-none") && !scene.name.equals("phase25-large-library-modern")) ratings.put(diff, OptionalDouble.of(rating));
            }
            library.add(new BeatmapSet("set" + i,title,"Local artist",mapper,null,image,diffs,List.of()));
        }
        var game = new OsuJavaGame(null,null) {
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
            @Override public SmoothUiFont smoothFont() { return smooth; }
            @Override public BeatmapLibrary library() { return library; }
            @Override public dev.osujava.ruleset.osu.OsuRuleset osuRuleset() { return new dev.osujava.ruleset.osu.OsuRuleset(); }
        };
        var resolver = switch (scene.name) {
            case "phase2-missing-star" -> new SkinAssetResolver(output.resolve("fixtures/missing"));
            case "phase2-broken-star" -> new SkinAssetResolver(output.resolve("fixtures/star-broken"));
            case "phase2-high-star" -> new SkinAssetResolver(output.resolve("fixtures/star-high"));
            case "phase2-default-fallback" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), null);
            case "phase25-large-library-modern", "phase25-large-library-modern-rated", "phase25-reference-modern", "phase25-rating-3", "phase25-rating-5", "phase25-rating-7",
                    "phase25-rating-high", "phase25-rating-none", "phase25-thumbnail-missing", "phase25-thumbnail-wide",
                    "phase25-thumbnail-tall", "phase25-chrome-full", "phase25-hover", "phase2-thumbnail-fade", "phase2-missing-thumbnail",
                    "phase2-portrait", "phase2-wide", "phase2-broken-thumbnail", "phase2-v22" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/star-high"), null);
            default -> scene.name.startsWith("greylooks") || phase2 ? SkinAssetResolver.withBundledDefault(null,null)
                    : new SkinAssetResolver(output.resolve("fixtures").resolve(scene.name));
        };
        var assets = new SongSelectSkinAssets(resolver);
        String preferredSet = scene.name.equals("greylooks-first-item") ? "set0"
                : scene.name.equals("greylooks-last-item") ? "set6"
                : scene.name.startsWith("greylooks-expanded") ? "set2" : "set3";
        int preferredDifficulty = scene.name.equals("greylooks-first-item") ? 0
                : (scene.name.equals("greylooks-last-item") || scene.name.startsWith("greylooks-expanded")) ? 3 : 1;
        var screen = new SongSelectScreen(game,preferredSet,preferredDifficulty,assets, diff -> ratings.getOrDefault(diff, OptionalDouble.empty()));
        var fb = new FrameBuffer(Pixmap.Format.RGBA8888,scene.width * scene.density,scene.height * scene.density,false);
        try {
            screen.show();
            screen.legacyThumbnailPreview(scene.name.equals("phase25-legacy-b"));
            screen.resize(scene.width,scene.height);
            fb.begin();
            for (int frame = 0; frame < 40; frame++) screen.render(1f / 60);
            if ((scene.name.equals("phase2-hover") || scene.name.equals("phase25-hover"))) {
                pointerRow(screen,carousel(screen).rows().stream().filter(r -> r.entry.difficultyIndex() == 1).findFirst().orElseThrow().entry.setIndex(),2,pointer,layout,scene.height);
                for (int frame = 0; frame < 60; frame++) screen.render(1f / 60);
            }
            String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + scene.name;
            if (scene.name.equals("phase2-thumbnail-fade")) {
                // Simulate a newly resident image after entrance, without changing content/hitboxes.
                try {
                    var field = SongSelectScreen.class.getDeclaredField("thumbnails"); field.setAccessible(true);
                    ((BeatmapThumbnails)field.get(screen)).close();
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                screen.render(0);
                capture(fb, name + "-frame-00");
                for (int frame = 1; frame <= 8; frame++) {
                    screen.render(1f / 60);
                    assertRenderedBounds(screen, layout);
                    if (frame == 2 || frame == 4 || frame == 8) capture(fb, name + "-frame-" + String.format(Locale.ROOT,"%02d",frame));
                }
            }
            if (phase2) assertPresentation(screen, assets, scene.name);
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
            if (setCount > 7) {
                profileMotion(carousel(screen),name);
                profileRender(screen, name);
            }
            // Wheel scenes intentionally leave selection behind. A real difficulty change restores it.
            if (scene.name.contains("scroll") || setCount > 7) {
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
                var setsField = SongSelectScreen.class.getDeclaredField("sets"); setsField.setAccessible(true);
                var selectedSet = (BeatmapSet)((List<?>)setsField.get(screen)).get(selected.getInt(screen));
                if (!selectedSet.id().equals("set2")) throw new AssertionError("Search/Random selection changed: " + name);
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
            if (assets.starTexture() != null) throw new AssertionError("Star texture retained after disposal");
            fb.dispose(); Gdx.graphics = actualGraphics; Gdx.input = actualInput;
        }
        if (++index == scenes.size()) {
            System.out.println("SongSelect harness: " + index + " captures + navigation/disposal checks passed: " + output);
            Gdx.app.exit();
        }
    }
    private void assertPresentation(SongSelectScreen screen, SongSelectSkinAssets assets, String scene) {
        try {
            var field = SongSelectScreen.class.getDeclaredField("rowContent"); field.setAccessible(true);
            for (Object item : ((Map<?, ?>)field.get(screen)).values()) {
                var content = (SongSelectRowPresentation.Content)item;
                if (scene.equals("phase2-no-rating") && content.stars().present()) throw new AssertionError("Invented rating");
                if (content.stars().count() > 9) throw new AssertionError("Unbounded stars");
            }
            if (assets.starTexture() == null) throw new AssertionError("Missing procedural star fallback");
            if (scene.equals("phase2-v22")) {
                var colour = SongSelectScreen.class.getDeclaredField("activeText"); colour.setAccessible(true);
                if (!((Color)colour.get(screen)).equals(UiTheme.TEXT)) throw new AssertionError("Dark unspecified text on skin-backed row");
            }
            if (scene.equals("phase2-default-fallback") && assets.get(SongSelectSkinAssets.Image.STAR).file().classpathResource() == null)
                throw new AssertionError("Default star fallback did not resolve bundled asset");
            if (scene.equals("phase2-missing-star") || scene.equals("phase2-broken-star")) {
                if (assets.get(SongSelectSkinAssets.Image.STAR) != null) throw new AssertionError("Unexpected skin star");
            }
            if (scene.equals("phase2-high-star") && assets.get(SongSelectSkinAssets.Image.STAR).density() != 2)
                throw new AssertionError("@2x star lost");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
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
    private void profileRender(SongSelectScreen screen, String name) {
        long total = 0, maximum = 0;
        for (int frame = 0; frame < 240; frame++) {
            long start = System.nanoTime(); screen.render(1f / 60); long elapsed = System.nanoTime() - start;
            if (frame >= 60) { total += elapsed; maximum = Math.max(maximum, elapsed); }
        }
        System.out.printf(Locale.ROOT, "Idle render CPU submission %s: mean %.3f ms, max %.3f ms (180 samples, shared artwork)%n",
                name, total / 180.0 / 1_000_000, maximum / 1_000_000.0);
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
                if (!Float.isFinite(x) || !Float.isFinite(y) || x < layout.width() * .50f || x > layout.width() * .70f)
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
