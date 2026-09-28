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
    private Path artwork, portrait, wide, customSkin;
    private final Map<Integer,Path> compatibilitySkins = new LinkedHashMap<>();
    private int captures, transitionFrames;
    private int index;

    private SongSelectVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java SongSelect capture"); config.setWindowedMode(1280,720); config.setForegroundFPS(30);
        // Captures use explicit FBOs; avoid primary-monitor centering in display-less macOS sessions.
        config.setWindowPosition(0,0); config.setInitialVisible(false);
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
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("phase3-sort-title", "phase3-sort-artist", "phase3-sort-bpm", "phase3-sort-length",
                        "phase3-group-none", "phase3-group-artist", "phase3-group-creator", "phase3-group-bpm", "phase3-group-length",
                        "phase3-search-inactive", "phase3-search-active", "phase3-search-short", "phase3-search-long", "phase3-search-unicode", "phase3-search-none",
                        "phase3-group-expanded", "phase3-group-selected", "phase3-group-first", "phase3-group-last",
                        "phase3-chrome-full", "phase3-chrome-cookie", "phase3-menu-group", "phase3-menu-sort", "phase3-large-library", "phase3-transitions", "phase3-fallback", "phase3-modern"))
                    scenes.add(new Scene(size[0],size[1],size[2],name));
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("empty", "single", "three", "many", "SS", "S", "A", "B", "C", "D",
                        "numbers", "numbers-bottom", "sibling", "no-score", "group", "search", "long", "unicode", "fallback", "bundled-fallback",
                        "high-only", "scroll-top", "scroll-middle", "scroll-bottom", "selected", "wheel-hover", "transitions",
                        "large-0", "large-10", "large-100", "large-1000"))
                    scenes.add(new Scene(size[0],size[1],size[2],"phase4-" + name));
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("current", "current-hover", "missing", "fallback", "bundled", "normal", "transparent", "present", "tall", "giant", "giant-high"))
                    scenes.add(new Scene(size[0], size[1], size[2], "phasechrome-" + name));
            Path present = Files.createDirectories(output.resolve("fixtures/present"));
            for (String name : List.of("songselect-top", "songselect-bottom")) {
                var pixels = new Pixmap(1366, name.endsWith("top") ? 149 : 90, Pixmap.Format.RGBA8888);
                pixels.setColor(name.endsWith("top") ? Color.MAGENTA : Color.CYAN); pixels.fill();
                PixmapIO.writePNG(Gdx.files.absolute(present.resolve(name + ".png").toString()), pixels);
                pixels.dispose();
            }
            Path tall = Files.createDirectories(output.resolve("fixtures/tall"));
            png(tall.resolve("songselect-top.png"),1366,240);
            png(tall.resolve("songselect-bottom.png"),1366,160);
            for (int density : new int[]{1, 2}) {
                Path giant = Files.createDirectories(output.resolve("fixtures/" + (density == 1 ? "giant" : "giant-high")));
                for (String part : List.of("top", "bottom")) {
                    var pixels = new Pixmap(1366 * density, 1400 * density, Pixmap.Format.RGBA8888);
                    pixels.setColor(part.equals("top") ? Color.MAGENTA : Color.CYAN); pixels.fill();
                    PixmapIO.writePNG(Gdx.files.absolute(giant.resolve("songselect-" + part
                            + (density == 2 ? "@2x" : "") + ".png").toString()), pixels);
                    pixels.dispose();
                }
            }
            Path transparent = Files.createDirectories(output.resolve("fixtures/transparent"));
            for (String name : List.of("songselect-top", "songselect-bottom")) {
                var pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                PixmapIO.writePNG(Gdx.files.absolute(transparent.resolve(name + ".png").toString()), pixels);
                pixels.dispose();
            }
            String custom = System.getProperty("osujava.songSelectCustomSkin", "");
            if (!custom.isBlank()) {
                Path source = Path.of(custom);
                customSkin = custom.toLowerCase(Locale.ROOT).endsWith(".osk")
                        ? new SkinImporter(output.resolve("imported-skins")).importFile(source) : source;
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                    scenes.add(new Scene(size[0],size[1],size[2],"phasechrome-custom"));
            }
            createToolboxFixtures();
            String corpus = System.getProperty("osujava.songSelectCorpusManifest", "");
            if (!corpus.isBlank()) loadCompatibilityCorpus(Path.of(corpus));
            String compatibility = System.getProperty("osujava.songSelectCompatibilitySkins", "");
            for (String source : compatibility.split("\\|")) if (!source.isBlank()) {
                Path path = Path.of(source);
                compatibilitySkins.put(compatibilitySkins.size(), source.toLowerCase(Locale.ROOT).endsWith(".osk")
                        ? new SkinImporter(output.resolve("imported-skins")).importFile(path) : path);
            }
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}}) {
                for (String fixture : List.of("all", "normal", "high", "missing", "malformed", "composite", "fallback", "bundled",
                        "transparent", "asymmetric", "asymmetric-high", "oversized-hover", "mismatched-high", "tiny-chrome", "normal-bundled"))
                    scenes.add(new Scene(size[0],size[1],size[2],"phase5a-assets-" + fixture));
                for (String state : toolboxStates()) {
                    scenes.add(new Scene(size[0],size[1],size[2],"phase5a-current-" + state));
                    for (int profile : compatibilitySkins.keySet())
                        scenes.add(new Scene(size[0],size[1],size[2],"phase5a-profile-" + profile + "-" + state));
                }
                for (String state : List.of("unplayed", "played", "sibling", "selected-played", "selected-unplayed", "save-reload", "aborted", "transitions",
                        "large-idle", "large-hover", "large-mods", "large-scroll"))
                    scenes.add(new Scene(size[0],size[1],size[2],"phase5a-state-" + state));
            }
            String phase = System.getProperty("osujava.songSelectPhase", "all");
            if (phase.equals("redevelopment")) {
                // Fast cross-feature suite; all original phase suites remain available unchanged.
                var regression = Set.of("greylooks-initial", "greylooks-hover", "greylooks-fast-scroll", "greylooks-scroll-reverse",
                        "greylooks-expanded-many-first", "greylooks-expanded-many-last", "greylooks-collapse-many",
                        "greylooks-first-item", "greylooks-last-item", "greylooks-large-library",
                        "missing", "high-only", "broken", "tiny", "unusual",
                        "phase2-long-english", "phase2-japanese", "phase2-symbols", "phase2-thumbnail-fade",
                        "phase3-search-none", "phase3-search-unicode", "phase3-group-artist", "phase3-transitions",
                        "phase4-selected", "phase4-wheel-hover", "phase4-transitions",
                        "phase5a-assets-composite", "phase5a-assets-transparent", "phase5a-assets-oversized-hover",
                        "phase5a-assets-asymmetric-high", "phase5a-current-mode-pressed", "phase5a-current-mods-pressed",
                        "phase5a-current-random-pressed", "phase5a-current-back-hover", "phase5a-state-save-reload",
                        "phasechrome-transparent", "phasechrome-tall");
                scenes.removeIf(scene -> !regression.contains(scene.name));
            } else if (!phase.equals("all")) scenes.removeIf(scene -> !scene.name.startsWith("phase" + phase + "-"));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private List<String> toolboxStates() {
        return List.of("idle", "mode-hover", "mods-hover", "random-hover", "options-hover",
                "mode-pressed", "mods-pressed", "random-pressed", "options-pressed", "back-hover", "import-hover", "cookie-hover",
                "back-pressed", "import-pressed", "cookie-pressed", "disabled", "mode-view", "mods-view", "active-none");
    }

    private void loadCompatibilityCorpus(Path manifest) throws Exception {
        var corpus = new com.badlogic.gdx.utils.JsonReader().parse(Files.readString(manifest));
        for (var skin : corpus.get("skins")) {
            if (!skin.getBoolean("regression")) continue;
            Path source = Path.of(skin.getString("source")).toAbsolutePath();
            if (!Files.isDirectory(source)) throw new AssertionError("Pinned corpus directory unavailable: " + source);
            for (var asset : skin.get("assets")) {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(source.resolve(asset.getString("filename"))));
                if (!java.util.HexFormat.of().formatHex(digest).equals(asset.getString("sha256")))
                    throw new AssertionError("Pinned skin asset changed: " + source + "/" + asset.getString("filename"));
            }
            System.out.println("CORPUS PASS " + skin.getString("id") + " " + source);
            // Greylooks is also captured explicitly as the current profile and as bundled/fallback.
            if (!skin.getString("id").equals("Greylooks")) compatibilitySkins.put(compatibilitySkins.size(),source);
        }
    }

    private void createToolboxFixtures() throws Exception {
        for (String name : List.of("all", "normal", "high", "missing", "malformed", "composite",
                "transparent", "asymmetric", "asymmetric-high", "oversized-hover", "mismatched-high", "tiny-chrome", "normal-bundled")) {
            Path dir = Files.createDirectories(output.resolve("fixtures/toolbox-" + name));
            Files.writeString(dir.resolve("skin.ini"),"[General]\nVersion: 2.5\n");
            if (name.equals("missing")) continue;
            for (var action : SongSelectSkinAssets.Selection.values()) for (var image : List.of(action.normal,action.hover)) {
                if (name.startsWith("normal") && image == action.hover) continue;
                int density = name.endsWith("high") ? 2 : 1;
                Path file = dir.resolve(image.basename + (density == 2 ? "@2x" : "") + ".png");
                if (name.equals("malformed")) { Files.writeString(file,"not a PNG"); continue; }
                int width = (int)action.logicalWidth*density, height = 90*density;
                if (name.equals("composite") && image == SongSelectSkinAssets.Image.MODE) { width=1150; height=540; }
                if (name.equals("transparent")) { width=density; height=density; }
                if (name.startsWith("asymmetric")) { width=400*density; height=150*density; }
                if (name.equals("oversized-hover") && image == action.hover) { width=1200; height=700; }
                var p = new Pixmap(width,height,Pixmap.Format.RGBA8888);
                p.setColor(image == action.hover ? new Color(.95f,.65f,.45f,1) : new Color(.4f,.55f,.75f,1));
                if (name.startsWith("asymmetric")) {
                    p.fillRectangle(20*density,80*density,40*density,50*density);
                    p.fillRectangle(300*density,5*density,60*density,15*density);
                } else if (!name.equals("transparent")) {
                    p.fillRectangle(3*density,height-80*density,(int)action.logicalWidth*density-6*density,73*density);
                    if (width > 500) { p.setColor(.5f,.3f,.5f,.8f); p.fillRectangle(0,height-140,width,30); }
                }
                PixmapIO.writePNG(Gdx.files.absolute(file.toString()),p); p.dispose();
                if (name.equals("mismatched-high")) {
                    var high = new Pixmap(150,180,Pixmap.Format.RGBA8888);
                    high.setColor(Color.WHITE); high.fillRectangle(6,20,130,150);
                    PixmapIO.writePNG(Gdx.files.absolute(dir.resolve(image.basename+"@2x.png").toString()),high);
                    high.dispose();
                }
            }
            if (name.startsWith("asymmetric") || name.equals("transparent")) {
                int d = name.endsWith("high") ? 2 : 1;
                var p = new Pixmap(400*d,150*d,Pixmap.Format.RGBA8888);
                if (!name.equals("transparent")) {
                    p.setColor(Color.WHITE); p.fillRectangle(20*d,80*d,100*d,50*d);
                    p.fillRectangle(300*d,5*d,60*d,15*d);
                }
                PixmapIO.writePNG(Gdx.files.absolute(dir.resolve("menu-back"+(d==2 ? "@2x" : "")+".png").toString()),p);
                p.dispose();
            }
            if (name.equals("tiny-chrome")) for (var image : List.of(SongSelectSkinAssets.Image.TOP,SongSelectSkinAssets.Image.BOTTOM)) {
                var tiny = new Pixmap(1,1,Pixmap.Format.RGBA8888);
                PixmapIO.writePNG(Gdx.files.absolute(dir.resolve(image.basename+".png").toString()),tiny); tiny.dispose();
            }
        }
    }

    private void assertChrome(SongSelectScreen screen, SongSelectSkinAssets assets, String name, Path greylooks) {
        var layout = UiLayout.fromPixels(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        var content = SongSelectChrome.content(layout.width(),layout.height(),assets);
        var scores = screen.scoreBounds(layout);
        if (name.equals("phasechrome-custom")) {
            var mode = assets.get(SongSelectSkinAssets.Image.MODE);
            System.out.println("CUSTOM directory=" + customSkin
                    + (mode == null ? "" : " mode=" + mode.file().path() + " logical=" + mode.logicalWidth() + "x" + mode.logicalHeight()));
            var current = new SkinAssetResolver(customSkin);
            for (var image : List.of(SongSelectSkinAssets.Image.BACK, SongSelectSkinAssets.Image.RANDOM,
                    SongSelectSkinAssets.Image.RANDOM_OVER)) {
                var local = image == SongSelectSkinAssets.Image.BACK
                        ? current.resolveAnimation(image.basename).stream().findFirst() : current.resolve(image.basename);
                var asset = assets.get(image);
                if (local.isPresent() && (asset == null || asset.file().fallback() || !asset.file().path().equals(local.get().path())))
                    throw new AssertionError("Current action replaced by chrome fallback: " + image);
                if (asset != null) System.out.println("ACTION PASS " + image + " provider="
                        + (asset.file().classpathResource() != null ? "bundled" : asset.file().fallback() ? "fallback" : "current")
                        + " path=" + asset.file().path() + " density=" + asset.density());
            }
        }
        if (scores.top() + 64 != content.rankingHeaderTop() || scores.bottom() < content.bottom())
            throw new AssertionError("Content bounds are disconnected from chrome");
        if (assets.get(SongSelectSkinAssets.Image.TOP) != null) {
            float scale = layout.height()/768;
            float leftDepth = assets.topDepth(0,layout.width()*.52f/scale)*scale;
            float rightDepth = assets.topDepth(layout.width()*.55f/scale,layout.width()/scale)*scale;
            if (content.rankingHeaderTop() > layout.height()-Math.min(leftDepth, layout.height() * SongSelectChrome.MAX_TOP_FRACTION)
                    || content.carouselTop() > layout.height()-Math.min(rightDepth, layout.height() * SongSelectChrome.MAX_TOP_FRACTION))
                throw new AssertionError("Chrome overlaps content");
        }
        for (var image : List.of(SongSelectSkinAssets.Image.TOP, SongSelectSkinAssets.Image.BOTTOM)) {
            var asset = assets.get(image);
            boolean missing = name.equals("phasechrome-missing") || name.equals("phasechrome-custom")
                    && new SkinAssetResolver(customSkin).resolve(image.basename).isEmpty();
            if (screen.renderedChromeProcedural(image) != missing || (asset == null) != missing)
                throw new AssertionError("Chrome rendered the wrong branch: " + name + " " + image);
            if (name.equals("phasechrome-custom")) {
                var local = new SkinAssetResolver(customSkin).resolve(image.basename);
                if (local.isPresent() && (!asset.file().path().equals(local.get().path()) || asset.file().fallback()))
                    throw new AssertionError("Custom native chrome did not win: " + asset.file());
                if (local.isEmpty() && asset != null) throw new AssertionError("Foreign custom chrome");
            }
            if (name.startsWith("phasechrome-current") && (asset.file().fallback() || asset.density() != 2
                    || !asset.file().path().equals(greylooks.resolve(image.basename + "@2x.png"))))
                throw new AssertionError("Current Greylooks did not win: " + asset);
            if (name.equals("phasechrome-fallback") && (!asset.file().fallback() || asset.file().classpathResource() != null))
                throw new AssertionError("Configured fallback did not win");
            if (name.equals("phasechrome-bundled") && asset.file().classpathResource() == null)
                throw new AssertionError("Bundled fallback did not win");
            if ((name.equals("phasechrome-normal") || name.equals("phasechrome-transparent"))
                    && (asset.file().fallback() || asset.density() != 1))
                throw new AssertionError("Current 1x must beat fallback 2x");
            System.out.println("CHROME PASS " + name + " " + image + " procedural=" + missing
                    + (asset == null ? "" : " path=" + asset.file().path() + " density=" + asset.density()));
        }
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
        boolean[] pressed = {false};
        UiLayout layout = UiLayout.fromPixels(scene.width,scene.height);
        int[] pointer = {40,scene.height / 2};
        if (scene.name.equals("greylooks-random-hover") || scene.name.equals("phasechrome-current-hover")) { pointer[0] = Math.round(344 * layout.scale()); pointer[1] = scene.height - Math.round(19 * layout.scale()); }
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor[0] = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor[0];
            case "getX" -> pointer[0]; case "getY" -> pointer[1];
            case "isButtonJustPressed" -> clicked[0];
            case "isButtonPressed" -> pressed[0];
            default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
        var library = new BeatmapLibrary();
        int setCount = (scene.name.equals("greylooks-large-library") || scene.name.startsWith("phase25-large-library-modern") || scene.name.equals("phase3-large-library") || scene.name.startsWith("phase4-large") || scene.name.startsWith("phase5a-state-large")) ? 1000 : 7;
        boolean phase2 = scene.name.startsWith("phase2");
        var ratings = new IdentityHashMap<BeatmapDifficulty, OptionalDouble>();
        for (int i = 0; i < setCount; i++) {
            String title = setCount > 7 ? String.format(Locale.ROOT,"Local song %03d",i) : "Local song " + i;
            if (scene.name.equals("phase2-long-set") && i == 2)
                title = "Local song 2 — A Very Long English Title with Unicode 星の旅人 that extends beyond the row";
            String mapper = "Harness", version = "Difficulty ";
            String artist = "Local artist";
            boolean browserScene = scene.name.startsWith("phase3") || scene.name.startsWith("phase4");
            if (scene.name.equals("phase4-long") && i == 3) title = "A Very Long Song Title Beyond the Carousel — 夜空の星と夢の続き";
            if (scene.name.equals("phase4-unicode") && i == 3) title = "夜空の星と夢の続き 별빛 创作者";
            if (browserScene) {
                artist = new String[]{"Camellia", "Aether", "夜の星"}[i % 3];
                mapper = new String[]{"Harness", "Mapper", "星の旅人"}[i % 3];
                if (i == 3 && scene.name.equals("phase3-search-unicode")) title += " 夜空";
            }
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
                var diff = new BeatmapDifficulty(title,artist,mapper,version + (difficulty + 1),0,"","",DifficultySettings.defaults(),
                        List.of(new TimingPoint(0,browserScene ? 60000.0 / (80 + (i % 6) * 55) : 500,4,0,0,100,true,0)),
                        browserScene ? List.of(new dev.osujava.beatmap.HitObject(0,0,1000,dev.osujava.beatmap.HitObject.Type.CIRCLE,1,0),
                                new dev.osujava.beatmap.HitObject(0,0,1000 + (i % 7 + 1) * 90000,dev.osujava.beatmap.HitObject.Type.CIRCLE,1,0)) : List.of(),null,image);
                if (scene.name.startsWith("phase4") || scene.name.startsWith("phase5a")) diff = diff.withAssets(null, image, output.resolve("maps/set" + i + "/難易度" + difficulty + ".osu"));
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
            library.add(new BeatmapSet("set" + i,title,artist,mapper,null,image,diffs,List.of()));
        }
        var localScores = scene.name.equals("phase5a-state-save-reload")
                ? new dev.osujava.score.LocalScoreStore(output.resolve("score-reload-" + scene.width + "-" + scene.density))
                : new dev.osujava.score.LocalScoreStore();
        if (scene.name.startsWith("phase4")) populateScores(localScores, library, scene.name);
        if (scene.name.startsWith("phase5a")) populatePlayed(localScores,library,scene.name);
        Screen[] destination = {null};
        var game = new OsuJavaGame(null,null) {
            @Override public void navigate(Screen next) { destination[0] = next; }
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
            @Override public SmoothUiFont smoothFont() { return smooth; }
            @Override public BeatmapLibrary library() { return library; }
            @Override public dev.osujava.score.LocalScoreStore localScores() { return localScores; }
            @Override public Path skinDirectory() { return scene.name.equals("phasechrome-custom") ? customSkin
                    : scene.name.startsWith("phasechrome-current") ? Path.of("core/src/main/resources/skins/default").toAbsolutePath() : null; }
            @Override public Path skinFallbackDirectory() { return scene.name.equals("phasechrome-custom") ? null : output.resolve("fixtures/latest"); }
            @Override public dev.osujava.ruleset.osu.OsuRuleset osuRuleset() { return new dev.osujava.ruleset.osu.OsuRuleset(); }
        };
        Path greylooks = Path.of("core/src/main/resources/skins/default").toAbsolutePath();
        var resolver = switch (scene.name) {
            case "phasechrome-current", "phasechrome-current-hover" -> SkinAssetResolver.withBundledDefault(greylooks, output.resolve("fixtures/latest"));
            case "phasechrome-missing" -> new SkinAssetResolver(output.resolve("fixtures/row-only"));
            case "phasechrome-fallback" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), greylooks);
            case "phasechrome-bundled" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), null);
            case "phasechrome-normal" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/normal-only"), greylooks);
            case "phasechrome-giant", "phasechrome-giant-high" -> SkinAssetResolver.withBundledDefault(
                    output.resolve("fixtures/" + scene.name.substring("phasechrome-".length())), greylooks);
            case "phasechrome-tall" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/tall"), greylooks);
            case "phasechrome-present" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/present"), greylooks);
            case "phasechrome-transparent" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/transparent"), greylooks);
            case "phase4-fallback", "phase3-fallback" -> new SkinAssetResolver(output.resolve("fixtures/empty"));
            case "phase4-high-only" -> new SkinAssetResolver(output.resolve("fixtures/high-only"));
            case "phase4-bundled-fallback" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), null);
            case "phase3-modern" -> new SkinAssetResolver(output.resolve("fixtures/latest"));
            case "phase2-missing-star" -> new SkinAssetResolver(output.resolve("fixtures/missing"));
            case "phase2-broken-star" -> new SkinAssetResolver(output.resolve("fixtures/star-broken"));
            case "phase2-high-star" -> new SkinAssetResolver(output.resolve("fixtures/star-high"));
            case "phase2-default-fallback" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), null);
            case "phase25-large-library-modern", "phase25-large-library-modern-rated", "phase25-reference-modern", "phase25-rating-3", "phase25-rating-5", "phase25-rating-7",
                    "phase25-rating-high", "phase25-rating-none", "phase25-thumbnail-missing", "phase25-thumbnail-wide",
                    "phase25-thumbnail-tall", "phase25-chrome-full", "phase25-hover", "phase2-thumbnail-fade", "phase2-missing-thumbnail",
                    "phase2-portrait", "phase2-wide", "phase2-broken-thumbnail", "phase2-v22" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/star-high"), null);
            default -> scene.name.startsWith("greylooks") || phase2 || scene.name.startsWith("phase3") || scene.name.startsWith("phase4") ? SkinAssetResolver.withBundledDefault(null,null)
                    : new SkinAssetResolver(output.resolve("fixtures").resolve(scene.name));
        };
        if (scene.name.startsWith("phase5a")) resolver = toolboxResolver(scene.name,greylooks);
        var assets = scene.name.startsWith("phasechrome-current") || scene.name.equals("phasechrome-custom") ? null : new SongSelectSkinAssets(resolver);
        String preferredSet = scene.name.equals("greylooks-first-item") ? "set0"
                : scene.name.equals("greylooks-last-item") ? "set6"
                : scene.name.startsWith("greylooks-expanded") ? "set2" : "set3";
        int preferredDifficulty = scene.name.equals("greylooks-first-item") ? 0
                : (scene.name.equals("greylooks-last-item") || scene.name.startsWith("greylooks-expanded")) ? 3 : 1;
        var screen = new SongSelectScreen(game,preferredSet,preferredDifficulty,assets, diff -> ratings.getOrDefault(diff, OptionalDouble.empty()));
        var fb = new FrameBuffer(Pixmap.Format.RGBA8888,scene.width * scene.density,scene.height * scene.density,false);
        try {
            screen.show();
            if (assets == null) {
                try { var field = SongSelectScreen.class.getDeclaredField("skin"); field.setAccessible(true);
                    assets = (SongSelectSkinAssets)field.get(screen); }
                catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            }
            screen.legacyThumbnailPreview(scene.name.equals("phase25-legacy-b"));
            screen.resize(scene.width,scene.height);
            fb.begin();
            if (scene.name.startsWith("phase3")) configureBrowserScene(screen, scene.name, processor[0], pointer, clicked, layout, scene.height);
            for (int frame = 0; frame < 40; frame++) screen.render(1f / 60);
            if ((scene.name.equals("phase2-hover") || scene.name.equals("phase25-hover"))) {
                pointerRow(screen,carousel(screen).rows().stream().filter(r -> r.entry.difficultyIndex() == 1).findFirst().orElseThrow().entry.setIndex(),2,pointer,layout,scene.height);
                for (int frame = 0; frame < 60; frame++) screen.render(1f / 60);
            }
            if (scene.name.startsWith("phase4")) configureScores(screen, scene.name, processor[0], pointer, clicked, layout, scene.height);
            String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + scene.name;
            if (scene.name.startsWith("phase5a")) {
                configureToolbox(screen,scene,processor[0],pointer,clicked,pressed,layout,localScores,library);
                if (scene.name.equals("phase5a-state-transitions")) toolboxTransitions(screen,processor[0],fb,name,layout);
                assertToolbox(screen,assets,scene.name,layout);
                capture(fb,name);
                if (scene.name.contains("large")) profileToolbox(screen,processor[0],name,scene.name);
                if (toolboxState(screen).open()) processor[0].keyDown(Input.Keys.ESCAPE);
                pressed[0] = false; pointer[0] = 40;
                fb.end();
                advanceScene();
                return;
            }
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
                    transitionFrames++;
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
                    if (scene.name.endsWith("last")) for (int i = 0; i < 15; i++) processor[0].keyDown(Input.Keys.DOWN);
                    long children = carousel(screen).rows().stream().filter(r -> r.entry.setIndex() == 3 && r.entry.difficultyIndex() >= 0).count();
                    if (children != (scene.name.endsWith("single") ? 1 : 16)) throw new AssertionError("Expansion input missed: " + name);
                    pointer[0] = 40;
                }
                case "greylooks-collapse-many" -> { processor[0].keyDown(Input.Keys.RIGHT); pointer[0] = 40; }
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
                    transitionFrames++;
                    if (frame == 1 || frame == 4 || frame == 10 || frame == 20 || frame == 40 || frame == 60)
                        capture(fb,name + "-frame-" + String.format(Locale.ROOT,"%02d",frame));
                }
            }
            if (scene.name.equals("phase3-transitions")) {
                for (int stage=0;stage<3;stage++) {
                    if (stage == 0) screen.browserMode(SongBrowserModel.Sort.ARTIST, SongBrowserModel.Group.ARTIST);
                    if (stage == 1) screen.browserSearch("Camellia Difficulty",true);
                    if (stage == 2) { screen.browserSearch("",false); screen.browserMode(SongBrowserModel.Sort.LENGTH,SongBrowserModel.Group.BPM); }
                    for (int frame=0;frame<=60;frame++) {
                        screen.render(1f/60); assertRenderedBounds(screen,layout);
                        transitionFrames++;
                        if (frame == 0 || frame == 8 || frame == 24 || frame == 60) capture(fb,name+"-stage-"+stage+"-frame-"+frame);
                    }
                }
            }
            if (scene.name.equals("phase4-transitions")) {
                for (int stage=0;stage<4;stage++) {
                    processor[0].keyDown(stage % 2 == 0 ? Input.Keys.DOWN : Input.Keys.UP);
                    assertScoreTarget(screen);
                    for (int frame=0;frame<=24;frame++) {
                        screen.render(1f/60); assertRenderedBounds(screen,layout);
                        transitionFrames++;
                        if (frame == 0 || frame == 8 || frame == 24) capture(fb,name+"-stage-"+stage+"-frame-"+frame);
                    }
                }
            }
            if (scene.name.startsWith("phasechrome-")) assertChrome(screen, assets, scene.name, greylooks);
            if (scene.name.startsWith("phasechrome-giant")) {
                var rendered = Pixmap.createFromFrameBuffer(0, 0, fb.getWidth(), fb.getHeight());
                try {
                    int x = Math.round(500 * layout.scale() * scene.density);
                    int middle = rendered.getPixel(x, fb.getHeight() / 2);
                    if (middle == Color.rgba8888(Color.MAGENTA) || middle == Color.rgba8888(Color.CYAN))
                        throw new AssertionError("Giant chrome still covers the browser background");
                    int edgeX = Math.round(700 * layout.scale() * scene.density);
                    if (rendered.getPixel(edgeX, fb.getHeight() - 3) != Color.rgba8888(Color.MAGENTA)
                            || rendered.getPixel(edgeX, 3) != Color.rgba8888(Color.CYAN))
                        throw new AssertionError("Chrome clipping changed edge artwork");
                } finally { rendered.dispose(); }
            }
            if (scene.name.equals("phasechrome-present")) {
                Pixmap rendered = Pixmap.createFromFrameBuffer(0, 0, fb.getWidth(), fb.getHeight());
                try {
                    // Opaque source colours must survive in the production framebuffer, with no fallback covering them.
                    // Sample clear chrome, beyond the fixed selection controls and before Cookie.
                    int x = Math.round(700 * layout.scale() * scene.density);
                    int inset = Math.max(1, 3 * scene.density);
                    if (rendered.getPixel(x, fb.getHeight() - inset) != Color.rgba8888(Color.MAGENTA)
                            || rendered.getPixel(x, inset) != Color.rgba8888(Color.CYAN))
                        throw new AssertionError("Procedural chrome covered current skin pixels");
                } finally { rendered.dispose(); }
            }
            capture(fb,name);
            if (scene.name.startsWith("phase3") || scene.name.startsWith("phase4")) {
                screen.browserSearch("",false);
                closeBrowserMenu(screen);
                screen.browserMode(SongBrowserModel.Sort.TITLE,SongBrowserModel.Group.NONE);
                for (int frame=0;frame<90;frame++)screen.render(1f/60);
            }
            assertRenderedBounds(screen,layout);
            assertScrollSettled(screen,name);
            if (setCount > 7) {
                if (scene.name.equals("phase3-large-library")) profileBrowser(screen,name);
                if (scene.name.startsWith("phase4")) profileScores(screen, localScores, name);
                profileMotion(carousel(screen),name);
                profileRender(screen, name);
            }
            // Wheel scenes intentionally leave selection behind. A real difficulty change restores it.
            if (scene.name.contains("scroll") || setCount > 7) {
                processor[0].keyDown(Input.Keys.DOWN); pointer[0] = 40;
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
            var randomBounds = toolboxLayout(screen).control(SongSelectSkinAssets.Selection.RANDOM).interaction();
            if (randomBounds.empty()) processor[0].keyDown(Input.Keys.F2);
            else {
                point(randomBounds,pointer,layout,scene.height);
                clicked[0] = true; screen.render(0); clicked[0] = false;
            }
            // Multi-token metadata search can match the numeric token in a difficulty name.
            // Large fixtures use the unique 002 token; small fixtures verify visible eligibility.
            try {
                var selected = SongSelectScreen.class.getDeclaredField("selectedSetIndex"); selected.setAccessible(true);
                var setsField = SongSelectScreen.class.getDeclaredField("sets"); setsField.setAccessible(true);
                var selectedSet = (BeatmapSet)((List<?>)setsField.get(screen)).get(selected.getInt(screen));
                var browserField = SongSelectScreen.class.getDeclaredField("browser"); browserField.setAccessible(true);
                var browser = (SongBrowserModel)browserField.get(screen);
                if (!browser.visibleSets().contains(selectedSet) || browser.selectedDifficulty() == null
                        || setCount > 7 && !selectedSet.id().equals("set2")) throw new AssertionError("Search/Random selection changed: " + name);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            // Selected difficulty re-click also reaches the production Play transition.
            pointer[0] = 40;
            for (int frame=0;frame<60;frame++) screen.render(1f/60);
            model = carousel(screen);
            selectedRow = model.rows().stream().max(Comparator.comparingDouble(r -> r.selectedAmount)).orElseThrow();
            if ((scene.name.equals("phase3-chrome-cookie") || scene.name.equals("phase4-sibling"))) {
                screen.browserMode(SongBrowserModel.Sort.LENGTH,SongBrowserModel.Group.BPM);
                processor[0].keyDown(Input.Keys.DOWN);
                pointer[0] = Math.round((layout.width()-20)*layout.scale()); pointer[1] = scene.height-Math.round(30*layout.scale());
            } else pointerRow(screen,selectedRow.entry.setIndex(),selectedRow.entry.difficultyIndex(),pointer,layout,scene.height);
            clicked[0] = true; screen.render(0); clicked[0] = false;
            if (!pending(screen)) throw new AssertionError("Selected re-click did not play: " + name);
            if ((scene.name.equals("phase3-chrome-cookie") || scene.name.equals("phase4-sibling"))) {
                try {
                    var browserField = SongSelectScreen.class.getDeclaredField("browser"); browserField.setAccessible(true);
                    var browser = (SongBrowserModel)browserField.get(screen);
                    var expectedSet = browser.selectedSet(); var expectedDifficulty = browser.selectedDifficulty();
                    var outgoing = SongSelectScreen.class.getDeclaredField("outgoing"); outgoing.setAccessible(true);
                    ((UiNavigation)outgoing.get(screen)).advance(.2f);
                    if (!(destination[0] instanceof GameplayScreen)) throw new AssertionError("Cookie did not navigate to gameplay");
                    var difficultyField = GameplayScreen.class.getDeclaredField("difficulty"); difficultyField.setAccessible(true);
                    var setField = GameplayScreen.class.getDeclaredField("set"); setField.setAccessible(true);
                    if (difficultyField.get(destination[0]) != expectedDifficulty || setField.get(destination[0]) != expectedSet)
                        throw new AssertionError("Cookie activated a stale difficulty");
                } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
                finally { if(destination[0] != null) destination[0].dispose(); }
            }
            fb.end();
        } finally {
            screen.dispose(); screen.dispose();
            for (var image : SongSelectSkinAssets.Image.values()) if (assets.get(image) != null) throw new AssertionError("Texture retained");
            if (assets.starTexture() != null) throw new AssertionError("Star texture retained after disposal");
            fb.dispose(); Gdx.graphics = actualGraphics; Gdx.input = actualInput;
        }
        advanceScene();
    }

    private void advanceScene() {
        if (++index == scenes.size()) {
            System.out.println("SongSelect harness: " + index + " scenes, " + captures + " PNG captures, " + transitionFrames
                    + " scripted transition frames + navigation/disposal checks passed: " + output);
            Gdx.app.exit();
        }
    }

    private SkinAssetResolver toolboxResolver(String name, Path greylooks) {
        if (name.startsWith("phase5a-profile-")) {
            int profile = Integer.parseInt(name.split("-")[2]);
            return SkinAssetResolver.withBundledDefault(compatibilitySkins.get(profile),null);
        }
        String prefix = "phase5a-assets-";
        if (!name.startsWith(prefix)) return SkinAssetResolver.withBundledDefault(greylooks,null);
        String fixture = name.substring(prefix.length());
        return switch (fixture) {
            case "fallback" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"),greylooks);
            case "bundled" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"),null);
            case "malformed" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/toolbox-malformed"),null);
            case "normal-bundled" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/toolbox-normal-bundled"),null);
            default -> new SkinAssetResolver(output.resolve("fixtures/toolbox-" + fixture));
        };
    }
    private Object screenField(SongSelectScreen screen, String name) {
        try { var f = SongSelectScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private SongSelectToolboxLayout toolboxLayout(SongSelectScreen screen) { return (SongSelectToolboxLayout)screenField(screen,"bottomLayout"); }
    private SongSelectToolboxState toolboxState(SongSelectScreen screen) { return (SongSelectToolboxState)screenField(screen,"toolbox"); }
    private SongBrowserModel browser(SongSelectScreen screen) { return (SongBrowserModel)screenField(screen,"browser"); }
    private void point(SongSelectToolboxLayout.Bounds b, int[] pointer, UiLayout layout, int height) {
        pointer[0] = Math.round((b.x()+b.width()/2)*layout.scale());
        pointer[1] = height-Math.round((b.y()+b.height()/2)*layout.scale());
    }
    private void populatePlayed(dev.osujava.score.LocalScoreStore store, BeatmapLibrary library, String name) {
        if (name.startsWith("phase5a-state-large")) {
            for (var set : library.all()) if (Integer.parseInt(set.id().substring(3)) % 2 == 0) savePlayed(store,set,0);
            var selected = library.all().stream().filter(s -> s.id().equals("set3")).findFirst().orElseThrow();
            for (int i = 0; i < 10; i++) savePlayed(store,selected,1);
        } else if (name.equals("phase5a-state-played") || name.equals("phase5a-state-sibling")
                || name.equals("phase5a-state-selected-played") || name.equals("phase5a-state-selected-unplayed")) {
            for (var set : library.all()) if (set.id().equals("set2") || set.id().equals("set4")) savePlayed(store,set,0);
            if (!name.equals("phase5a-state-played")) {
                var selected = library.all().stream().filter(s -> s.id().equals("set3")).findFirst().orElseThrow();
                savePlayed(store,selected,name.equals("phase5a-state-selected-played") ? 1 : 0);
            }
        }
    }
    private void savePlayed(dev.osujava.score.LocalScoreStore store, BeatmapSet set, int difficulty) {
        store.save(new dev.osujava.score.LocalScore(UUID.randomUUID(),dev.osujava.score.DifficultyIdentity.of(set.id(),set.difficulties().get(difficulty)),
                1_790_467_200_000L,new dev.osujava.gameplay.ScoreState(123456,0,100,100,0,0,0,1)),dev.osujava.gameplay.GameplayRunMode.MANUAL);
    }
    private void configureToolbox(SongSelectScreen screen, Scene scene, InputProcessor input,
            int[] pointer, boolean[] clicked, boolean[] pressed, UiLayout layout,
            dev.osujava.score.LocalScoreStore store, BeatmapLibrary library) {
        String state = scene.name.replaceFirst("phase5a-profile-[0-9]+-", "").replaceFirst("phase5a-(current|state|assets)-", "");
        var geometry = toolboxLayout(screen);
        if (state.equals("oversized-hover")) point(geometry.control(SongSelectSkinAssets.Selection.MODE).interaction(),pointer,layout,scene.height);
        for (var action : SongSelectSkinAssets.Selection.values()) if (state.equals(action.name().toLowerCase(Locale.ROOT)+"-hover")
                || state.equals(action.name().toLowerCase(Locale.ROOT)+"-pressed")) point(geometry.control(action).interaction(),pointer,layout,scene.height);
        if (state.startsWith("back-")) point(geometry.backInteraction,pointer,layout,scene.height);
        if (state.startsWith("import-")) point(geometry.importAction,pointer,layout,scene.height);
        if (state.startsWith("cookie-")) point(new SongSelectToolboxLayout.Bounds(layout.width()-60,10,50,35),pointer,layout,scene.height);
        pressed[0] = state.endsWith("pressed");
        if (state.equals("disabled")) screen.browserSearch("no matching beatmap at all",false);
        if (state.equals("mode-view")) {
            var modeBounds = geometry.control(SongSelectSkinAssets.Selection.MODE).interaction();
            point(modeBounds,pointer,layout,scene.height);
            clicked[0] = true; screen.render(0); clicked[0] = false;
            if (modeBounds.empty()) {
                if (toolboxState(screen).open()) throw new AssertionError("Suppressed Mode gained a phantom click target");
            } else if (toolboxState(screen).overlay() != SongSelectToolboxState.Overlay.MODE)
                throw new AssertionError("Mode did not open capability view");
        }
        if (state.equals("mods-view") || state.equals("active-none") || state.equals("large-mods")) {
            if (state.equals("mods-view")) {
                point(geometry.control(SongSelectSkinAssets.Selection.MODS).interaction(),pointer,layout,scene.height);
                clicked[0] = true; screen.render(0); clicked[0] = false;
            } else input.keyDown(Input.Keys.F1);
            if (toolboxState(screen).overlay() != SongSelectToolboxState.Overlay.MODS || !toolboxState(screen).active().isEmpty())
                throw new AssertionError("Mods selector invented gameplay capabilities");
        }
        if (toolboxState(screen).open()) {
            var selected = browser(screen).selectedDifficulty(); String search = (String)screenField(screen,"search");
            var scores = scoreBrowser(screen); int first = scores.first(); var selectedScore = scores.selected();
            for (var b : List.of(geometry.control(SongSelectSkinAssets.Selection.RANDOM).interaction(),geometry.cookie,
                    new SongSelectToolboxLayout.Bounds(800,590,70,40),new SongSelectToolboxLayout.Bounds(30,560,70,30))) {
                point(b,pointer,layout,scene.height); clicked[0] = true; screen.render(0); clicked[0] = false;
            }
            for (int key : new int[]{Input.Keys.F2,Input.Keys.F3,Input.Keys.I,Input.Keys.ENTER,Input.Keys.F6,Input.Keys.DOWN}) input.keyDown(key);
            input.keyTyped('x'); input.scrolled(0,3);
            if (browser(screen).selectedDifficulty() != selected || !screenField(screen,"search").equals(search)
                    || pending(screen) || scores.first() != first || !Objects.equals(scores.selected(),selectedScore))
                throw new AssertionError("Selector input leaked to underlying UI");
        }
        if (state.equals("save-reload")) {
            var selected = browser(screen).selectedSet(); var diff = browser(screen).selectedDifficulty();
            savePlayed(store,selected,selected.difficulties().indexOf(diff)); screen.render(0);
            var snapshot = (SongSelectScoreSnapshot)screenField(screen,"scoreSnapshot");
            var reloaded = new dev.osujava.score.LocalScoreStore(output.resolve("score-reload-" + scene.width + "-" + scene.density));
            var restart = new SongSelectScoreSnapshot(reloaded); restart.refresh(browser(screen).librarySets());
            if (!snapshot.played(selected,diff) || !restart.played(selected,diff) || scoreBrowser(screen).rows().isEmpty())
                throw new AssertionError("Saved score did not update browser and survive reload");
        }
        if (state.equals("aborted")) {
            long revision = store.revision();
            var game = new OsuJavaGame(null,null) {
                @Override public SpriteBatch batch() { return batch; }
                @Override public ShapeRenderer shapes() { return shapes; }
                @Override public BitmapFont font() { return font; }
                @Override public SmoothUiFont smoothFont() { return smooth; }
                @Override public BeatmapLibrary library() { return library; }
                @Override public dev.osujava.score.LocalScoreStore localScores() { return store; }
                @Override public dev.osujava.ruleset.osu.OsuRuleset osuRuleset() { return new dev.osujava.ruleset.osu.OsuRuleset(); }
                @Override public void navigate(Screen next) { next.dispose(); }
            };
            var gameplay = new GameplayScreen(game,browser(screen).selectedSet(),browser(screen).selectedDifficulty());
            gameplay.show(); Gdx.input.getInputProcessor().keyDown(Input.Keys.ESCAPE); gameplay.dispose(); Gdx.input.setInputProcessor(input);
            if (store.revision() != revision) throw new AssertionError("Aborted gameplay marked played");
        }
        if (state.equals("large-hover")) point(geometry.control(SongSelectSkinAssets.Selection.RANDOM).interaction(),pointer,layout,scene.height);
        if (state.equals("large-scroll")) for (int i=0;i<12;i++) { carousel(screen).scrollBy(80); screen.render(1f/60); }
        screen.render(1f/60);
        assertRenderedBounds(screen,layout);
    }

    private void assertToolbox(SongSelectScreen screen, SongSelectSkinAssets assets, String name, UiLayout layout) {
        var geometry = toolboxLayout(screen);
        boolean authoredSurface = false;
        for (var image : SongSelectSkinAssets.Image.values()) if (image == SongSelectSkinAssets.Image.TOP
                || image == SongSelectSkinAssets.Image.BOTTOM || image == SongSelectSkinAssets.Image.BACK
                || image == SongSelectSkinAssets.Image.MENU_BUTTON_BACKGROUND || SongSelectSkinAssets.Selection.of(image) != null)
            authoredSurface |= assets.get(image) != null && assets.provider(image).equals("current");
        for (var image : List.of(SongSelectSkinAssets.Image.TOP,SongSelectSkinAssets.Image.BOTTOM)) {
            if (authoredSurface && assets.get(image) != null && !assets.provider(image).equals("current"))
                throw new AssertionError("Foreign chrome in authored Song Select: " + name + " " + image);
            if (screen.renderedChromeProcedural(image) != (assets.get(image) == null))
                throw new AssertionError("Chrome rendering presence differs from provider resolution");
        }
        System.out.println("CHROME OWNERSHIP " + name + " top-visual=" + assets.provider(SongSelectSkinAssets.Image.TOP)
                + " top-layout=" + assets.topLayoutProvider() + " bottom-visual=" + assets.provider(SongSelectSkinAssets.Image.BOTTOM)
                + " bottom-artwork=" + geometry.bottomImage + " bottom-reservation=" + geometry.chrome);
        if (name.equals("phase5a-assets-tiny-chrome") && (geometry.bottomImage.height() >= 1
                || geometry.chrome.height() < SongSelectChrome.bottomHeight(layout.height())
                || assets.get(SongSelectSkinAssets.Image.TOP) == null || assets.get(SongSelectSkinAssets.Image.BOTTOM) == null))
            throw new AssertionError("Tiny chrome changed presence or layout reservation");
        float scale = layout.height()/768;
        boolean legacy = assets.configuration().legacyVersion() < 2;
        if (Math.abs(geometry.control(SongSelectSkinAssets.Selection.MODE).anchorX()
                - (layout.width() > layout.height()*4/3 ? 224 : 192)*scale) > .01f)
            throw new AssertionError("Navigation origin depended on Back artwork");
        var back = assets.get(SongSelectSkinAssets.Image.BACK);
        if (back != null && (geometry.backImage.x() != 0 || geometry.backImage.y() != 0
                || Math.abs(geometry.backImage.width()-back.logicalWidth()*scale) > .01f
                || Math.abs(geometry.backImage.height()-back.logicalHeight()*scale) > .01f))
            throw new AssertionError("Back raw origin/scale lost transparent margins");
        for (var action : SongSelectSkinAssets.Selection.values()) {
            var control = geometry.control(action);
            if (control.slot().y() != geometry.baseline || control.slot().height() != geometry.controlHeight
                    || control.interaction().width() > control.slot().width()+.01f || control.interaction().height() > control.slot().height()+.01f
                    || control.slot().x()+control.slot().width() > geometry.cookie.x()) throw new AssertionError("Toolbox geometry diverged");
            if (screen.renderedSelectionProcedural(action) != (assets.get(action.normal) == null)) throw new AssertionError("Current selection asset lost to procedural");
            if (assets.provider(action.normal).equals("current") && assets.get(action.hover) != null
                    && !assets.provider(action.hover).equals("current")) throw new AssertionError("Foreign hover in current visual family");
            for (var image : List.of(action.normal,action.hover)) {
                var texture = assets.get(image);
                var artwork = image == action.normal ? control.normal() : control.hover();
                if (texture != null && (artwork.image().x() != control.anchorX()
                        || Math.abs(artwork.image().width()-texture.logicalWidth()*scale) > .01f
                        || Math.abs(artwork.image().height()-texture.logicalHeight()*scale) > .01f
                        || Math.abs(artwork.image().y() - (legacy ? control.anchorY()-artwork.image().height() : control.anchorY())) > .01f))
                    throw new AssertionError("Raw artwork was fitted or alpha-aligned: " + image);
                if (name.equals("phase5a-assets-transparent") && (!artwork.opaque().empty() || !control.interaction().empty()))
                    throw new AssertionError("Transparent replacement created interaction");
                if (name.startsWith("phase5a-assets-asymmetric")
                        && (control.interaction().contains(artwork.image().x()+310*scale,135*scale)
                            || Math.abs(control.interaction().x()-(control.anchorX()+20*scale)) > .01f
                            || Math.abs(control.interaction().y()-20*scale) > .01f))
                    throw new AssertionError("Decorative overshoot became an action");
                if (name.startsWith("phase5a-current") && (texture == null || texture.file().fallback() || texture.density() != 2))
                    throw new AssertionError("Current @2x selection did not win: " + image);
                if (name.startsWith("phase5a-profile-")) {
                    Path path = compatibilitySkins.get(Integer.parseInt(name.split("-")[2]));
                    var local = new SkinAssetResolver(path).resolve(image.basename);
                    if (local.isPresent() && (texture == null || texture.file().fallback() || !texture.file().path().equals(local.get().path())))
                        throw new AssertionError("Composite current asset replaced: " + image);
                }
                if ((name.equals("phase5a-assets-normal") || name.equals("phase5a-assets-normal-bundled"))
                        && image == action.hover && texture != null) throw new AssertionError("Normal-only fixture supplied hover");
                if (name.equals("phase5a-assets-high") && (texture == null || texture.density() != 2)) throw new AssertionError("Selection density lost");
                String expected = name.equals("phase5a-assets-fallback") ? "fallback"
                        : name.equals("phase5a-assets-bundled") || name.equals("phase5a-assets-malformed") ? "bundled"
                        : name.equals("phase5a-assets-missing") ? "procedural" : null;
                if (expected != null && !assets.provider(image).equals(expected)) throw new AssertionError("Selection provider priority: " + name + " " + image);
                System.out.println("SELECTION PASS " + name + " asset=" + image.basename + " provider=" + assets.provider(image)
                        + " density=" + (texture == null ? 0 : texture.density()) + " procedural=" + (texture == null)
                        + " image=" + (image == action.normal ? control.normal().image() : control.hover().image())
                        + " opaque=" + (image == action.normal ? control.normal().opaque() : control.hover().opaque())
                        + " interaction=" + control.interaction());
            }
        }
        if (name.equals("phase5a-assets-transparent") && !geometry.backInteraction.empty())
            throw new AssertionError("Transparent Back created interaction");
        if (geometry.importAction.height() >= geometry.controlHeight*.5f) throw new AssertionError("Import gained primary visual weight");
        var snapshot = (SongSelectScoreSnapshot)screenField(screen,"scoreSnapshot");
        for (var set : browser(screen).librarySets()) {
            boolean played = set.difficulties().stream().anyMatch(d -> snapshot.best(set,d) != null);
            if (snapshot.played(set) != played) throw new AssertionError("Set played projection wrong");
        }
        if (name.equals("phase5a-state-unplayed") || name.equals("phase5a-state-aborted")) {
            if (browser(screen).librarySets().stream().anyMatch(snapshot::played)) throw new AssertionError("Unplayed/aborted Set changed colour");
        }
        if (name.equals("phase5a-state-selected-played") && !snapshot.played(browser(screen).selectedSet(),browser(screen).selectedDifficulty()))
            throw new AssertionError("Selected played fixture lacks real score");
        if (name.equals("phase5a-state-selected-unplayed") && snapshot.played(browser(screen).selectedSet(),browser(screen).selectedDifficulty()))
            throw new AssertionError("Selected unplayed fixture gained score");
    }

    private void toolboxTransitions(SongSelectScreen screen, InputProcessor input, FrameBuffer fb, String name, UiLayout layout) {
        for (int stage = 0; stage < 5; stage++) {
            if (stage == 0) input.keyDown(Input.Keys.F1);
            if (stage == 1) input.keyDown(Input.Keys.ESCAPE);
            if (stage == 2) screen.browserMode(SongBrowserModel.Sort.ARTIST,SongBrowserModel.Group.ARTIST);
            if (stage == 3) screen.browserSearch("Local song 2",false);
            if (stage == 4) { screen.browserSearch("",false); input.keyDown(Input.Keys.F2); }
            for (int frame = 0; frame < 25; frame++) {
                screen.render(1f/60); assertRenderedBounds(screen,layout); transitionFrames++;
                if (frame == 0 || frame == 8 || frame == 24) capture(fb,name+"-stage-"+stage+"-frame-"+frame);
            }
        }
    }
    private void profileToolbox(SongSelectScreen screen, InputProcessor input, String name, String state) {
        long[] samples = new long[180];
        for (int frame = 0; frame < 240; frame++) {
            if (state.endsWith("scroll")) carousel(screen).scrollBy(frame < 120 ? 90 : -90);
            long start = System.nanoTime(); screen.render(1f/60);
            if (frame >= 60) samples[frame-60] = System.nanoTime()-start;
        }
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT,"Toolbox CPU submission %s: mean %.3f ms, p95 %.3f ms, max %.3f ms (180 samples)%n",name,
                Arrays.stream(samples).average().orElseThrow()/1e6,samples[170]/1e6,samples[179]/1e6);
        if (toolboxState(screen).open()) input.keyDown(Input.Keys.ESCAPE);
        long[] switches = new long[100];
        for (int i=0;i<140;i++) { long start=System.nanoTime(); input.keyDown(i%2==0 ? Input.Keys.DOWN : Input.Keys.UP);
            if(i>=40)switches[i-40]=System.nanoTime()-start; }
        Arrays.sort(switches);
        System.out.printf(Locale.ROOT,"Toolbox difficulty switch %s: mean %.3f ms, p95 %.3f ms, max %.3f ms%n",name,
                Arrays.stream(switches).average().orElseThrow()/1e6,switches[94]/1e6,switches[99]/1e6);
    }
    private void populateScores(dev.osujava.score.LocalScoreStore store, BeatmapLibrary library, String name) {
        var set = library.all().stream().filter(b -> b.id().equals("set3")).findFirst().orElseThrow();
        int count = name.equals("phase4-empty") || name.equals("phase4-large-0") ? 0
                : name.startsWith("phase4-large-") ? Integer.parseInt(name.substring("phase4-large-".length()))
                : name.equals("phase4-single") || name.matches("phase4-(SS|S|A|B|C|D)") ? 1
                : name.equals("phase4-three") ? 3 : 16;
        int[][] counts = {{100,0,0,0},{91,9,0,0},{81,19,0,0},{71,29,0,0},{61,39,0,0},{20,30,20,30}};
        for (int i=0;i<count;i++) {
            int grade = name.matches("phase4-(SS|S|A|B|C|D)")
                    ? dev.osujava.ruleset.osu.OsuGrade.valueOf(name.substring(7)).ordinal() : i % 6;
            var tracker = new dev.osujava.gameplay.ScoreTracker();
            for(int j=0;j<4;j++) for(int n=0;n<counts[grade][j];n++)
                tracker.record(dev.osujava.gameplay.Judgement.values()[j]);
            if (name.startsWith("phase4-numbers") && i == 0) tracker = new dev.osujava.gameplay.ScoreTracker();
            if (name.startsWith("phase4-numbers") && i == 0) tracker.record(dev.osujava.gameplay.Judgement.HIT50);
            if (name.startsWith("phase4-numbers") && i == 1) {
                for(int n=0;n<10000;n++)tracker.recordNestedHit(0,true,true);
                tracker.recordBonusScore(9_876_543_210L);
            } else if (count>100) tracker.recordBonusScore(i * 100000L);
            var identity = dev.osujava.score.DifficultyIdentity.of(set.id(),set.difficulties().get(1));
            store.save(new dev.osujava.score.LocalScore(new UUID(0,i+1),identity,1_790_467_200_000L + i * 60000L,
                    tracker.snapshot()),dev.osujava.gameplay.GameplayRunMode.MANUAL);
        }
        if (!name.equals("phase4-empty") && !name.startsWith("phase4-large")) {
            var identity = dev.osujava.score.DifficultyIdentity.of(set.id(),set.difficulties().get(2));
            store.save(new dev.osujava.score.LocalScore(new UUID(1,1),identity,1_790_467_200_000L,
                    new dev.osujava.gameplay.ScoreState(30000,100,100,100,0,0,0,1)),dev.osujava.gameplay.GameplayRunMode.MANUAL);
        }
    }
    private ScoreBrowserModel scoreBrowser(SongSelectScreen screen) {
        try { var f=SongSelectScreen.class.getDeclaredField("scores"); f.setAccessible(true); return (ScoreBrowserModel)f.get(screen); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void assertScoreTarget(SongSelectScreen screen) {
        try {
            var f=SongSelectScreen.class.getDeclaredField("browser"); f.setAccessible(true);
            var browser=(SongBrowserModel)f.get(screen);
            var target=browser.selectedSet()==null ? null : dev.osujava.score.DifficultyIdentity.of(browser.selectedSet().id(),browser.selectedDifficulty());
            if (!Objects.equals(target,scoreBrowser(screen).target())) throw new AssertionError("Stale score target");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void configureScores(SongSelectScreen screen, String name, InputProcessor input, int[] pointer,
                                 boolean[] clicked, UiLayout layout, int height) {
        var scores=scoreBrowser(screen); var bounds=screen.scoreBounds(layout);
        pointer[0]=Math.round(100*layout.scale()); pointer[1]=height-Math.round((bounds.top()-32)*layout.scale());
        float carouselTarget=carousel(screen).scrollTarget();
        int before=scores.first(); input.scrolled(0,1);
        if(carousel(screen).scrollTarget()!=carouselTarget) throw new AssertionError("Left wheel moved carousel");
        scores.scroll(-1);
        if(scores.first()!=before) throw new AssertionError("Score scroll did not reverse");
        pointer[0]=Math.round(layout.width()*.9f*layout.scale()); pointer[1]=height/2;
        input.scrolled(0,1);
        if(scores.first()!=before) throw new AssertionError("Right wheel moved score browser");
        input.scrolled(0,-1);
        pointer[0]=Math.round(100*layout.scale()); pointer[1]=height-Math.round((bounds.top()-32)*layout.scale());
        if(name.equals("phase4-scroll-middle")) scores.scroll(6);
        if(name.equals("phase4-scroll-bottom") || name.equals("phase4-numbers-bottom")) scores.scroll(10000);
        if(name.equals("phase4-selected")) { clicked[0]=true; screen.render(0); clicked[0]=false; if(scores.selected()==null)throw new AssertionError("Score click lost"); }
        if(name.equals("phase4-no-score")) input.keyDown(Input.Keys.UP);
        if(name.equals("phase4-group")) screen.browserMode(SongBrowserModel.Sort.ARTIST,SongBrowserModel.Group.ARTIST);
        if(name.equals("phase4-search")) screen.browserSearch("Local song",true);
        assertScoreTarget(screen);
        for (int frame=0;frame<90;frame++) screen.render(1f/60);
    }
    private void profileScores(SongSelectScreen screen, dev.osujava.score.LocalScoreStore store, String name) {
        var scores=scoreBrowser(screen); var target=scores.target();
        for(String operation:List.of("query","sort","scroll","difficulty","input-switch","cold-format")) {
            long[] samples=new long[100];
            for(int i=0;i<140;i++) {
                long start=System.nanoTime();
                switch(operation) {
                    case "query" -> store.query(target);
                    case "sort" -> { var copy=new ArrayList<>(store.query(target)); copy.sort(dev.osujava.score.LocalScore.ORDER); }
                    case "scroll" -> scores.scroll(i%2==0 ? 1 : -1);
                    case "difficulty" -> { scores.target(null); scores.target(target); }
                    case "input-switch" -> Gdx.input.getInputProcessor().keyDown(i%2==0 ? Input.Keys.DOWN : Input.Keys.UP);
                    case "cold-format" -> new ScoreBrowserModel(store).target(target);
                }
                if(i>=40)samples[i-40]=System.nanoTime()-start;
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT,"Score profile %s %s: mean %.3f ms, p95 %.3f ms, max %.3f ms%n",name,operation,Arrays.stream(samples).average().orElseThrow()/1e6,samples[94]/1e6,samples[99]/1e6);
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
        captures++;
    }
    private Object valueUnchecked(Class<?> type, Object object, String name) {
        try { return value(type,object,name); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void closeBrowserMenu(SongSelectScreen screen) {
        try { var f = SongSelectScreen.class.getDeclaredField("controls"); f.setAccessible(true); ((SongBrowserControls)f.get(screen)).close(); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void configureBrowserScene(SongSelectScreen screen, String name, InputProcessor processor, int[] pointer, boolean[] clicked, UiLayout layout, int height) {
        var sort = switch(name) {
            case "phase3-sort-artist" -> SongBrowserModel.Sort.ARTIST;
            case "phase3-sort-bpm" -> SongBrowserModel.Sort.BPM;
            case "phase3-sort-length" -> SongBrowserModel.Sort.LENGTH;
            default -> SongBrowserModel.Sort.TITLE;
        };
        var group = switch(name) {
            case "phase3-group-creator" -> SongBrowserModel.Group.CREATOR;
            case "phase3-group-bpm" -> SongBrowserModel.Group.BPM;
            case "phase3-group-length" -> SongBrowserModel.Group.LENGTH;
            case "phase3-fallback", "phase3-modern", "phase3-group-artist", "phase3-group-expanded", "phase3-group-selected", "phase3-group-first", "phase3-group-last" -> SongBrowserModel.Group.ARTIST;
            default -> SongBrowserModel.Group.NONE;
        };
        screen.browserMode(sort,group);
        switch(name) {
            case "phase3-chrome-full" -> {
                var groupBounds = SongBrowserControls.groupBounds(layout.width(),layout.height());
                var sortBounds = SongBrowserControls.sortBounds(layout.width(),layout.height());
                clickBrowser(screen,groupBounds.x()+10,groupBounds.y()+10,pointer,clicked,layout,height);
                clickBrowser(screen,groupBounds.x()+10,groupBounds.y()-2*26-5,pointer,clicked,layout,height);
                clickBrowser(screen,sortBounds.x()+10,sortBounds.y()+10,pointer,clicked,layout,height);
                clickBrowser(screen,sortBounds.x()+10,sortBounds.y()-26-5,pointer,clicked,layout,height);
                try {
                    var f = SongSelectScreen.class.getDeclaredField("browser"); f.setAccessible(true); var model = (SongBrowserModel)f.get(screen);
                    if(model.group() != SongBrowserModel.Group.CREATOR || model.sort() != SongBrowserModel.Sort.ARTIST
                            || !model.selectedSet().id().equals("set3") || !model.selectedDifficulty().version().equals("Difficulty 2"))
                        throw new AssertionError("Browser control input lost selection");
                } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
                pointer[0] = 40;
            }
            case "phase3-search-active" -> screen.browserSearch("",true);
            case "phase3-search-short" -> screen.browserSearch("Camellia Difficulty",true);
            case "phase3-search-long" -> screen.browserSearch("Local song Camellia Harness Difficulty Local song Camellia Harness Difficulty",true);
            case "phase3-search-unicode" -> screen.browserSearch("夜空",true);
            case "phase3-search-none" -> screen.browserSearch("no matching beatmap",true);
            case "phase3-group-first" -> { for(int i=0;i<12;i++)processor.keyDown(Input.Keys.LEFT); }
            case "phase3-group-last" -> { for(int i=0;i<12;i++)processor.keyDown(Input.Keys.RIGHT); }
            case "phase3-menu-group", "phase3-menu-sort" -> {
                var bounds = name.endsWith("group") ? SongBrowserControls.groupBounds(layout.width(),layout.height()) : SongBrowserControls.sortBounds(layout.width(),layout.height());
                pointer[0] = Math.round((bounds.x()+20)*layout.scale()); pointer[1] = height - Math.round((bounds.y()+10)*layout.scale());
                clicked[0] = true; screen.render(0); clicked[0] = false;
            }
        }
    }
    private void clickBrowser(SongSelectScreen screen, float x, float y, int[] pointer, boolean[] clicked, UiLayout layout, int height) {
        pointer[0] = Math.round(x*layout.scale()); pointer[1] = height-Math.round(y*layout.scale());
        clicked[0] = true; screen.render(0); clicked[0] = false;
    }
    private void profileBrowser(SongSelectScreen screen, String name) {
        try {
            var f = SongSelectScreen.class.getDeclaredField("browser"); f.setAccessible(true); var model = (SongBrowserModel)f.get(screen);
            for (String operation : List.of("search", "sort", "group")) {
                long[] samples = new long[100];
                for(int i=0;i<140;i++) {
                    long start = System.nanoTime();
                    if(operation.equals("search")) model.search(i%2==0 ? "Local song" : "Local song 00");
                    if(operation.equals("sort")) model.sort(SongBrowserModel.Sort.values()[i%5]);
                    if(operation.equals("group")) model.group(SongBrowserModel.Group.values()[i%5]);
                    if(i>=40)samples[i-40] = System.nanoTime()-start;
                }
                Arrays.sort(samples);
                System.out.printf(Locale.ROOT,"Browser profile %s %s: mean %.3f ms, p95 %.3f ms, max %.3f ms (100 samples)%n",name,operation,Arrays.stream(samples).average().orElseThrow()/1e6,samples[94]/1e6,samples[99]/1e6);
                model.search(""); model.sort(SongBrowserModel.Sort.TITLE); model.group(SongBrowserModel.Group.NONE);
            }
            for (String operation : List.of("search", "sort", "group")) {
                long[] samples = new long[60];
                for(int i=0;i<100;i++) {
                    long start = System.nanoTime();
                    if(operation.equals("search")) screen.browserSearch(i%2==0 ? "Local song" : "Local song 00",false);
                    if(operation.equals("sort")) screen.browserMode(SongBrowserModel.Sort.values()[i%5],SongBrowserModel.Group.NONE);
                    if(operation.equals("group")) screen.browserMode(SongBrowserModel.Sort.TITLE,SongBrowserModel.Group.values()[i%5]);
                    if(i>=40)samples[i-40] = System.nanoTime()-start;
                }
                Arrays.sort(samples);
                System.out.printf(Locale.ROOT,"Browser bridge profile %s %s: mean %.3f ms, p95 %.3f ms, max %.3f ms (60 samples)%n",name,operation,Arrays.stream(samples).average().orElseThrow()/1e6,samples[56]/1e6,samples[59]/1e6);
                screen.browserSearch("",false); screen.browserMode(SongBrowserModel.Sort.TITLE,SongBrowserModel.Group.NONE);
            }
            screen.browserMode(SongBrowserModel.Sort.TITLE,SongBrowserModel.Group.NONE);
        } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
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
        pointer[1] = height - Math.round((model.renderY(row,screen.chromeBounds(layout).carouselTop()) + model.rowHeight()/2) * layout.scale());
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
                var row = model.rows().stream().filter(r -> r.entry.setIndex() == set && r.entry.difficultyIndex() == diff
                        && (set >= 0 || Math.abs(model.renderY(r,screen.chromeBounds(layout).carouselTop()) - (float)valueUnchecked(type,snapshot,"y")) < .001f)).findFirst().orElseThrow();
                float x = (float) value(type,snapshot,"x"), y = (float) value(type,snapshot,"y");
                if (Math.abs(x - model.renderX(row,layout.width())) > .001f
                        || Math.abs(y - model.renderY(row,screen.chromeBounds(layout).carouselTop())) > .001f)
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
