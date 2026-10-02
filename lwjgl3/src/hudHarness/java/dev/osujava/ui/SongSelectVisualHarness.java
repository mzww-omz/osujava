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
            createParityFixtures();
            for (String mode : List.of("scale", "crop")) {
                Path dir = Files.createDirectories(output.resolve("fixtures/foreground-" + mode));
                Files.writeString(dir.resolve("skin.ini"), "[General]\nVersion: " + (mode.equals("scale") ? "2.2" : "1") + "\n");
                int density = mode.equals("scale") ? 2 : 1;
                for (var image : List.of(SongSelectSkinAssets.Image.MODE_TAIKO_SMALL, SongSelectSkinAssets.Image.GRADE_A)) {
                    int w = image == SongSelectSkinAssets.Image.GRADE_A ? 24 : 20;
                    int h = image == SongSelectSkinAssets.Image.GRADE_A ? 16 : 12;
                    var square = new Pixmap(w*density+(density==2 ? 1 : 0),h*density+(density==2 ? 1 : 0),Pixmap.Format.RGBA8888);
                    square.setColor(Color.WHITE); square.fill();
                    PixmapIO.writePNG(Gdx.files.absolute(dir.resolve(image.basename+(density==2 ? "@2x" : "")+".png").toString()),square);
                    square.dispose();
                }
            }
            Path rowColours = Files.createDirectories(output.resolve("fixtures/row-colours-sprite"));
            Files.createDirectories(output.resolve("fixtures/row-colours-procedural"));
            var whiteRow = new Pixmap(800,64,Pixmap.Format.RGBA8888);
            whiteRow.setColor(Color.WHITE); whiteRow.fill();
            PixmapIO.writePNG(Gdx.files.absolute(rowColours.resolve("menu-button-background.png").toString()),whiteRow);
            whiteRow.dispose();
            for (String mode : List.of("scale", "crop")) Files.copy(rowColours.resolve("menu-button-background.png"),
                    output.resolve("fixtures/foreground-"+mode+"/menu-button-background.png"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            for (String mode : List.of("scale", "crop", "old-default-background")) {
                Path dir = Files.createDirectories(output.resolve("fixtures/star-animation-" + mode));
                Files.writeString(dir.resolve("skin.ini"), "[General]\nVersion: " + (mode.equals("scale") ? "2.2" : "1") + "\n");
                int density = mode.equals("crop") ? 1 : 2;
                int pixels = 40*density + (mode.equals("old-default-background") ? 1 : 0);
                var square = new Pixmap(pixels,pixels,Pixmap.Format.RGBA8888);
                square.setColor(Color.WHITE); square.fill();
                PixmapIO.writePNG(Gdx.files.absolute(dir.resolve("star"+(density==2 ? "@2x" : "")+".png").toString()),square);
                square.dispose();
                if (!mode.equals("old-default-background")) Files.copy(rowColours.resolve("menu-button-background.png"),
                        dir.resolve("menu-button-background.png"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            for (String mode : List.of("scale", "crop")) {
                Path dir = Files.createDirectories(output.resolve("fixtures/composition-" + mode));
                Files.writeString(dir.resolve("skin.ini"), "[General]\nVersion: " + (mode.equals("scale") ? "2.2" : "1") + "\n");
                int density = mode.equals("scale") ? 2 : 1;
                compositionImage(dir, "menu-button-background", 800, 160, density, Color.WHITE);
                compositionImage(dir, "mode-taiko-small", 320, 240, density, Color.BLUE);
                compositionImage(dir, "ranking-A-small", 320, 240, density, new Color(0,1,0,128/255f));
                compositionImage(dir, "star", 40, 120, density, new Color(1,0,0,128/255f));
            }
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
                        "phase2-no-rating", "phase2-low-rating", "phase2-tenth-star", "phase2-five-stars", "phase2-fractional",
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
                        "phase3-group-toggle", "phase3-group-close",
                        "phase3-focus-set", "phase3-focus-confirm", "phase3-focus-group", "phase3-focus-group-toggle", "phase3-focus-page",
                        "phase3-chrome-full", "phase3-chrome-cookie", "phase3-menu-group", "phase3-menu-sort", "phase3-large-library", "phase3-transitions", "phase3-fallback", "phase3-modern"))
                    scenes.add(new Scene(size[0],size[1],size[2],name));
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("empty", "single", "three", "many", "SS", "S", "A", "B", "C", "D",
                        "numbers", "numbers-bottom", "sibling", "no-score", "group", "search", "long", "unicode", "fallback", "bundled-fallback",
                        "high-only", "scroll-top", "scroll-middle", "scroll-bottom", "selected", "wheel-hover", "transitions", "resize", "oversized-score",
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
                        "transparent", "asymmetric", "asymmetric-high", "oversized-hover", "mismatched-high", "tiny-chrome", "normal-bundled", "v1-default-mods", "v1-custom-mods", "upper-chrome", "upper-chrome-high"))
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
            for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2}})
                for (String name : List.of("repair-empty", "repair-single", "repair-low-fps"))
                    scenes.add(new Scene(size[0], size[1], size[2], name));
            String phase = System.getProperty("osujava.songSelectPhase", "all");
            if (phase.equals("case")) {
                String name = System.getProperty("osujava.songSelectCase", "greylooks-initial");
                if (scenes.stream().noneMatch(scene -> scene.name.equals(name))) throw new IllegalArgumentException("Unknown scene: " + name);
                scenes.clear();
                scenes.add(new Scene(Integer.getInteger("osujava.songSelectWidth", 1280),
                        Integer.getInteger("osujava.songSelectHeight", 720),
                        Integer.getInteger("osujava.songSelectDensity", 1), name));
            } else if (phase.equals("parity-visual")) {
                scenes.clear();
                for (int[] size : new int[][]{{1365,768,1},{1366,768,1},{1367,768,1},{1152,768,1},{1153,768,1},{1280,720,2}})
                    for (String name : List.of("overlap", "transparent-back", "odd-hd", "short-top", "animated-back"))
                        scenes.add(new Scene(size[0],size[1],size[2],"parity-"+name));
            } else if (phase.equals("audit")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1280,800,1},{1024,768,1},{1280,720,2}})
                    for (String name : List.of("repair-empty", "repair-single", "greylooks-expanded-many-last",
                            "greylooks-collapse-many", "greylooks-scroll-reverse", "phase2-long-english", "phase2-japanese",
                            "phase2-missing-thumbnail", "phase3-search-none", "phase4-empty", "phase4-single", "phase4-many",
                            "phase4-wheel-hover", "phase4-oversized-score", "phase4-resize", "phasechrome-giant",
                            "phasechrome-transparent", "phase5a-state-save-reload"))
                        scenes.add(new Scene(size[0],size[1],size[2],name));
            } else if (phase.equals("configured")) {
                scenes.clear();
                scenes.add(new Scene(Integer.getInteger("osujava.songSelectWidth", 1280),
                        Integer.getInteger("osujava.songSelectHeight", 720),
                        Integer.getInteger("osujava.songSelectDensity", 1), "configured"));
            } else if (phase.equals("star-contracts")) {
                var cases = Set.of("phase2-no-rating", "phase2-low-rating", "phase2-tenth-star", "phase2-high-rating");
                scenes.removeIf(scene -> !cases.contains(scene.name));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024,768,1,scene.name));
            } else if (phase.equals("star-animation")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String mode : List.of("scale", "crop", "old-default-background"))
                        scenes.add(new Scene(size[0],size[1],size[2],"star-animation-" + mode));
            } else if (phase.equals("search-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String mode : List.of("plain", "group"))
                        scenes.add(new Scene(size[0],size[1],size[2],"search-" + mode));
            } else if (phase.equals("composition-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String mode : List.of("scale", "crop"))
                        scenes.add(new Scene(size[0],size[1],size[2],"composition-" + mode));
            } else if (phase.equals("foreground-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String mode : List.of("scale", "crop"))
                        scenes.add(new Scene(size[0],size[1],size[2],"foreground-" + mode));
            } else if (phase.equals("lifecycle-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String name : List.of("group", "reentry"))
                        scenes.add(new Scene(size[0],size[1],size[2],"lifecycle-" + name));
            } else if (phase.equals("row-colour-contracts") || phase.equals("row-colour-animation")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String mode : List.of("sprite", "procedural"))
                        scenes.add(new Scene(size[0],size[1],size[2],"row-colours-" + mode));
            } else if (phase.equals("wheel-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (int fps : new int[]{30, 60, 144})
                        scenes.add(new Scene(size[0],size[1],size[2],"wheel-" + fps));
            } else if (phase.equals("keyboard-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (int fps : new int[]{30, 60, 144})
                        scenes.add(new Scene(size[0],size[1],size[2],"keyboard-" + fps));
            } else if (phase.equals("pointer-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String name : List.of("threshold", "right", "context", "chord"))
                        scenes.add(new Scene(size[0],size[1],size[2],"pointer-" + name));
            } else if (phase.equals("activation-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String name : List.of("left", "right", "middle"))
                        scenes.add(new Scene(size[0],size[1],size[2],"activation-" + name));
            } else if (phase.equals("drag-contracts")) {
                scenes.clear();
                for (int[] size : new int[][]{{1280,720,1},{1920,1080,1},{1280,720,2},{1024,768,1}})
                    for (String name : List.of("flick", "pause", "reverse", "cancel"))
                        scenes.add(new Scene(size[0],size[1],size[2],"drag-" + name));
            } else if (phase.equals("row-motion-contracts")) {
                var cases = Set.of("greylooks-hover", "greylooks-fast-scroll", "greylooks-scroll-reverse",
                        "greylooks-expanded-many-first", "greylooks-expanded-many-last", "greylooks-collapse-many",
                        "greylooks-first-item", "greylooks-last-item");
                scenes.removeIf(scene -> !cases.contains(scene.name));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024,768,1,scene.name));
            } else if (phase.equals("navigation-contracts")) {
                scenes.removeIf(scene -> !scene.name.startsWith("phase3-focus-")
                        && !scene.name.equals("phase3-group-toggle"));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024,768,1,scene.name));
            } else if (phase.equals("browser-contracts")) {
                var cases = Set.of("phase3-group-toggle", "phase3-group-close", "phase3-group-artist",
                        "phase3-group-creator", "phase3-group-bpm", "phase3-group-length", "phase3-search-none");
                scenes.removeIf(scene -> !cases.contains(scene.name));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024,768,1,scene.name));
            } else if (phase.equals("skin-contracts")) {
                var cases = Set.of("phase5a-assets-normal", "phase5a-assets-normal-bundled",
                        "phase5a-assets-v1-default-mods", "phase5a-assets-v1-custom-mods",
                        "phase5a-assets-transparent", "phasechrome-bundled");
                scenes.removeIf(scene -> !cases.contains(scene.name));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024,768,1,scene.name));
            } else if (phase.equals("selection-chrome")) {
                scenes.removeIf(scene -> !scene.name.startsWith("phase5a-assets-upper-chrome")
                        && !scene.name.equals("phase5a-assets-composite") && !scene.name.equals("phase5a-current-mode-view"));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024,768,1,scene.name));
            } else if (phase.equals("thumbnail-mode")) {
                var cases = Set.of("phase25-thumbnail-wide", "phase25-thumbnail-tall", "phase25-thumbnail-missing",
                        "phase4-selected", "phase5a-current-mode-view", "phase5a-assets-transparent", "phasechrome-bundled");
                scenes.removeIf(scene -> !cases.contains(scene.name));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024, 768, 1, scene.name));
            } else if (phase.equals("foundation")) {
                var cases = Set.of("greylooks-initial", "greylooks-hover", "greylooks-fast-scroll",
                        "greylooks-first-item", "greylooks-last-item", "greylooks-difficulty-selected",
                        "greylooks-random", "phase3-search-unicode", "phase5a-current-back-hover",
                        "phase5a-current-mode-view", "phase5a-current-mods-view", "phase5a-current-options-hover",
                        "old", "missing", "tiny", "broken", "unusual", "high-only", "phasechrome-giant");
                scenes.removeIf(scene -> !cases.contains(scene.name));
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1024, 768, 1, scene.name));
            } else if (phase.equals("repair")) {
                // The desktop launcher starts at 1100x720; include that aspect ratio too.
                for (var scene : List.copyOf(scenes)) if (scene.width == 1280 && scene.density == 1)
                    scenes.add(new Scene(1100, 720, 1, scene.name));
                var cases = Set.of("repair-empty", "repair-single", "repair-low-fps", "greylooks-initial",
                        "greylooks-hover", "greylooks-expanded-many-last", "greylooks-scroll-reverse",
                        "greylooks-large-library", "phasechrome-current", "phasechrome-bundled",
                        "phasechrome-giant", "phasechrome-giant-high", "phasechrome-transparent",
                        "missing", "high-only", "tiny", "phase2-five-stars", "phase2-thumbnail-fade",
                        "phase2-long-english", "phase3-transitions");
                scenes.removeIf(scene -> !cases.contains(scene.name));
            } else if (phase.equals("redevelopment")) {
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
                "transparent", "asymmetric", "asymmetric-high", "oversized-hover", "mismatched-high", "tiny-chrome", "normal-bundled", "v1-default-mods", "v1-custom-mods", "upper-chrome", "upper-chrome-high")) {
            Path dir = Files.createDirectories(output.resolve("fixtures/toolbox-" + name));
            Files.writeString(dir.resolve("skin.ini"),"[General]\nVersion: " + (name.startsWith("v1-") ? "1" : "2.5") + "\n");
            if (name.equals("missing")) continue;
            for (var action : SongSelectSkinAssets.Selection.values()) for (var image : List.of(action.normal,action.hover)) {
                if (name.startsWith("normal") && image == action.hover) continue;
                if (name.equals("v1-default-mods") && action == SongSelectSkinAssets.Selection.MODS) continue;
                int density = name.endsWith("high") ? 2 : 1;
                Path file = dir.resolve(image.basename + (density == 2 ? "@2x" : "") + ".png");
                if (name.equals("malformed")) { Files.writeString(file,"not a PNG"); continue; }
                int width = (int)action.logicalWidth*density, height = 90*density;
                if (name.equals("composite") && image == SongSelectSkinAssets.Image.MODE) { width=1150; height=540; }
                if (name.startsWith("upper-chrome") && image == SongSelectSkinAssets.Image.MODE) { width=1150*density; height=768*density; }
                if (name.startsWith("upper-chrome") && image == SongSelectSkinAssets.Image.MODE_OVER) { width=220*density; height=90*density; }
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
                if (name.equals("composite") && image == SongSelectSkinAssets.Image.MODE) {
                    p.setColor(Color.GREEN); p.fillRectangle(30, 130, 40, 40);
                    // Deliberately cross the carousel: cards must not erase foreground chrome.
                    p.fillRectangle(650, 290, 80, 40);
                }
                if (name.startsWith("upper-chrome")) {
                    if (image == SongSelectSkinAssets.Image.MODE) {
                        p.setColor(Color.GREEN); p.fillRectangle(0, 0, 450*density, 80*density);
                    } else if (image == SongSelectSkinAssets.Image.MODE_OVER) {
                        p.setColor(Color.RED); p.fillRectangle(110*density, 30*density, 30*density, 30*density);
                    }
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
            boolean missing = name.equals("phasechrome-missing");
            if (screen.renderedChromeProcedural(image) != missing || (asset == null) != missing)
                throw new AssertionError("Chrome rendered the wrong branch: " + name + " " + image);
            if (name.equals("phasechrome-custom")) {
                var local = new SkinAssetResolver(customSkin).resolve(image.basename);
                if (local.isPresent() && (!asset.file().path().equals(local.get().path()) || asset.file().fallback()))
                    throw new AssertionError("Custom native chrome did not win: " + asset.file());
                if (local.isEmpty() && (asset == null || !asset.file().fallback()))
                    throw new AssertionError("Missing custom chrome did not resolve its fallback");
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
        int[] display = {scene.width, scene.height};
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a) -> switch(m.getName()) {
            case "getWidth" -> display[0]; case "getHeight" -> display[1];
            case "getBackBufferWidth" -> display[0] * scene.density; case "getBackBufferHeight" -> display[1] * scene.density;
            default -> m.invoke(actualGraphics,a);
        });
        InputProcessor[] processor = {null};
        boolean[] clicked = {false};
        boolean[] pressed = {false};
        boolean[] rightClicked = {false}, rightPressed = {false};
        boolean[] middlePressed = {false};
        Set<Integer> heldKeys = new HashSet<>();
        UiLayout layout = UiLayout.fromPixels(scene.width,scene.height);
        int[] pointer = {40,scene.height / 2};
        if (scene.name.equals("greylooks-random-hover") || scene.name.equals("phasechrome-current-hover")) { pointer[0] = Math.round(344 * layout.scale()); pointer[1] = scene.height - Math.round(19 * layout.scale()); }
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a) -> switch(m.getName()) {
            case "setInputProcessor" -> { processor[0] = (InputProcessor)a[0]; yield null; }
            case "getInputProcessor" -> processor[0];
            case "getX" -> pointer[0]; case "getY" -> pointer[1];
            case "isKeyPressed" -> heldKeys.contains((int)a[0]);
            case "isButtonJustPressed" -> (int)a[0] == Input.Buttons.LEFT ? clicked[0] : (int)a[0] == Input.Buttons.RIGHT && rightClicked[0];
            case "isButtonPressed" -> (int)a[0] == Input.Buttons.LEFT ? pressed[0]
                    : (int)a[0] == Input.Buttons.RIGHT ? rightPressed[0] : (int)a[0] == Input.Buttons.MIDDLE && middlePressed[0];
            default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
        var library = new BeatmapLibrary();
        int setCount = (scene.name.equals("greylooks-large-library") || scene.name.startsWith("phase25-large-library-modern") || scene.name.equals("phase3-large-library") || scene.name.startsWith("phase4-large") || scene.name.startsWith("phase5a-state-large")) ? 1000 : 7;
        if (scene.name.equals("phase3-focus-page") || scene.name.startsWith("lifecycle-") || scene.name.startsWith("keyboard-")) setCount = 24;
        if (scene.name.equals("repair-empty")) setCount = 0;
        if (scene.name.equals("repair-single")) setCount = 1;
        boolean phase2 = scene.name.startsWith("phase2");
        var ratings = new IdentityHashMap<BeatmapDifficulty, OptionalDouble>();
        for (int i = 0; i < setCount; i++) {
            String title = setCount > 7 ? String.format(Locale.ROOT,"Local song %03d",i) : "Local song " + i;
            if (Boolean.getBoolean("osujava.selectionProfileLongText"))
                title += " 夜空の彼方への冒険 — The Never Ending Journey Across the Constellations".repeat(5);
            if (scene.name.equals("phase2-long-set") && i == 2)
                title = "Local song 2 — A Very Long English Title with Unicode 星の旅人 that extends beyond the row";
            String mapper = "Harness", version = "Difficulty ";
            String artist = "Local artist";
            boolean browserScene = scene.name.startsWith("phase3") || scene.name.startsWith("phase4") || scene.name.startsWith("lifecycle-") || scene.name.startsWith("search-");
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
            int difficultyCount = (scene.name.equals("repair-single") || scene.name.equals("greylooks-expanded-single") || scene.name.equals("phase2-single")) ? 1
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
                    case "phase2-tenth-star" -> 9.25;
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
            case "configured" -> customSkin == null ? SkinAssetResolver.withBundledDefault(null, null)
                    : SkinAssetResolver.withBundledDefault(customSkin, null);
            case "phasechrome-current", "phasechrome-current-hover" -> SkinAssetResolver.withBundledDefault(greylooks, output.resolve("fixtures/latest"));
            case "phasechrome-missing" -> new SkinAssetResolver(output.resolve("fixtures/row-only"));
            case "phasechrome-fallback" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), greylooks);
            case "repair-empty", "repair-single", "repair-low-fps", "phasechrome-bundled" -> SkinAssetResolver.withBundledDefault(output.resolve("fixtures/empty"), null);
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
        if (scene.name.startsWith("search-")) resolver = SkinAssetResolver.withBundledDefault(null,null);
        if (scene.name.startsWith("phase5a")) resolver = toolboxResolver(scene.name,greylooks);
        if (scene.name.equals("star-animation-old-default-background"))
            resolver = SkinAssetResolver.withBundledDefault(output.resolve("fixtures/"+scene.name),null);
        if (scene.name.startsWith("parity-"))
            resolver = SkinAssetResolver.withBundledDefault(output.resolve("fixtures/"+scene.name),null);
        if (scene.name.equals("phase4-oversized-score")) {
            try {
                Path dir = Files.createDirectories(output.resolve("fixtures/oversized-score"));
                compositionImage(dir, "menu-button-background", 2000, 800, scene.density, Color.WHITE);
                resolver = SkinAssetResolver.withBundledDefault(dir, null);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        var assets = scene.name.startsWith("phasechrome-current") || scene.name.equals("phasechrome-custom") ? null : new SongSelectSkinAssets(resolver);
        String preferredSet = scene.name.equals("greylooks-first-item") || scene.name.startsWith("keyboard-") ? "set0"
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
            screen.legacyThumbnailPreview(scene.name.equals("phase25-legacy-b") || scene.name.startsWith("foreground-"));
            screen.resize(scene.width,scene.height);
            fb.begin();
            if (scene.name.equals("configured") || scene.name.startsWith("parity-")) {
                captureConfigured(screen, scene, fb, pointer, processor[0], assets);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("phase3")) configureBrowserScene(screen, scene.name, processor[0], pointer, clicked, layout, scene.height);
            for (int frame = 0; frame < 40; frame++) screen.render(1f / 60);
            if ((scene.name.equals("phase2-hover") || scene.name.equals("phase25-hover"))) {
                pointerRow(screen,carousel(screen).rows().stream().filter(r -> r.entry.difficultyIndex() == 1).findFirst().orElseThrow().entry.setIndex(),2,pointer,layout,scene.height);
                for (int frame = 0; frame < 60; frame++) screen.render(1f / 60);
            }
            if (scene.name.startsWith("phase3-focus-"))
                exerciseKeyboardFocus(screen, scene.name, processor[0], layout);
            if (scene.name.equals("phase3-group-toggle") || scene.name.equals("phase3-group-close"))
                exerciseGroupCards(screen, scene.name, pointer, clicked, pressed, layout, scene.height);
            if (scene.name.startsWith("phase4")) configureScores(screen, scene.name, processor[0], pointer, clicked, layout, scene.height);
            String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + scene.name;
            if (scene.name.startsWith("search-")) {
                exerciseSearch(screen, scene, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("composition-")) {
                exerciseComposition(screen, scene, assets, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("foreground-")) {
                exerciseForeground(screen, scene, assets, processor[0], layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("star-animation-")) {
                exerciseStarAnimation(screen, scene, assets, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("row-colours-")) {
                if (System.getProperty("osujava.songSelectPhase", "").equals("row-colour-animation"))
                    exerciseRowColourAnimation(screen, assets, layout, fb, name);
                else exerciseRowColours(screen, assets, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("lifecycle-")) {
                exerciseLifecycle(screen, scene, processor[0], pointer, clicked, pressed, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("drag-")) {
                exerciseDrag(screen, scene, processor[0], pointer, clicked, pressed, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("pointer-")) {
                exercisePointer(screen, scene, pointer, clicked, pressed, rightClicked, rightPressed, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("activation-")) {
                exerciseActivation(screen, scene, pointer, clicked, pressed, rightPressed, middlePressed, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("wheel-")) {
                exerciseWheel(screen, scene, processor[0], pointer, heldKeys, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.startsWith("keyboard-")) {
                exerciseHeldKeyboard(screen, scene, processor[0], heldKeys, layout, fb, name);
                fb.end(); advanceScene(); return;
            }
            if (scene.name.equals("repair-empty") || scene.name.equals("repair-single")) {
                // Genuine empty/one-difficulty libraries, not a filtered seven-Set fixture.
                if (carousel(screen).rows().size() != setCount) throw new AssertionError("Wrong small-library fixture");
                for (int key : new int[]{Input.Keys.UP, Input.Keys.DOWN, Input.Keys.PAGE_UP, Input.Keys.PAGE_DOWN, Input.Keys.F2})
                    tapKey(processor[0], key);
                pointer[0] = Math.round((layout.width() - 100) * layout.scale());
                pointer[1] = scene.height / 2;
                processor[0].scrolled(0, 4);
                for (int frame = 0; frame < 20; frame++) screen.render(.1f);
                assertRenderedBounds(screen, layout);
                assertScrollSettled(screen, name);
                capture(fb, name);
                tapKey(processor[0], Input.Keys.ESCAPE);
                screen.render(.3f);
                if (destination[0] == null) throw new AssertionError("Back did not leave small Library");
                fb.end(); advanceScene(); return;
            }
            if (scene.name.equals("repair-low-fps")) {
                tapKey(processor[0], Input.Keys.DOWN);
                for (int frame = 0; frame < 20; frame++) {
                    screen.render(.1f); assertRenderedBounds(screen, layout); transitionFrames++;
                    if (frame == 0 || frame == 2 || frame == 19) capture(fb, name + "-frame-" + frame);
                }
            }
            if (scene.name.startsWith("phase5a")) {
                configureToolbox(screen,scene,processor[0],pointer,clicked,pressed,layout,localScores,library);
                if (scene.name.equals("phase5a-state-transitions")) toolboxTransitions(screen,processor[0],fb,name,layout);
                assertToolbox(screen,assets,scene.name,layout);
                capture(fb,name);
                if (scene.name.contains("large")) profileToolbox(screen,processor[0],name,scene.name);
                if (toolboxState(screen).open()) tapKey(processor[0], Input.Keys.ESCAPE);
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
                    ((Map<?,?>)screenField(screen,"rowForeground")).clear();
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
                    tapPointer(screen, clicked, pressed);
                    if (pending(screen) || !browser(screen).selectedSet().id().equals("set2"))
                        throw new AssertionError("Set click did not select without playing: " + name);
                }
                case "greylooks-difficulty-selected" -> {
                    pointerRow(screen,3,2,pointer,layout,scene.height);
                    tapPointer(screen, clicked, pressed);
                    if (pending(screen)) throw new AssertionError("Unselected difficulty played: " + name);
                }
                case "greylooks-hover" -> pointerRow(screen,3,2,pointer,layout,scene.height);
                case "greylooks-after-wheel" -> {
                    pointerRow(screen,3,1,pointer,layout,scene.height);
                    var before = carousel(screen).rows();
                    if (!processor[0].scrolled(0,1)) throw new AssertionError("Wheel lost: " + name);
                    if (carousel(screen).rows() != before) throw new AssertionError("Wheel rebuilt selection: " + name);
                }
                case "greylooks-random" -> tapKey(processor[0], Input.Keys.F2);
                case "greylooks-expanded-many-first", "greylooks-expanded-many-last", "greylooks-expanded-single" -> {
                    pointerRow(screen,3,-1,pointer,layout,scene.height);
                    tapPointer(screen, clicked, pressed);
                    if (scene.name.endsWith("last")) for (int i = 0; i < 15; i++) tapKey(processor[0], Input.Keys.DOWN);
                    long children = carousel(screen).rows().stream().filter(r -> r.entry.setIndex() == 3 && r.entry.difficultyIndex() >= 0).count();
                    if (children != (scene.name.endsWith("single") ? 1 : 16)) throw new AssertionError("Expansion input missed: " + name);
                    pointer[0] = 40;
                }
                case "greylooks-collapse-many" -> { tapKey(processor[0], Input.Keys.RIGHT); pointer[0] = 40; }
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
                    tapKey(processor[0], stage % 2 == 0 ? Input.Keys.DOWN : Input.Keys.UP);
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
            if (scene.name.startsWith("phase4-")) assertScoreClipping(screen, layout, fb);
            if (scene.name.equals("phase4-resize")) exerciseScoreResize(screen, scene, display, fb);
            if (scene.name.startsWith("phase3-focus-")) {
                // The generic pointer checks below target the playable row. Focus deliberately
                // leaves that row behind, so explicitly restore selection before those checks.
                var browser = (SongBrowserModel) screenField(screen, "browser");
                screen.previewSelection(browser.librarySets().indexOf(browser.selectedSet()),
                        browser.selectedSet().difficulties().indexOf(browser.selectedDifficulty()));
            }
            if (scene.name.startsWith("phase3") || scene.name.startsWith("phase4")) {
                screen.browserSearch("",false);
                closeBrowserMenu(screen);
                screen.browserMode(SongBrowserModel.Sort.TITLE,SongBrowserModel.Group.NONE);
                for (int frame=0;frame<90;frame++)screen.render(1f/60);
            }
            assertRenderedBounds(screen,layout);
            // Captures above keep their original timestamps. Native wheel inertia can exceed one second.
            for (int frame = 0; frame < 180 && Math.abs(carousel(screen).scrollTarget() - carousel(screen).scrollOffset()) > 1; frame++) {
                screen.render(1f / 60); assertRenderedBounds(screen, layout);
            }
            assertScrollSettled(screen,name);
            if (setCount > 7) {
                if (scene.name.equals("phase3-large-library")) profileBrowser(screen,name);
                if (scene.name.startsWith("phase4")) profileScores(screen, localScores, name);
                profileMotion(carousel(screen),name);
                profileRender(screen, name);
            }
            // Wheel scenes intentionally leave selection behind. A real difficulty change restores it.
            if (scene.name.contains("scroll") || setCount > 7) {
                tapKey(processor[0], Input.Keys.DOWN); pointer[0] = 40;
                for (int frame = 0; frame < 90; frame++) screen.render(1f/60);
            }
            // Position the pointer on the current selected row for the existing input smoke checks.
            var model = carousel(screen);
            var selectedRow = model.rows().stream().max(Comparator.comparingDouble(r -> r.selectedAmount)).orElseThrow();
            pointerRow(screen,selectedRow.entry.setIndex(),selectedRow.entry.difficultyIndex(),pointer,layout,scene.height);
            // Navigation and search still work even with malformed/missing visual assets.
            if (!processor[0].scrolled(0,1)) throw new AssertionError("Wheel lost: " + name);
            for (int key : new int[]{Input.Keys.UP,Input.Keys.DOWN,Input.Keys.PAGE_UP,Input.Keys.PAGE_DOWN,Input.Keys.LEFT,Input.Keys.RIGHT,Input.Keys.F2})
                if (!tapKey(processor[0], key)) throw new AssertionError("Key lost: " + name + " / " + key);
            screen.render(.05f);
            pointer[0] = Math.round((layout.width() - 100) * layout.scale());
            pointer[1] = Math.round(45 * layout.scale());
            clicked[0] = true; screen.render(0); clicked[0] = false;
            for (char c : (setCount > 7 ? "Local song 002" : "Local song 2").toCharArray()) if (!processor[0].keyTyped(c)) throw new AssertionError("Search lost: " + name);
            tapKey(processor[0], Input.Keys.ENTER);
            var randomBounds = toolboxLayout(screen).control(SongSelectSkinAssets.Selection.RANDOM).interaction();
            if (randomBounds.empty()) tapKey(processor[0], Input.Keys.F2);
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
                tapKey(processor[0], Input.Keys.DOWN);
                pointer[0] = Math.round((layout.width()-20)*layout.scale()); pointer[1] = scene.height-Math.round(30*layout.scale());
            } else pointerRow(screen,selectedRow.entry.setIndex(),selectedRow.entry.difficultyIndex(),pointer,layout,scene.height);
            tapPointer(screen, clicked, pressed);
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
            case "normal-bundled", "v1-default-mods", "v1-custom-mods" ->
                    SkinAssetResolver.withBundledDefault(output.resolve("fixtures/toolbox-" + fixture),null);
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
    private void exerciseLifecycle(SongSelectScreen screen, Scene scene, InputProcessor input,
            int[] pointer, boolean[] clicked, boolean[] pressed, UiLayout layout, FrameBuffer fb, String name) {
        pointer[0] = Math.round((layout.width() - 30) * layout.scale()); pointer[1] = scene.height / 2;
        var model = carousel(screen); var browser = browser(screen); var selection = browser.selection();
        if (scene.name.equals("lifecycle-group")) {
            screen.browserMode(SongBrowserModel.Sort.TITLE, SongBrowserModel.Group.ARTIST);
            for (int frame = 0; frame < 60; frame++) screen.render(1f / 60);
            var groups = browser.rows().stream().filter(r -> r.group() && !r.expanded).toList();
            for (int stage = 0; stage < groups.size(); stage++) {
                var group = groups.get(stage); clickGroupCard(screen, group.key, pointer, clicked, pressed, layout, scene.height);
                var parent = model.rows().stream().filter(r -> r.entry.key().equals(group.key)).findFirst().orElseThrow();
                var child = model.rows().stream().filter(r -> browser.row(r.entry.key()).parent == group).findFirst().orElseThrow();
                if (Math.abs(parent.motionY - child.motionY) > .001 || Math.abs(parent.motionX - child.motionX) > .001)
                    throw new AssertionError("Group opening did not seed its first child");
                capture(fb, name + "-stage-" + stage + "-frame-0");
                for (int frame = 1; frame <= 30; frame++) {
                    screen.render(1f / 60); transitionFrames++; assertRenderedBounds(screen, layout);
                    if (frame == 1 || frame == 4 || frame == 10 || frame == 30)
                        capture(fb, name + "-stage-" + stage + "-frame-" + frame);
                }
            }
        } else {
            var identities = List.copyOf(model.allRows());
            var seen = new HashSet<String>(); var retired = new HashSet<String>(); var returned = new HashSet<String>();
            for (var row : identities) if (row.resident) seen.add(row.entry.key());
            for (int frame = 0; frame < 240; frame++) {
                if (frame < 8 || frame >= 120 && frame < 128) input.scrolled(0, frame < 120 ? 3 : -3);
                screen.render(frame % 5 == 0 ? 1f / 30 : 1f / 60); transitionFrames++;
                assertRenderedBounds(screen, layout);
                for (int i = 0; i < identities.size(); i++) {
                    var row = model.allRows().get(i);
                    if (row != identities.get(i)) throw new AssertionError("Scroll replaced persistent rows");
                    if (row.resident) { if (retired.contains(row.entry.key())) returned.add(row.entry.key()); seen.add(row.entry.key()); }
                    else if (seen.contains(row.entry.key())) retired.add(row.entry.key());
                }
                if (frame == 0 || frame == 7 || frame == 30 || frame == 119 || frame == 127 || frame == 150 || frame == 239)
                    capture(fb, name + "-frame-" + frame);
            }
            if (retired.isEmpty() || returned.isEmpty()) throw new AssertionError("Fixture did not retire and reenter rows");
            assertScrollSettled(screen, name);
        }
        if (!selection.equals(browser.selection()) || pending(screen)) throw new AssertionError("Lifecycle changed playable selection");
    }

    private void exerciseHeldKeyboard(SongSelectScreen screen, Scene scene, InputProcessor input,
            Set<Integer> held, UiLayout layout, FrameBuffer fb, String name) {
        int fps = Integer.parseInt(scene.name.substring("keyboard-".length()));
        var browser = browser(screen);
        var visible = browser.rows().stream().filter(SongBrowserModel.Row::visible).map(r -> r.key).toList();
        int start = visible.indexOf(browser.selectedKey());
        Set<Integer> pulses = switch (fps) {
            case 30 -> Set.of(7,10,13,16,19,22,25,28);
            case 60 -> Set.of(14,20,26,32,38,44,50,56);
            case 144 -> Set.of(32,47,61,75,90,104,119,133);
            default -> throw new AssertionError("Unknown frame-rate fixture");
        };
        held.add(Input.Keys.DOWN); input.keyDown(Input.Keys.DOWN); screen.render(0);
        int moves = 1;
        for (int frame = 0; frame < fps; frame++) {
            if (pulses.contains(frame)) moves++;
            screen.render(1f / fps); transitionFrames++;
            String active = browser.focusKey() == null ? browser.selectedKey() : browser.focusKey();
            if (!visible.get(start + moves).equals(active)) throw new AssertionError("Repeat state/timing: " + name + " frame " + frame);
            if (pending(screen)) throw new AssertionError("Held Down played a row");
            assertRenderedBounds(screen, layout);
            if (frame == 0 || frame == pulses.stream().mapToInt(Integer::intValue).min().orElseThrow()
                    || frame == fps / 2 || frame == fps - 1) capture(fb, name + "-frame-" + frame);
        }
        String focus = browser.focusKey();
        if (focus == null) throw new AssertionError("Fixture did not reach a collapsed Set");
        held.remove(Input.Keys.DOWN); input.keyUp(Input.Keys.DOWN);
        for (int frame = 0; frame < 10; frame++) {
            screen.render(.05f); transitionFrames++;
            if (!focus.equals(browser.focusKey())) throw new AssertionError("Released key still repeats");
        }
        held.add(Input.Keys.ENTER); input.keyDown(Input.Keys.ENTER); screen.render(0);
        var selection = browser.selection();
        capture(fb, name + "-confirmed");
        for (int frame = 0; frame < 30; frame++) {
            screen.render(1f / 30); transitionFrames++; assertRenderedBounds(screen, layout);
            if (pending(screen) || !selection.equals(browser.selection())) throw new AssertionError("Held Enter repeated confirmation/play");
        }
        capture(fb, name + "-enter-held");
        held.remove(Input.Keys.ENTER); input.keyUp(Input.Keys.ENTER);
    }

    private void exerciseForeground(SongSelectScreen screen, Scene scene, SongSelectSkinAssets assets,
            InputProcessor input, UiLayout layout, FrameBuffer fb, String name) {
        boolean cropped = scene.name.endsWith("crop");
        var animation = new SongSelectForegroundAnimation();
        animation.update(3,false,1000,0);
        var pixels = new Pixmap(64,48,Pixmap.Format.RGBA8888);
        pixels.setColor(Color.WHITE); pixels.fill();
        var image = new Texture(pixels); pixels.dispose();
        int[] times = {0,100,200,300,500,1000,1150,1300};
        try {
            for (int time : times) {
                if (time==1150) animation.update(1,false,2000,0);
                animation.update(time>1000 ? 1 : 3,false,1000+time,0);
                var row = new SongSelectRow(0,time>1000 ? -1 : 0,null,false,true,100,300,600,72,0,1);
                var content = new SongSelectRowPresentation.Content("","","",null,
                        SongSelectRowPresentation.Stars.of(OptionalDouble.empty()),1);
                var geometry = SongSelectLayout.row(row,0,100,300,layout.width(),0,layout.height(),true,true,true,cropped);
                var snapshot = animation.snapshot();
                var item = new SongSelectRowRenderer.Presentation(row,content,true,dev.osujava.ruleset.osu.OsuGrade.A,
                        image,geometry,false,0,SongSelectStarAnimation.Snapshot.EMPTY,snapshot);
                var labelRow = new SongSelectRow(1,0,null,false,false,600,300,300,72,0,1);
                var labelContent = new SongSelectRowPresentation.Content("MMMM","MMMM","MMMM",null,
                        SongSelectRowPresentation.Stars.of(OptionalDouble.empty()),-1);
                var labels = new SongSelectRowRenderer.Presentation(labelRow,labelContent,false,null,null,
                        SongSelectLayout.row(labelRow,1,600,300,layout.width(),0,layout.height(),false,false,false,cropped),
                        false,0,SongSelectStarAnimation.Snapshot.EMPTY,snapshot);
                Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                ((SongSelectRowRenderer)screenField(screen,"rowRenderer")).draw(List.of(item),
                        new SongSelectRowRenderer.Style(layout.width(),assets,(Texture)screenField(screen,"rowFill"),Color.GREEN,Color.GREEN,true));
                ((SongSelectRowRenderer)screenField(screen,"rowRenderer")).draw(List.of(labels),
                        new SongSelectRowRenderer.Style(layout.width(),assets,(Texture)screenField(screen,"rowFill"),Color.GREEN,Color.GREEN,false));
                // Independent reference coordinates for H=72: offsets * 1.5, image dimensions * .9375.
                float cy = cropped ? 340.5f : 336, inset = cropped ? 15 : 5;
                float detailAlpha = time<=1000 ? Math.min(1,time/300f) : 1-(time-1000)/300f;
                int brightness = time<=1000 ? (int)(50+205*Math.min(1,time/300f)) : (int)(255-205*(time-1000)/300f);
                float thumbnailAlpha = Math.min(1,time/1000f);
                var captured=Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
                try {
                    assertForegroundRectangle(captured,layout,new SongSelectLayout.Rect(107.8f,295.546875f,106.875f,80.15625f),
                            new float[]{brightness*thumbnailAlpha,brightness*thumbnailAlpha,brightness*thumbnailAlpha},name+" thumbnail t="+time);
                    assertForegroundRectangle(captured,layout,new SongSelectLayout.Rect(100+(75+inset+1)*1.5f,cy+19.5f-4.5f,15,9),
                            new float[]{0,255*detailAlpha,0},name+" mode t="+time);
                    assertForegroundRectangle(captured,layout,new SongSelectLayout.Rect(100+(75+inset-1)*1.5f,cy-21-7.5f,22.5f,15),
                            new float[]{255*detailAlpha,255*detailAlpha,255*detailAlpha},name+" grade t="+time);
                    for (int label=0;label<3;label++) {
                        float centre=cy+new float[]{24,6,-10.5f}[label];
                        float alpha=label==2 ? detailAlpha : Math.min(1,time/200f);
                        float sx=captured.getWidth()/layout.width(), sy=captured.getHeight()/layout.height();
                        int maximum=0;
                        for (int y=(int)((centre-4)*sy);y<(centre+4)*sy;y++)
                            for (int x=(int)(615*sx);x<715*sx;x++) maximum=Math.max(maximum,captured.getPixel(x,y) >>> 16 & 255);
                        if (Math.abs(maximum-255*alpha)>3) throw new AssertionError(name+" label="+label+" t="+time
                                +" expected="+255*alpha+" actual="+maximum);
                    }
                } finally { captured.dispose(); }
                capture(fb,name+"-sprites-"+time+"ms"); transitionFrames++;
            }
        } finally { image.dispose(); }

        // Actual Screen: shared artwork must still produce different row brightness and per-row load fades.
        screen.previewSelection(3,0);
        for (int frame=0;frame<90;frame++) { screen.render(1f/60); transitionFrames++; }
        String selected=browser(screen).selectedKey();
        var before=foregroundPresentation(screen,selected);
        float expectedTitleY=before.row().height()/2+(cropped ? 19 : 16)*before.row().height()/48;
        if (Math.abs(before.geometry().text().titleY()-expectedTitleY)>.0001f)
            throw new AssertionError("Screen foreground style/provider: " + name);
        if (before.thumbnail()==null || before.foreground().thumbnailBrightness()!=255 || before.foreground().thumbnailOpacity()!=1)
            throw new AssertionError("Selected thumbnail did not finish loading: " + name);
        var collapsed=((List<?>)screenField(screen,"rowPresentations")).stream().map(SongSelectRowRenderer.Presentation.class::cast)
                .filter(p -> p.row().difficultyIndex()<0 && p.thumbnail()!=null).findFirst().orElseThrow();
        if (collapsed.thumbnail()!=before.thumbnail() || collapsed.foreground().thumbnailBrightness()!=50)
            throw new AssertionError("Shared artwork lost row-owned colour: " + name);
        tapKey(input,Input.Keys.RIGHT); screen.render(0);
        capture(fb,name+"-screen-collapse-0ms");
        for (int frame=1;frame<=60;frame++) {
            screen.render(1f/60); transitionFrames++;
            assertRenderedBounds(screen,layout);
            if (Set.of(6,18,30,60).contains(frame)) capture(fb,name+"-screen-collapse-"+frame+"f");
        }
        var after=foregroundPresentation(screen,selected);
        if (after.foreground().detailOpacity()!=0 || after.foreground().thumbnailBrightness()!=50
                || !after.content().detail().equals(before.content().detail()))
            throw new AssertionError("Collapsed representative lost its sprites: " + name);
        System.out.println("FOREGROUND PASS " + name);
    }

    private SongSelectRowRenderer.Presentation foregroundPresentation(SongSelectScreen screen, String key) {
        return ((List<?>)screenField(screen,"rowPresentations")).stream().map(SongSelectRowRenderer.Presentation.class::cast)
                .filter(p -> p.row().key().equals(key)).findFirst().orElseThrow();
    }

    private static void assertForegroundRectangle(Pixmap pixels, UiLayout layout, SongSelectLayout.Rect rect, float[] rgb, String context) {
        float sx=pixels.getWidth()/layout.width(), sy=pixels.getHeight()/layout.height();
        for (int iy=1;iy<=3;iy++) for (int ix=1;ix<=3;ix++) {
            int actual=pixels.getPixel((int)((rect.x()+rect.width()*ix/4)*sx),(int)((rect.y()+rect.height()*iy/4)*sy));
            for (int channel=0;channel<3;channel++) if (Math.abs((actual >>> (24-8*channel) & 255)-rgb[channel])>3)
                throw new AssertionError(context+" channel="+channel+" expected="+rgb[channel]+" actual="+(actual >>> (24-8*channel) & 255));
        }
    }

    private void exerciseSearch(SongSelectScreen screen, Scene scene, UiLayout layout, FrameBuffer fb, String name) {
        screen.browserMode(SongBrowserModel.Sort.TITLE,scene.name.endsWith("group") ? SongBrowserModel.Group.ARTIST : SongBrowserModel.Group.NONE);
        String[] queries={"difficulty=\"difficulty 2\"", "difficulty!=\"difficulty 1\" artist=aether", "ar>=5 cs=5",
                "bpm>=190 bpm<300 length>=271 drain<=450 mode=osu",
                "bpm=135 length=181 drain=180 mode=o difficulty=\"difficulty 2\"", "mode=mania", "difficulty=absent", ""};
        int[] expected={7,6,28,8,1,0,0,28};
        var browser=(SongBrowserModel)screenField(screen,"browser");
        for (int stage=0;stage<queries.length;stage++) {
            screen.browserSearch(queries[stage],true);
            for (int frame=0;frame<45;frame++) {
                screen.render(1f/60); transitionFrames++; assertRenderedBounds(screen,layout);
                var matched=browser.rows().stream().filter(r -> !r.group() && !r.excluded).toList();
                if (matched.size()!=expected[stage]) throw new AssertionError(name+" search count at "+stage+": "+matched.size());
                for (var item : ((List<?>)screenField(screen,"rowPresentations")).stream().map(SongSelectRowRenderer.Presentation.class::cast).toList()) {
                    var row=browser.row(item.row().key());
                    if (row.excluded) throw new AssertionError("Excluded search row was drawn");
                    if ((stage==0 || stage==4) && !row.group() && (item.row().difficultyIndex()!=1 || !item.content().detail().equals("Difficulty 2")))
                        throw new AssertionError("Search singleton lost its original difficulty index or label");
                }
                if (scene.name.endsWith("group") && browser.rows().stream().filter(r -> r.group()).mapToInt(r -> r.matchingChildren).sum()!=expected[stage])
                    throw new AssertionError("Group includes excluded difficulties");
                if (expected[stage]==0 && browser.selectedDifficulty()!=null) throw new AssertionError("Empty search exposed gameplay selection");
                if (frame==0 || frame==8 || frame==44) capture(fb,name+"-query-"+stage+"-frame-"+frame);
            }
        }
        System.out.println("SEARCH PASS "+name);
    }

    private static void compositionImage(Path dir, String name, int w, int h, int density, Color colour) {
        var pixels = new Pixmap(w*density,h*density,Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None); pixels.setColor(colour); pixels.fill();
        PixmapIO.writePNG(Gdx.files.absolute(dir.resolve(name+(density==2 ? "@2x" : "")+".png").toString()),pixels);
        pixels.dispose();
    }

    private void exerciseComposition(SongSelectScreen screen, Scene scene, SongSelectSkinAssets assets,
            UiLayout layout, FrameBuffer fb, String name) {
        boolean cropped = scene.name.endsWith("crop");
        var renderer = (SongSelectRowRenderer)screenField(screen,"rowRenderer");
        var style = new SongSelectRowRenderer.Style(layout.width(),assets,(Texture)screenField(screen,"rowFill"),Color.WHITE,Color.WHITE,false);
        var empty = new SongSelectRowPresentation.Content("","","",null,SongSelectRowPresentation.Stars.of(OptionalDouble.empty()),-1);
        var selected = new SongSelectRow(0,0,null,true,false,100,300,600,72,0,1);
        var later = new SongSelectRow(1,0,null,false,false,100,300,600,72,0,1);
        var group = new SongSelectRow(-1,-2,"",false,false,100,300,600,72,0,1,2,100,300,"group:test",false);
        // The viewport reservation deliberately excludes both the sample and the row body.
        var firstGeometry = SongSelectLayout.row(selected,0,100,300,layout.width(),450,500,false,false);
        var first = new SongSelectRowRenderer.Presentation(selected,empty,false,null,null,0,firstGeometry,false,0xff000080);
        for (var lastRow : List.of(later,group)) {
            var last = new SongSelectRowRenderer.Presentation(lastRow,lastRow.group() ? null : empty,false,null,null,0,
                    SongSelectLayout.row(lastRow,1,100,300,layout.width(),450,500,false,false),false,0x0000ff80);
            // Also prove the pass restores the caller's blend function after using ordinary alpha.
            batch.setBlendFunction(GL20.GL_SRC_ALPHA,GL20.GL_ONE);
            Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.draw(List.of(first,last),style);
            if (batch.getBlendDstFunc()!=GL20.GL_ONE) throw new AssertionError("Row blend state leaked");
            batch.setBlendFunction(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
            var pixels = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
            try {
                float a=128/255f;
                assertForegroundRectangle(pixels,layout,new SongSelectLayout.Rect(500,290,50,90),
                        new float[]{255*a*(1-a),0,255*a},name+" browser order / unclipped background");
            } finally { pixels.dispose(); }
            capture(fb,name+(lastRow.group() ? "-group-over-selected" : "-row-over-selected")); transitionFrames++;
        }
        var content = new SongSelectRowPresentation.Content("MMMMMMMM","MMMMMMMM","MMMMMMMM",null,empty.stars(),1);
        var narrow = new SongSelectRow(0,0,null,false,false,100,300,120,72,0,1);
        var geometry = SongSelectLayout.row(narrow,0,100,300,layout.width(),450,500,false,true,true,cropped);
        var stars = new SongSelectStarAnimation(cropped,40);
        stars.update(SongSelectRowPresentation.Stars.of(OptionalDouble.of(3.25)),true,0,16); stars.advance(1);
        for (boolean withStars : List.of(false,true)) {
            var item = new SongSelectRowRenderer.Presentation(narrow,content,false,dev.osujava.ruleset.osu.OsuGrade.A,
                    null,0,geometry,false,0,withStars ? stars.snapshot() : SongSelectStarAnimation.Snapshot.EMPTY);
            Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.draw(List.of(item),style);
            var pixels = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
            try {
                float a=128/255f;
                // Huge badges overlap the text, extend beyond row height/width, and use source alpha.
                assertForegroundRectangle(pixels,layout,new SongSelectLayout.Rect(240,390,80,20),
                        new float[]{0,255*a,255*(1-a)},name+" grade over mode outside body");
                if (!withStars) {
                    // Scan all three text bands, including glyph interiors, rather than sparse sample points.
                    float sx=fb.getWidth()/layout.width(), sy=fb.getHeight()/layout.height();
                    float[] expected={0,255*a,255*(1-a)};
                    for (int py=(int)(320*sy);py<370*sy;py++) for (int px=(int)(145*sx);px<220*sx;px++) {
                        int pixel=pixels.getPixel(px,py);
                        for (int channel=0;channel<3;channel++) if (Math.abs((pixel >>> (24-channel*8) & 255)-expected[channel])>3)
                            throw new AssertionError(name+" text escaped mode/grade occlusion at "+px+","+py);
                    }
                }
                assertForegroundRectangle(pixels,layout,new SongSelectLayout.Rect(240,220,80,10),
                        new float[]{0,255*a,0},name+" grade below body");
                if (withStars) {
                    // Background star pass then foreground pass, both after the grade.
                    float red = a+(30/255f*a)*(1-a);
                    float cx = (cropped ? 160 : 145)+11.25f, cy = cropped ? 313.5f : 309;
                    assertForegroundRectangle(pixels,layout,new SongSelectLayout.Rect(cx-2,cy-2,4,4),
                            new float[]{255*red,255*a*(1-red),255*(1-a)*(1-red)},name+" stars over badges");
                }
            } finally { pixels.dispose(); }
            capture(fb,name+(withStars ? "-stars-over-badges" : "-badges-over-text")); transitionFrames++;
        }
        System.out.println("COMPOSITION PIXELS PASS " + name);
    }

    private void exerciseStarAnimation(SongSelectScreen screen, Scene scene, SongSelectSkinAssets assets,
            UiLayout layout, FrameBuffer fb, String name) {
        boolean cropped = scene.name.endsWith("-crop");
        var actual = ((List<?>)screenField(screen,"rowPresentations")).stream().map(SongSelectRowRenderer.Presentation.class::cast)
                .filter(p -> !p.stars().glyphs().isEmpty()).findFirst().orElseThrow();
        if (actual.stars().cropped()!=cropped) throw new AssertionError("Screen star mode/provider: " + name);
        if ((int)assets.get(SongSelectSkinAssets.Image.STAR).logicalWidth()!=40) throw new AssertionError("Star density: " + name);
        var animation = new SongSelectStarAnimation(cropped,40);
        var rating = SongSelectRowPresentation.Stars.of(OptionalDouble.of(2.5));
        animation.update(rating,false,1000,0);
        var row = new SongSelectRow(0,0,null,false,false,100,300,600,72,0,1);
        var geometry = SongSelectLayout.row(row,0,row.x(),row.y(),layout.width(),0,layout.height(),false,false);
        var content = new SongSelectRowPresentation.Content("","","",null,
                SongSelectRowPresentation.Stars.of(OptionalDouble.empty()),-1);
        int[] times = {0,130,250,380,500,630,1000,1150};
        int checks=0;
        for (int stage=0;stage<times.length;stage++) {
            animation.advance(1000+times[stage]);
            var snapshot=animation.snapshot();
            var drawnRow = stage==times.length-1 ? new SongSelectRow(0,0,null,false,false,100,300,160,72,0,1) : row;
            var drawnGeometry = SongSelectLayout.row(drawnRow,0,100,300,layout.width(),0,layout.height(),false,false);
            var item = new SongSelectRowRenderer.Presentation(drawnRow,content,false,null,null,0,drawnGeometry,false,0,snapshot);
            Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            ((SongSelectRowRenderer)screenField(screen,"rowRenderer")).draw(List.of(item),
                    new SongSelectRowRenderer.Style(layout.width(),assets,(Texture)screenField(screen,"rowFill"),Color.WHITE,Color.WHITE,false));
            // A white 40-logical-pixel texture at row height 72 gives a 22.5-unit pitch.
            // Compare the entire band away from raster boundaries, not only the sprite centres.
            float left=100+geometry.text().textX(), cy=cropped ? 313.5f : 309f;
            float sx=fb.getWidth()/layout.width(), sy=fb.getHeight()/layout.height();
            var pixels=Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
            try {
                for (int py=(int)((cy-15)*sy);py<(cy+15)*sy;py++) for (int px=(int)((left-3)*sx);px<(left+228)*sx;px++) {
                    float x=(px+.5f)/sx, y=(py+.5f)/sy;
                    double alpha=0;
                    boolean edge=false;
                    for (int i=0;i<10;i++) {
                        float cx=left+(i+.5f)*22.5f;
                        float bg=cropped ? 22.5f : 7.875f;
                        var glyph=snapshot.glyphs().get(i);
                        float fw=cropped ? 22.5f*glyph.crop() : 22.5f*Math.abs(glyph.scale());
                        float fh=cropped ? 22.5f : fw;
                        float fx=cropped ? cx-11.25f : cx-fw/2;
                        var background=new SongSelectLayout.Rect(cx-bg/2,cy-bg/2,bg,bg);
                        var foreground=new SongSelectLayout.Rect(fx,cy-fh/2,fw,fh);
                        for (var bounds : List.of(background,foreground)) {
                            if (bounds.width()<=0 || bounds.height()<=0) continue;
                            if (x>=bounds.x()-1/sx && x<=bounds.x()+bounds.width()+1/sx
                                    && y>=bounds.y()-1/sy && y<=bounds.y()+bounds.height()+1/sy
                                    && (Math.abs(x-bounds.x())<1/sx || Math.abs(x-bounds.x()-bounds.width())<1/sx
                                    || Math.abs(y-bounds.y())<1/sy || Math.abs(y-bounds.y()-bounds.height())<1/sy)) edge=true;
                        }
                        if (background.contains(x,y)) alpha=30/255.0*snapshot.backgroundOpacity();
                        if (fw>0 && foreground.contains(x,y)) alpha=snapshot.foregroundOpacity()+alpha*(1-snapshot.foregroundOpacity());
                    }
                    if (edge) continue;
                    int pixel=pixels.getPixel(px,py);
                    for (int channel=0;channel<3;channel++) if (Math.abs((pixel >>> (24-channel*8) & 255)-alpha*255)>3)
                        throw new AssertionError("Star pixel: " + name + " t=" + times[stage] + " at=" + x + "," + y
                                + " expected=" + alpha*255 + " actual=" + (pixel >>> (24-channel*8) & 255));
                    checks++;
                }
            } finally { pixels.dispose(); }
            capture(fb,name+"-"+(stage==times.length-1 ? "narrow-unclipped-1150" : times[stage])+"ms");
            transitionFrames++;
        }
        System.out.println("STAR PIXELS PASS " + name + " samples=" + checks);
    }

    private void exerciseRowColourAnimation(SongSelectScreen screen, SongSelectSkinAssets assets,
            UiLayout layout, FrameBuffer fb, String name) {
        String[] labels = {"State 300ms", "Hover 1000ms", "Focus 50ms", "Blocked hover", "Flash interrupted by focus"};
        int[] times = {0,25,50,150,300,500,1000,1001};
        int[][] expected = {
                {0xffffffdc,0x26c7fff0,0x0096ecf0,0xffffffdc,0x26c7fff0},
                {0xe9f6fddd,0x25c5fef0,0x00b4f5f0,0xe9f6fddd,0x25c5fef0},
                {0xd4edfbdf,0x24c4fef0,0x00d2fff0,0xd4edfbdf,0x24c4fef0},
                {0x7fcaf5e6,0x20bffcf0,0x00d2fff0,0x7fcaf5e6,0x24c4fef0},
                {0x0096ecf0,0x1ab8f9f0,0x00d2fff0,0x0096ecf0,0x00d2fff0},
                {0x0096ecf0,0x13aef5f0,0x00d2fff0,0x0096ecf0,0x00d2fff0},
                {0x0096ecf0,0x0096ecf0,0x00d2fff0,0x0096ecf0,0x00d2fff0},
                {0x0096ecf0,0x0096ecf0,0x00d2fff0,0x0096ecf0,0x00d2fff0}
        };
        var animations = new SongSelectRowColourAnimation[labels.length];
        for (int i=0;i<animations.length;i++) {
            animations[i]=new SongSelectRowColourAnimation();
            animations[i].update(i==0 || i==3 ? 4 : 3,i==0 || i==3 ? 0xffffffdc : 0x0096ecf0,false,false,0,0);
        }
        for (int stage=0;stage<times.length;stage++) {
            var presentations = new ArrayList<SongSelectRowRenderer.Presentation>();
            for (int i=0;i<labels.length;i++) {
                animations[i].update(3,0x0096ecf0,i==2 || i==4 && times[stage]>=150,i==1 || i==3 || i==4,1000+times[stage],0);
                if (animations[i].rgba()!=expected[stage][i]) throw new AssertionError("Animation RGBA: " + name + " " + labels[i] + " at " + times[stage]);
                var row = new SongSelectRow(i,0,null,false,true,80,80+110*i,layout.width()-160,72,0,1);
                var content = new SongSelectRowPresentation.Content(labels[i],times[stage]+" ms","",null,
                        SongSelectRowPresentation.Stars.of(OptionalDouble.empty()),-1);
                presentations.add(new SongSelectRowRenderer.Presentation(row,content,false,null,null,0,
                        SongSelectLayout.row(row,i,row.x(),row.y(),layout.width(),0,layout.height(),false,false),false,animations[i].rgba()));
            }
            Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            ((SongSelectRowRenderer)screenField(screen,"rowRenderer")).draw(presentations,
                    new SongSelectRowRenderer.Style(layout.width(),assets,(Texture)screenField(screen,"rowFill"),Color.BLACK,Color.WHITE,false));
            var pixels = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
            try {
                for (int i=0;i<labels.length;i++) {
                    int pixel=pixels.getPixel(Math.round(700*fb.getWidth()/layout.width()),
                            Math.round((80+110*i+36)*fb.getHeight()/layout.height()));
                    for (int channel=0;channel<3;channel++) {
                        int actual=pixel >>> (24-channel*8) & 255;
                        double wanted=(expected[stage][i] >>> (24-channel*8) & 255)*(expected[stage][i] & 255)/255.0;
                        if (Math.abs(actual-wanted)>2) throw new AssertionError("Animated colour pixel: " + name + " " + labels[i]
                                + " at " + times[stage] + " channel=" + channel + " expected=" + wanted + " actual=" + actual);
                    }
                }
            } finally { pixels.dispose(); }
            transitionFrames++;
            capture(fb,name+"-animation-"+times[stage]+"ms");
        }
    }

    private void exerciseRowColours(SongSelectScreen screen, SongSelectSkinAssets assets,
            UiLayout layout, FrameBuffer fb, String name) {
        String[] labels = {"Selected", "Sibling", "Played", "Unplayed", "Group closed", "Group contains selection", "Group open"};
        int[][][] palette = {
                {{255,255,255,220},{0,150,236,240},{233,104,0,240},{235,73,153,240},{35,50,143,255},{35,90,193,255},{163,240,44,255}},
                {{255,255,255,220},{38,199,255,240},{255,150,38,240},{255,116,202,240},{75,92,191,255},{75,135,245,255},{213,255,85,255}},
                {{255,255,255,220},{0,210,255,240},{255,145,0,240},{255,102,214,240},{49,70,200,255},{49,126,255,255},{228,255,61,255}}
        };
        for (int stage=0;stage<3;stage++) {
            var presentations = new ArrayList<SongSelectRowRenderer.Presentation>();
            for (int i=0;i<labels.length;i++) {
                boolean group=i>=4;
                var row = new SongSelectRow(group ? -1 : i,group ? -2 : 0,group ? labels[i] : null,i==0,i==1,
                        80,35+90*i,layout.width()-160,72,stage==1 ? 1 : 0,1,i,80,35+90*i,"colour-"+i,i==6,stage==2 ? 1 : 0);
                var content = new SongSelectRowPresentation.Content(labels[i],"Artist // Mapper","Difficulty",null,
                        SongSelectRowPresentation.Stars.of(OptionalDouble.empty()),-1);
                presentations.add(new SongSelectRowRenderer.Presentation(row,content,i==2,null,null,0,
                        SongSelectLayout.row(row,i,row.x(),row.y(),layout.width(),0,layout.height(),false,false),i==5,
                        Color.rgba8888(SongSelectRowColours.background(new Color(),row,i==2,i==5))));
            }
            Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            ((SongSelectRowRenderer)screenField(screen,"rowRenderer")).draw(presentations,
                    new SongSelectRowRenderer.Style(layout.width(),assets,(Texture)screenField(screen,"rowFill"),Color.BLACK,Color.WHITE,false));
            var pixels = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
            try {
                for (int i=0;i<labels.length;i++) {
                    int pixel=pixels.getPixel(Math.round(700*fb.getWidth()/layout.width()),
                            Math.round((35+90*i+36)*fb.getHeight()/layout.height()));
                    for (int channel=0;channel<3;channel++) {
                        int actual=(pixel >>> (24-channel*8)) & 255;
                        double expected=palette[stage][i][channel]*palette[stage][i][3]/255.0;
                        if (Math.abs(actual-expected)>2)
                            throw new AssertionError("Row colour pixel: " + name + " " + labels[i] + " stage=" + stage
                                    + " channel=" + channel + " expected=" + expected + " actual=" + actual);
                    }
                }
            } finally { pixels.dispose(); }
            capture(fb,name+"-"+new String[]{"base","hover-target","focus-target"}[stage]);
        }
    }

    private void exerciseWheel(SongSelectScreen screen, Scene scene, InputProcessor input,
            int[] pointer, Set<Integer> held, UiLayout layout, FrameBuffer fb, String name) {
        int fps = Integer.parseInt(scene.name.substring("wheel-".length()));
        pointer[0] = Math.round(scene.width * .8f); pointer[1] = scene.height / 2;
        var model = carousel(screen); var selection = browser(screen).selection();
        float initial = model.scrollVelocity();
        input.scrolled(0, 20); input.scrolled(0, -20); screen.render(0); transitionFrames++;
        if (model.scrollVelocity() != initial) throw new AssertionError("Cancelled batch changed velocity: " + name);
        capture(fb, name + "-cancelled");
        double scale = SongSelectMetrics.carouselScale(layout.height()) * 1000;
        double velocity = initial / scale;
        float delta = 1f / fps;
        for (int frame = 0; frame < fps / 2; frame++) {
            int direction = frame < fps / 4 ? 1 : -1;
            // Twenty callbacks still yield one impulse; the negative half first brakes inertia.
            for (int event = 0; event < 20; event++) input.scrolled(0, direction * 3);
            velocity += direction * .4 * (1 + Math.min(Math.abs(velocity) / 2, 5));
            velocity *= Math.pow(.994, delta * 1000.0);
            if (Math.abs(velocity) < .01) velocity = 0;
            screen.render(delta); transitionFrames++; assertRenderedBounds(screen, layout);
            if (Math.abs(model.scrollVelocity() - velocity * scale) > .02)
                throw new AssertionError("Wheel aggregation/integration: " + name + " frame " + frame);
            if (!selection.equals(browser(screen).selection()) || pending(screen))
                throw new AssertionError("Wheel changed selection: " + name);
            if (frame == 0 || frame == fps / 4 || frame == fps / 2 - 1) capture(fb, name + "-frame-" + frame);
        }
        held.add(Input.Keys.DOWN); input.keyDown(Input.Keys.DOWN);
        screen.render(0); screen.render(.25f); transitionFrames += 2;
        input.scrolled(0, 1); screen.render(0); transitionFrames++;
        if (model.pointerCancellationEnabled(300)) throw new AssertionError("Wheel ran after key repeat: " + name);
        assertRenderedBounds(screen, layout); capture(fb, name + "-key-repeat");
        held.remove(Input.Keys.DOWN); input.keyUp(Input.Keys.DOWN);
    }

    private void tapPointer(SongSelectScreen screen, boolean[] clicked, boolean[] pressed) {
        clicked[0] = true; pressed[0] = true; screen.render(0); transitionFrames++;
        clicked[0] = false; pressed[0] = false; screen.render(0); transitionFrames++;
    }

    private void exerciseActivation(SongSelectScreen screen, Scene scene, int[] pointer,
            boolean[] clicked, boolean[] pressed, boolean[] right, boolean[] middle,
            UiLayout layout, FrameBuffer fb, String name) {
        boolean middleCase = scene.name.endsWith("middle"), rightCase = scene.name.endsWith("right");
        pointerRow(screen, 2, -1, pointer, layout, scene.height);
        clicked[0] = pressed[0] = !middleCase && !rightCase;
        middle[0] = middleCase; right[0] = rightCase;
        screen.render(0); transitionFrames++;
        clicked[0] = pressed[0] = middle[0] = right[0] = false;
        screen.render(0); transitionFrames++;
        if (pending(screen) || !browser(screen).selectedSet().id().equals("set2"))
            throw new AssertionError("First release must expand the Set without playing");
        capture(fb, name + "-expanded");
        var selected = carousel(screen).rows().stream().filter(r -> r.entry.key().equals(browser(screen).selectedKey())).findFirst().orElseThrow();
        pointerRow(screen, selected.entry.setIndex(), selected.entry.difficultyIndex(), pointer, layout, scene.height);
        if (middleCase) {
            middle[0] = true; screen.render(0); transitionFrames++;
            if (((SongSelectInputController) screenField(screen, "input")).pressedKey() != null)
                throw new AssertionError("Middle double-click recaptured a row");
            middle[0] = false; screen.render(0); transitionFrames++;
            if (pending(screen)) throw new AssertionError("Middle double-click started play");
            capture(fb, name + "-second-release");
            middle[0] = true; screen.render(0); transitionFrames++;
            middle[0] = false; screen.render(0); transitionFrames++;
        } else tapPointer(screen, clicked, pressed);
        if (!pending(screen)) throw new AssertionError("Valid selected release was blocked after Set expansion");
        assertRenderedBounds(screen, layout); capture(fb, name + "-play-requested");
    }

    private void exercisePointer(SongSelectScreen screen, Scene scene, int[] pointer,
            boolean[] clicked, boolean[] pressed, boolean[] rightClicked, boolean[] rightPressed,
            UiLayout layout, FrameBuffer fb, String name) {
        var model = carousel(screen);
        var browser = (SongBrowserModel) screenField(screen, "browser");
        String selection = browser.selectedKey();
        var row = model.rows().stream().filter(r -> r.entry.key().equals(selection)).findFirst().orElseThrow();
        if (scene.name.equals("pointer-threshold")) {
            for (int distance : new int[]{81, 80}) {
                pointerRow(screen, row.entry.setIndex(), row.entry.difficultyIndex(), pointer, layout, scene.height);
                int startX = pointer[0];
                clicked[0] = true; pressed[0] = true; screen.render(.02f); transitionFrames++; clicked[0] = false;
                pointer[0] += distance; screen.render(.02f); transitionFrames++;
                pointer[0] = startX; screen.render(.02f); transitionFrames++;
                capture(fb, name + "-held-" + distance);
                pressed[0] = false; screen.render(0); transitionFrames++;
                if (pending(screen) != (distance == 80)) throw new AssertionError("Window-pixel click threshold: " + name);
                assertRenderedBounds(screen, layout); capture(fb, name + "-released-" + distance);
            }
        } else if (scene.name.equals("pointer-context")) {
            var other = model.rows().stream().filter(r -> r.entry.setIndex() == row.entry.setIndex()
                    && r.entry.difficultyIndex() >= 0 && !r.entry.key().equals(selection)).findFirst().orElseThrow();
            pointerRow(screen, other.entry.setIndex(), other.entry.difficultyIndex(), pointer, layout, scene.height);
            rightPressed[0] = true; screen.render(0); transitionFrames++;
            if (!selection.equals(browser.selectedKey())) throw new AssertionError("Right down selected before release");
            capture(fb, name + "-pressed");
            rightPressed[0] = false; screen.render(0); transitionFrames++;
            if (!other.entry.key().equals(browser.selectedKey()) || pending(screen)
                    || !"Beatmap Options unavailable in this version.".equals(screenField(screen, "toast")))
                throw new AssertionError("Right release did not select and request Options");
            assertRenderedBounds(screen, layout); capture(fb, name + "-released");
            return;
        } else if (scene.name.equals("pointer-chord")) {
            pointerRow(screen, row.entry.setIndex(), row.entry.difficultyIndex(), pointer, layout, scene.height);
            clicked[0] = true; pressed[0] = true; rightPressed[0] = true;
            screen.render(.02f); transitionFrames++; clicked[0] = false;
            rightPressed[0] = false; screen.render(.02f); transitionFrames++;
            if (pending(screen) || !"Beatmap Options unavailable in this version.".equals(screenField(screen, "toast")))
                throw new AssertionError("Mixed-button release played instead of requesting Options");
            capture(fb, name + "-right-released");
            float before = model.scrollOffset();
            pointer[1] -= 30; screen.render(.02f); transitionFrames++;
            if (Math.abs(model.scrollOffset() - before - 30 / layout.scale()) > .01)
                throw new AssertionError("Right release stopped left drag");
            assertRenderedBounds(screen, layout); capture(fb, name + "-left-held");
            pressed[0] = false; screen.render(0); transitionFrames++;
            if (pending(screen)) throw new AssertionError("Candidate committed twice");
            capture(fb, name + "-left-released");
        } else {
            pointerRow(screen, row.entry.setIndex(), row.entry.difficultyIndex(), pointer, layout, scene.height);
            rightClicked[0] = true; rightPressed[0] = true; screen.render(0); rightClicked[0] = false;
            if (((SongSelectInputController) screenField(screen, "input")).rightScrolling())
                throw new AssertionError("Right press on row started scrolling");
            rightPressed[0] = false; screen.render(0);
            pointer[0] = Math.round(250f * scene.height / 480); pointer[1] = Math.round(235f * scene.height / 480);
            rightClicked[0] = true; rightPressed[0] = true; screen.render(0); rightClicked[0] = false;
            for (int stage = 0; stage < 3; stage++) {
                pointer[1] = Math.round((stage == 0 ? 235 : stage == 1 ? 410 : 60) * scene.height / 480f);
                double fraction = Math.max(0, Math.min(1, (pointer[1] * 480.0 / scene.height - 70) / 330));
                for (int frame = 0; frame < 30; frame++) {
                    float before = model.scrollOffset();
                    float dt = frame % 3 == 0 ? 1f / 30 : frame % 3 == 1 ? 1f / 60 : 1f / 144;
                    screen.render(dt); transitionFrames++;
                    double expected = fraction * model.maxScroll() + (before - fraction * model.maxScroll()) * Math.pow(.992, dt * 1000);
                    if (Math.abs(model.scrollOffset() - expected) > .01) throw new AssertionError("Right-position interpolation: " + name);
                    assertRenderedBounds(screen, layout);
                    if (frame == 0 || frame == 29) capture(fb, name + "-stage-" + stage + "-frame-" + frame);
                }
            }
            rightPressed[0] = false; screen.render(0);
            if (pending(screen)) throw new AssertionError("Right scroll played a row");
        }
        if (!selection.equals(browser.selectedKey())) throw new AssertionError("Pointer gesture changed playable selection");
    }

    private void exerciseDrag(SongSelectScreen screen, Scene scene, InputProcessor input,
            int[] pointer, boolean[] clicked, boolean[] pressed, UiLayout layout, FrameBuffer fb, String name) {
        var model = carousel(screen);
        var browser = (SongBrowserModel) screenField(screen, "browser");
        String selection = browser.selectedKey();
        var row = model.rows().stream().filter(r -> r.entry.key().equals(selection)).findFirst().orElseThrow();
        pointerRow(screen, row.entry.setIndex(), row.entry.difficultyIndex(), pointer, layout, scene.height);
        clicked[0] = true; pressed[0] = true; screen.render(.02f); clicked[0] = false;
        float before = model.scrollOffset();
        for (int frame = 0; frame < 3; frame++) {
            int pixels = Math.round((scene.name.equals("drag-reverse") ? (frame == 2 ? -100 : 50) : 30) * layout.scale());
            pointer[1] -= pixels;
            float previous = model.scrollOffset();
            screen.render(.02f); transitionFrames++;
            float expected = Math.max(0, Math.min(model.maxScroll(), previous + pixels / layout.scale()));
            if (Math.abs(model.scrollOffset() - expected) > .01f) throw new AssertionError("Drag integrated free flight: " + name);
            assertRenderedBounds(screen, layout); capture(fb, name + "-held-" + frame);
        }
        if (model.scrollOffset() == before && !scene.name.equals("drag-reverse")) throw new AssertionError("Drag did not move: " + name);
        float velocity = model.scrollVelocity(), position = model.scrollOffset();
        if (scene.name.equals("drag-reverse") && velocity >= 0) throw new AssertionError("Reversal did not brake: " + name);
        if (scene.name.equals("drag-pause")) {
            for (int frame = 0; frame < 5; frame++) { screen.render(.02f); transitionFrames++; }
            if (Math.abs(model.scrollOffset() - position) > .001 || model.scrollVelocity() != velocity)
                throw new AssertionError("Stationary hold moved: " + name);
        }
        if (scene.name.equals("drag-cancel")) { tapKey(input, Input.Keys.F1); screen.render(0); }
        pressed[0] = false; screen.render(0);
        float expectedVelocity = scene.name.equals("drag-cancel") ? 0
                : scene.name.equals("drag-pause") ? velocity * (float)Math.pow(.95, 34) : velocity;
        if (Math.abs(model.scrollVelocity() - expectedVelocity) > .01f) throw new AssertionError("Release speed: " + name);
        for (int frame = 0; frame < 60; frame++) {
            screen.render(1f / 60); transitionFrames++; assertRenderedBounds(screen, layout);
            if (frame == 0 || frame == 9 || frame == 59) capture(fb, name + "-released-" + frame);
        }
        if (!selection.equals(browser.selectedKey()) || pending(screen)) throw new AssertionError("Drag clicked a row: " + name);
        if (scene.name.equals("drag-cancel") && Math.abs(model.scrollOffset() - position) > .001)
            throw new AssertionError("Cancelled drag retained velocity: " + name);
    }

    private void configureToolbox(SongSelectScreen screen, Scene scene, InputProcessor input,
            int[] pointer, boolean[] clicked, boolean[] pressed, UiLayout layout,
            dev.osujava.score.LocalScoreStore store, BeatmapLibrary library) {
        String state = scene.name.replaceFirst("phase5a-profile-[0-9]+-", "").replaceFirst("phase5a-(current|state|assets)-", "");
        var geometry = toolboxLayout(screen);
        if (state.equals("oversized-hover") || state.startsWith("upper-chrome")) point(geometry.control(SongSelectSkinAssets.Selection.MODE).interaction(),pointer,layout,scene.height);
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
            } else tapKey(input, Input.Keys.F1);
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
            for (int key : new int[]{Input.Keys.F2,Input.Keys.F3,Input.Keys.I,Input.Keys.ENTER,Input.Keys.F6,Input.Keys.DOWN}) tapKey(input, key);
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
            gameplay.show(); tapKey(Gdx.input.getInputProcessor(), Input.Keys.ESCAPE); gameplay.dispose(); Gdx.input.setInputProcessor(input);
            if (store.revision() != revision) throw new AssertionError("Aborted gameplay marked played");
        }
        if (state.equals("large-hover")) point(geometry.control(SongSelectSkinAssets.Selection.RANDOM).interaction(),pointer,layout,scene.height);
        if (state.equals("large-scroll")) for (int i=0;i<12;i++) { carousel(screen).scrollBy(80); screen.render(1f/60); }
        screen.render(1f/60);
        if (state.startsWith("upper-chrome")) for (int frame = 0; frame < 12; frame++) screen.render(1f/60);
        assertRenderedBounds(screen,layout);
    }

    private void assertToolbox(SongSelectScreen screen, SongSelectSkinAssets assets, String name, UiLayout layout) {
        var geometry = toolboxLayout(screen);
        for (var image : List.of(SongSelectSkinAssets.Image.TOP,SongSelectSkinAssets.Image.BOTTOM)) {
            if (screen.renderedChromeProcedural(image) != (assets.get(image) == null))
                throw new AssertionError("Chrome rendering presence differs from provider resolution");
        }
        if (assets.get(SongSelectSkinAssets.Image.TOP) != null
                && !assets.topLayoutProvider().equals(assets.provider(SongSelectSkinAssets.Image.TOP)))
            throw new AssertionError("Top layout used different artwork from rendering");
        System.out.println("CHROME OWNERSHIP " + name + " top-visual=" + assets.provider(SongSelectSkinAssets.Image.TOP)
                + " top-layout=" + assets.topLayoutProvider() + " bottom-visual=" + assets.provider(SongSelectSkinAssets.Image.BOTTOM)
                + " bottom-artwork=" + geometry.bottomImage + " bottom-reservation=" + geometry.chrome);
        if (name.equals("phase5a-assets-tiny-chrome") && (geometry.bottomImage.height() >= 1
                || geometry.chrome.height() < SongSelectChrome.bottomHeight(layout.height())
                || assets.get(SongSelectSkinAssets.Image.TOP) == null || assets.get(SongSelectSkinAssets.Image.BOTTOM) == null))
            throw new AssertionError("Tiny chrome changed presence or layout reservation");
        float scale = layout.height()/768;
        if (name.equals("phase5a-assets-composite")) {
            var art = geometry.control(SongSelectSkinAssets.Selection.MODE).normal().image();
            int x = Math.round(art.x() + 50 * scale), y = Math.round(art.y() + (540 - 150) * scale);
            var pixel = Pixmap.createFromFrameBuffer(
                    Math.round(x * Gdx.graphics.getBackBufferWidth() / layout.width()),
                    Math.round(y * Gdx.graphics.getBackBufferHeight() / layout.height()), 1, 1);
            try {
                int rgba = pixel.getPixel(0, 0);
                if ((rgba >>> 16 & 255) < 180 || (rgba >>> 24 & 255) > 50 || (rgba >>> 8 & 255) > 50)
                    throw new AssertionError("Composite decoration above bottom reservation was clipped: " + Integer.toHexString(rgba));
            } finally { pixel.dispose(); }
            if (geometry.control(SongSelectSkinAssets.Selection.MODE).interaction().contains(x, y))
                throw new AssertionError("Composite decoration expanded the control hitbox");
            int cardX = Math.round(art.x() + 690 * scale), cardY = Math.round(art.y() + (540 - 310) * scale);
            var overlap = Pixmap.createFromFrameBuffer(
                    Math.round(cardX * Gdx.graphics.getBackBufferWidth() / layout.width()),
                    Math.round(cardY * Gdx.graphics.getBackBufferHeight() / layout.height()), 1, 1);
            try {
                int rgba = overlap.getPixel(0, 0);
                if ((rgba >>> 16 & 255) < 180 || (rgba >>> 24 & 255) > 50 || (rgba >>> 8 & 255) > 50)
                    throw new AssertionError("Carousel overwrote composite chrome: " + Integer.toHexString(rgba));
            } finally { overlap.dispose(); }
        }
        if (name.startsWith("phase5a-assets-upper-chrome")) {
            var art = geometry.control(SongSelectSkinAssets.Selection.MODE).normal().image();
            // Sample the full title/mapper band: old metadata-after-chrome ordering
            // leaks white glyphs into this opaque green part of selection-mode.
            var pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try {
                for (int x = 15; x < Math.min(430, (layout.width()*.52f-28-art.x())/scale); x += 2) for (int y = 5; y < 70; y += 2) {
                    int px = Math.round((art.x() + x * scale) * pixels.getWidth() / layout.width());
                    int py = Math.round((art.y() + (768-y) * scale) * pixels.getHeight() / layout.height());
                    int rgba = pixels.getPixel(px, py);
                    if ((rgba >>> 16 & 255) < 240 || (rgba >>> 24 & 255) > 15 || (rgba >>> 8 & 255) > 15)
                        throw new AssertionError("Metadata overwrote upper selection-mode chrome: " + Integer.toHexString(rgba));
                }
            } finally { pixels.dispose(); }
            // The red hover decoration crosses MODS' blue normal canvas.
            var overlap = Pixmap.createFromFrameBuffer(
                    Math.round((art.x() + 125 * scale) * Gdx.graphics.getBackBufferWidth() / layout.width()),
                    Math.round((art.y() + 45 * scale) * Gdx.graphics.getBackBufferHeight() / layout.height()), 1, 1);
            try {
                int rgba = overlap.getPixel(0, 0);
                if ((rgba >>> 24 & 255) < 240 || (rgba >>> 16 & 255) > 15 || (rgba >>> 8 & 255) > 15)
                    throw new AssertionError("Normal button covered Mode hover: " + Integer.toHexString(rgba));
            } finally { overlap.dispose(); }
            if (geometry.control(SongSelectSkinAssets.Selection.MODE).interaction().contains(
                    art.x() + 125 * scale, art.y() + 45 * scale))
                throw new AssertionError("Hover decoration expanded Mode input into Mods");
        }
        boolean legacy = assets.legacySelectionAnchors();
        if (name.equals("phase5a-assets-v1-default-mods") && (legacy || assets.configuration().legacyVersion() != 1))
            throw new AssertionError("Built-in Mods did not select new anchors independently of Version 1");
        if (name.equals("phase5a-assets-v1-custom-mods") && !legacy)
            throw new AssertionError("Custom Mods did not retain Version 1 anchors");
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
                if (name.equals("phase5a-assets-normal") && image == action.hover && texture != null)
                    throw new AssertionError("Normal-only fixture without fallback supplied hover");
                if (name.equals("phase5a-assets-normal-bundled")
                        && !assets.provider(image).equals(image == action.hover ? "bundled" : "current"))
                    throw new AssertionError("Normal and hover did not resolve independently");
                if (name.equals("phase5a-assets-high") && (texture == null || texture.density() != 2)) throw new AssertionError("Selection density lost");
                String expected = name.equals("phase5a-assets-fallback") ? "fallback"
                        : name.equals("phase5a-assets-bundled") ? "bundled"
                        : name.equals("phase5a-assets-missing") || name.equals("phase5a-assets-malformed") ? "procedural" : null;
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
            if (stage == 0) tapKey(input, Input.Keys.F1);
            if (stage == 1) tapKey(input, Input.Keys.ESCAPE);
            if (stage == 2) screen.browserMode(SongBrowserModel.Sort.ARTIST,SongBrowserModel.Group.ARTIST);
            if (stage == 3) screen.browserSearch("Local song 2",false);
            if (stage == 4) { screen.browserSearch("",false); tapKey(input, Input.Keys.F2); }
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
        if (toolboxState(screen).open()) tapKey(input, Input.Keys.ESCAPE);
        long[] switches = new long[100];
        for (int i=0;i<140;i++) { long start=System.nanoTime(); tapKey(input, i%2==0 ? Input.Keys.DOWN : Input.Keys.UP);
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
    /** Exercise the production background pass with a sentinel outside every row clip. */
    private void assertScoreClipping(SongSelectScreen screen, UiLayout layout, FrameBuffer fb) {
        var renderer = (SongSelectRenderer) screenField(screen, "renderer");
        Gdx.gl.glClearColor(1, 0, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        try {
            var draw = SongSelectRenderer.class.getDeclaredMethod("drawScoreBackgrounds", UiLayout.class);
            draw.setAccessible(true); draw.invoke(renderer, layout);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        if (Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)) throw new AssertionError("Score pass retained scissor state");
        var pixels = Pixmap.createFromFrameBuffer(0, 0, fb.getWidth(), fb.getHeight());
        var bounds = screen.scoreBounds(layout);
        try {
            for (int slot = 0; slot < bounds.capacity(); slot++) {
                float y = bounds.rowY(slot), centre = y + bounds.rowHeight() / 2;
                for (float[] sample : new float[][]{{bounds.x()-2, centre}, {bounds.x()+bounds.width()+2, centre},
                        {bounds.x()+bounds.width()/2, y-2}, {bounds.x()+bounds.width()/2, y+bounds.rowHeight()+2}}) {
                    int px = Math.round(sample[0] * pixels.getWidth() / layout.width());
                    int py = Math.round(sample[1] * pixels.getHeight() / layout.height());
                    if (px >= 0 && px < pixels.getWidth() && py >= 0 && py < pixels.getHeight()
                            && pixels.getPixel(px, py) != Color.rgba8888(Color.MAGENTA))
                        throw new AssertionError("Score artwork escaped row clip: " + slot + " at " + px + "," + py);
                }
            }
        } finally { pixels.dispose(); }
        screen.render(0);
    }

    private void exerciseScoreResize(SongSelectScreen screen, Scene scene, int[] display, FrameBuffer original) {
        var scores = scoreBrowser(screen);
        var target = scores.target();
        scores.select(1); var selected = scores.selected();
        original.end();
        try {
            for (int[] size : new int[][]{{1024,768}, {1280,800}, {1920,1080}, {1280,720}}) {
                display[0] = size[0]; display[1] = size[1];
                var layout = UiLayout.fromPixels(size[0], size[1]);
                var resized = new FrameBuffer(Pixmap.Format.RGBA8888, size[0]*scene.density, size[1]*scene.density, false);
                try {
                    screen.resize(size[0],size[1]); scores.scroll(10000);
                    resized.begin();
                    for (int frame = 0; frame < 24; frame++) {
                        screen.render(1f/60); assertRenderedBounds(screen, layout); transitionFrames++;
                    }
                    var bounds = screen.scoreBounds(layout);
                    if (!Objects.equals(target, scores.target()) || !Objects.equals(selected, scores.selected())
                            || scores.first() != Math.max(0, scores.rows().size()-bounds.capacity()))
                        throw new AssertionError("Resize lost score target, selection or scroll clamp");
                    if (bounds.x()+bounds.width()+ScoreBrowserBounds.COLUMN_GAP
                            > SongSelectMetrics.wheelLeft(layout.width(),layout.height())+.001f)
                        throw new AssertionError("Resize overlapped ranking and carousel");
                    assertScoreClipping(screen, layout, resized);
                    capture(resized, scene.width+"x"+scene.height+"-"+scene.density+"x-resize-to-"+size[0]+"x"+size[1]);
                    resized.end();
                } finally { resized.dispose(); }
            }
        } finally {
            display[0] = scene.width; display[1] = scene.height;
            screen.resize(scene.width,scene.height); original.begin(); screen.render(0);
        }
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
        int before=scores.first(); input.scrolled(0,1); ((SongSelectWheelInput) input).dispatch();
        if(carousel(screen).scrollTarget()!=carouselTarget) throw new AssertionError("Left wheel moved carousel");
        scores.scroll(-1);
        if(scores.first()!=before) throw new AssertionError("Score scroll did not reverse");
        pointer[0]=Math.round(layout.width()*.9f*layout.scale()); pointer[1]=height/2;
        input.scrolled(0,1); ((SongSelectWheelInput) input).dispatch();
        if(scores.first()!=before) throw new AssertionError("Right wheel moved score browser");
        input.scrolled(0,-1); ((SongSelectWheelInput) input).dispatch();
        pointer[0]=Math.round(100*layout.scale()); pointer[1]=height-Math.round((bounds.top()-32)*layout.scale());
        if(name.equals("phase4-scroll-middle")) scores.scroll(6);
        if(name.equals("phase4-scroll-bottom") || name.equals("phase4-numbers-bottom")) scores.scroll(10000);
        if(name.equals("phase4-selected")) { clicked[0]=true; screen.render(0); clicked[0]=false; if(scores.selected()==null)throw new AssertionError("Score click lost"); }
        if(name.equals("phase4-no-score")) tapKey(input, Input.Keys.UP);
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
                    case "input-switch" -> tapKey(Gdx.input.getInputProcessor(), i%2==0 ? Input.Keys.DOWN : Input.Keys.UP);
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
                if (content.stars().count() > 10) throw new AssertionError("Unbounded stars");
                if (content.stars().present() && content.stars().slots() != 10)
                    throw new AssertionError("Star background does not have ten slots");
                if (scene.equals("phase2-tenth-star") && content.stars().present()
                        && Math.abs(content.stars().fill(9) - .25f) > .0001f)
                    throw new AssertionError("The tenth star lost its fractional foreground");
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

    /** Repeatable state capture through production model/input APIs and read-only state inspection. */
    private void captureConfigured(SongSelectScreen screen, Scene scene, FrameBuffer fb, int[] pointer,
                                   InputProcessor input, SongSelectSkinAssets assets) {
        var layout = UiLayout.fromPixels(scene.width, scene.height);
        screen.previewSelection(Integer.getInteger("osujava.songSelectSelectedSet", 3),
                Integer.getInteger("osujava.songSelectSelectedDifficulty", 1));
        screen.browserSearch(System.getProperty("osujava.songSelectSearch", ""), false);
        // Establish resources and initial state before the requested transition.
        screen.render(0);
        if (scene.name.equals("parity-animated-back")) {
            float scale = layout.height()/768;
            screen.render(.5f);
            var changed = (SongSelectToolboxLayout)screenField(screen,"bottomLayout");
            var texture = (SongSelectSkinAssets.SkinTexture)screenField(screen,"backFrame");
            if (texture.logicalWidth() != 140 || Math.abs(changed.backImage.width()-80*scale) > .01f)
                throw new AssertionError("Back texture swaps after the cached geometry update");
            var pixel = Pixmap.createFromFrameBuffer(Math.round(60*scene.height/768f*scene.density),
                    Math.round(20*scene.height/768f*scene.density),1,1);
            try {
                int colour = pixel.getPixel(0,0);
                if ((colour >>> 24 & 255) < 240 || (colour >>> 8 & 255) > 15)
                    throw new AssertionError("Back must sample the cached 80x50 crop after the HD texture swap");
            } finally { pixel.dispose(); }
            screen.render(0);
            var refreshed = (SongSelectToolboxLayout)screenField(screen,"bottomLayout");
            if (Math.abs(refreshed.backImage.width()-140*scale) > .01f
                    || Math.abs(refreshed.backImage.height()-90*scale) > .01f
                    || Math.abs(refreshed.backInteraction.width()-140*scale) > .01f)
                throw new AssertionError("Back draw and interaction dimensions must follow the next update");
        }
        screen.previewScroll(Float.parseFloat(System.getProperty("osujava.songSelectScroll", "0")));
        int key = switch (System.getProperty("osujava.songSelectAction", "none")) {
            case "selection" -> Input.Keys.DOWN;
            case "random" -> Input.Keys.F2;
            case "back" -> Input.Keys.ESCAPE;
            case "mode" -> -2;
            case "mods" -> Input.Keys.F1;
            case "options" -> Input.Keys.F3;
            case "none" -> -1;
            default -> throw new IllegalArgumentException("Unknown capture action");
        };
        if (key == -2) toolboxState(screen).open(SongSelectToolboxState.Overlay.MODE);
        else if (key >= 0) tapKey(input, key);
        String[] hover = System.getProperty("osujava.songSelectHover", "40,360").split(",");
        pointer[0] = Math.round(Float.parseFloat(hover[0]) * layout.scale());
        pointer[1] = scene.height - Math.round(Float.parseFloat(hover[1]) * layout.scale());
        float time = Float.parseFloat(System.getProperty("osujava.songSelectTime", "0.667"));
        if (!Float.isFinite(time) || time < 0 || time > 60) throw new IllegalArgumentException("Capture time must be 0..60s");
        int steps = (int) Math.ceil(time * 120);
        for (int step = 0; step < steps; step++) screen.render(Math.min(1f / 120, time - step / 120f));
        screen.render(0);
        String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + scene.name;
        if (scene.name.equals("parity-overlap")) {
            int x = Math.round(260 * scene.height / 768f * scene.density);
            int y = Math.round(40 * scene.height / 768f * scene.density);
            var pixel = Pixmap.createFromFrameBuffer(x,y,1,1);
            try {
                int colour = pixel.getPixel(0,0);
                if ((colour >>> 16 & 255) < 240 || (colour >>> 24 & 255) > 15 || (colour >>> 8 & 255) > 15)
                    throw new AssertionError("Back must precede green selection artwork: " + Integer.toHexString(colour));
            } finally { pixel.dispose(); }
        }
        if (scene.name.startsWith("parity-")) {
            var pixel = Pixmap.createFromFrameBuffer(Math.round(490 * scene.height/768f * scene.density),
                    Math.round(40 * scene.height/768f * scene.density),1,1);
            try {
                int colour = pixel.getPixel(0,0);
                if ((colour >>> 24 & 255) < 240 || (colour >>> 16 & 255) < 240 || (colour >>> 8 & 255) < 240)
                    throw new AssertionError("Options normal artwork must retain its white tint: " + Integer.toHexString(colour));
            } finally { pixel.dispose(); }
        }
        if (scene.name.equals("parity-odd-hd") && scene.height == 768 && scene.density == 1) {
            var pixel = Pixmap.createFromFrameBuffer(315,40,1,1);
            try {
                int colour = pixel.getPixel(0,0);
                if ((colour >>> 16 & 255) < 240 || (colour >>> 8 & 255) > 15)
                    throw new AssertionError("Odd HD sentinel column must be cropped: " + Integer.toHexString(colour));
            } finally { pixel.dispose(); }
        }
        if (scene.name.equals("parity-short-top")) {
            var pixel = Pixmap.createFromFrameBuffer(Math.round(scene.width*.4f*scene.density),
                    Math.round((scene.height-28)*scene.density),1,1);
            try {
                if ((pixel.getPixel(0,0) >>> 8 & 255) > 100)
                    throw new AssertionError("Short top must not stretch its edge across the viewport");
            } finally { pixel.dispose(); }
        }
        capture(fb, name);
        var report = new StringBuilder("window=" + scene.width + "x" + scene.height + " density=" + scene.density
                + " time=" + time + "\n");
        report.append("locale=").append(Locale.getDefault().toLanguageTag())
                .append(" java=").append(System.getProperty("java.version"))
                .append(" font=SmoothUiFont(AWT logical SansSerif, oversample=2)\n");
        report.append("elapsed=").append(screenField(screen,"seconds"))
                .append(" backProbe=").append(scene.name.equals("parity-animated-back") ? "update(0.5),update(0)" : "none").append("\n");
        var sampledBack = (SongSelectSkinAssets.SkinTexture)screenField(screen,"backFrame");
        if (sampledBack != null) report.append("backFrame=").append(sampledBack.file().path()).append("\n");
        for (var image : SongSelectSkinAssets.Image.values()) {
            var asset = assets.get(image);
            report.append(image.basename).append(" provider=").append(assets.provider(image));
            if (asset != null) report.append(" density=").append(asset.density()).append(" source=").append(asset.file().path())
                    .append(" physical=").append(asset.texture().getWidth()).append('x').append(asset.texture().getHeight())
                    .append(" logical=").append(asset.logicalWidth()).append('x').append(asset.logicalHeight());
            report.append("\n");
        }
        report.append("skinVersion=").append(assets.configuration().legacyVersion()).append("\n");
        report.append("legacySelectionAnchors=").append(assets.legacySelectionAnchors()).append("\n");
        var browser = (SongBrowserModel) screenField(screen,"browser");
        report.append("selection=").append(browser.selection()).append(" focus=").append(browser.focusKey())
                .append(" query=").append(browser.search()).append(" sort=").append(browser.sort())
                .append(" group=").append(browser.group()).append("\n");
        report.append("input action=").append(System.getProperty("osujava.songSelectAction","none"))
                .append(" hover=").append(Arrays.toString(hover)).append(" steps=").append(steps).append(" hz=120\n");
        report.append("tabs=").append(SongBrowserControls.tabCount(layout.width(),layout.height())).append("\n");
        var geometry = (SongSelectToolboxLayout)screenField(screen,"bottomLayout");
        report.append("back=").append(geometry.backImage).append(" hit=").append(geometry.backInteraction).append("\n");
        for (var action : SongSelectSkinAssets.Selection.values())
            report.append(action).append('=').append(geometry.control(action)).append("\n");
        for (var row : screen.rowGeometrySnapshot()) report.append(row).append("\n");
        try { Files.writeString(output.resolve(name + ".txt"), report); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private void capture(FrameBuffer fb, String name) {
        Pixmap capture = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
        PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),capture,-1,true);
        capture.dispose();
        captures++;
    }

    /** Authored test colours, never exported native assets. Baselines record Java, not parity. */
    private void createParityFixtures() throws java.io.IOException {
        for (String name : List.of("overlap","transparent-back","odd-hd","short-top","animated-back")) {
            Path dir = Files.createDirectories(output.resolve("fixtures/parity-"+name));
            Files.writeString(dir.resolve("skin.ini"),"[General]\nVersion: 2.2\nAnimationFramerate: 2\n[Colours]\nSongSelectActiveText: 0,0,0\nSongSelectInactiveText: 255,255,255\n");
            for (String image : List.of("songselect-top","songselect-bottom","menu-back","cursor","cursortrail","cursormiddle","star2"))
                parityImage(dir,image,1,1,Color.CLEAR);
            for (var action : SongSelectSkinAssets.Selection.values()) {
                parityImage(dir,action.normal.basename,(int)action.logicalWidth,90,
                        action == SongSelectSkinAssets.Selection.MODE ? Color.GREEN : Color.WHITE);
                parityImage(dir,action.hover.basename,1,1,Color.CLEAR);
            }
            switch (name) {
                case "overlap" -> parityImage(dir,"menu-back",400,150,Color.RED);
                case "odd-hd" -> {
                    var pixels = new Pixmap(185,181,Pixmap.Format.RGBA8888);
                    try {
                        pixels.setColor(Color.GREEN); pixels.fill();
                        pixels.setColor(Color.BLUE); pixels.drawLine(184,0,184,180); pixels.drawLine(0,180,184,180);
                        PixmapIO.writePNG(Gdx.files.absolute(dir.resolve("selection-mode@2x.png").toString()),pixels);
                    } finally { pixels.dispose(); }
                    parityImage(dir,"menu-back@2x",545,183,Color.RED);
                }
                case "short-top" -> parityImage(dir,"songselect-top",100,90,Color.BLUE);
                case "animated-back" -> {
                    parityImage(dir,"menu-back-0",80,50,Color.RED);
                    var pixels = new Pixmap(280,180,Pixmap.Format.RGBA8888);
                    try {
                        pixels.setColor(Color.BLUE); pixels.fill();
                        pixels.setColor(Color.RED); pixels.fillRectangle(0,0,160,100);
                        PixmapIO.writePNG(Gdx.files.absolute(dir.resolve("menu-back-1@2x.png").toString()),pixels);
                    } finally { pixels.dispose(); }
                }
                default -> { }
            }
        }
        final java.security.MessageDigest digest;
        try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var manifest = new StringBuilder();
        Path root = output.resolve("fixtures");
        try (var files = Files.walk(root)) {
            for (var path : files.filter(Files::isRegularFile)
                    .filter(p -> root.relativize(p).getName(0).toString().startsWith("parity-")).sorted().toList())
                manifest.append(HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path))))
                        .append("  ").append(root.relativize(path)).append('\n');
        }
        Files.writeString(output.resolve("parity-fixtures.sha256"),manifest);
    }
    private void parityImage(Path dir, String name, int width, int height, Color colour) {
        var pixels = new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try {
            pixels.setColor(colour); pixels.fill();
            PixmapIO.writePNG(Gdx.files.absolute(dir.resolve(name+".png").toString()),pixels);
        } finally { pixels.dispose(); }
    }
    private void closeBrowserMenu(SongSelectScreen screen) {
        try { var f = SongSelectScreen.class.getDeclaredField("controls"); f.setAccessible(true); ((SongBrowserControls)f.get(screen)).close(); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private void exerciseKeyboardFocus(SongSelectScreen screen, String name, InputProcessor processor, UiLayout layout) {
        var browser = (SongBrowserModel) screenField(screen, "browser");
        if (name.endsWith("page")) tapKey(processor, Input.Keys.PAGE_DOWN);
        else if (name.contains("group")) {
            for (int i = 0; i < 20 && (browser.focusKey() == null || !browser.row(browser.focusKey()).group()); i++)
                tapKey(processor, Input.Keys.UP);
        } else {
            for (int i = 0; i < 20 && browser.focusKey() == null; i++) tapKey(processor, Input.Keys.DOWN);
        }
        if (browser.focusKey() == null) throw new AssertionError("Keyboard did not reach focus: " + name);
        var focused = browser.row(browser.focusKey());
        var selected = browser.selection();
        if (!focused.group() && focused.set.id().equals(selected.setId()))
            throw new AssertionError("Same-family movement should select directly");
        for (int i = 0; i < 90; i++) screen.render(1f / 60);
        var row = carousel(screen).rows().stream().filter(r -> r.entry.key().equals(focused.key)).findFirst().orElseThrow();
        if (row.focusAmount != 1 || row.selectedAmount > .001f)
            throw new AssertionError("Focus and selected emphasis were conflated");
        if (pending(screen)) throw new AssertionError("Focus navigation started gameplay");
        if (name.endsWith("confirm")) {
            tapKey(processor, Input.Keys.ENTER);
            if (browser.focusKey() != null || !browser.selectedSet().id().equals(focused.set.id()) || pending(screen))
                throw new AssertionError("Enter must confirm the focused Set before starting gameplay");
        } else if (name.endsWith("toggle")) {
            boolean expanded = focused.expanded;
            tapKey(processor, Input.Keys.ENTER);
            if (focused.expanded == expanded || !selected.equals(browser.selection()) || pending(screen))
                throw new AssertionError("Enter on focused Group must only toggle that Group");
        }
        for (int i = 0; i < 90; i++) screen.render(1f / 60);
        assertRenderedBounds(screen, layout);
        assertScoreTarget(screen);
    }

    private void exerciseGroupCards(SongSelectScreen screen, String name,
                                    int[] pointer, boolean[] clicked, boolean[] pressed, UiLayout layout, int height) {
        var browser = (SongBrowserModel) screenField(screen, "browser");
        var selection = browser.selection();
        var parent = browser.row(browser.selectedKey()).parent;
        clickGroupCard(screen, parent.key, pointer, clicked, pressed, layout, height);
        if (parent.expanded || !selection.equals(browser.selection()) || pending(screen))
            throw new AssertionError("Closing Group lost selection or started gameplay");
        if (browser.entries().stream().anyMatch(e -> e.kind() != SongBrowserModel.Kind.GROUP_HEADER))
            throw new AssertionError("Closed Groups still expose children");
        clickGroupCard(screen, parent.key, pointer, clicked, pressed, layout, height);
        if (!parent.expanded || pending(screen)) throw new AssertionError("Group re-click did not reopen Group");
        var next = browser.rows().stream().filter(r -> r.group() && r != parent).findFirst().orElseThrow();
        clickGroupCard(screen, next.key, pointer, clicked, pressed, layout, height);
        if (parent.expanded || !next.expanded || !selection.equals(browser.selection()) || pending(screen))
            throw new AssertionError("Opening another Group changed playable selection");
        if (name.endsWith("close")) clickGroupCard(screen, next.key, pointer, clicked, pressed, layout, height);
        pointer[0] = 40;
        for (int i = 0; i < 90; i++) screen.render(1f / 60);
        assertRenderedBounds(screen, layout);
    }

    private void clickGroupCard(SongSelectScreen screen, String key, int[] pointer, boolean[] clicked, boolean[] pressed,
                                UiLayout layout, int height) {
        // Position the viewport, then use the production press/release and shared hit geometry.
        carousel(screen).select(key);
        // On the left, native tracking returns to the playable selection instead of this test target.
        pointer[0] = Math.round((layout.width() - 100) * layout.scale());
        for (int i = 0; i < 90; i++) screen.render(1f / 60);
        var snapshots = (List<?>) screenField(screen, "visibleRows");
        var row = snapshots.stream().map(SongSelectRow.class::cast)
                .filter(r -> key.equals(r.key())).findFirst().orElseThrow();
        pointer[0] = Math.round(Math.min(layout.width() - 30, row.x() + 120) * layout.scale());
        pointer[1] = height - Math.round((row.y() + row.height() / 2) * layout.scale());
        tapPointer(screen, clicked, pressed);
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
            case "phase3-fallback", "phase3-modern", "phase3-group-artist", "phase3-group-expanded", "phase3-group-selected", "phase3-group-first", "phase3-group-last", "phase3-group-toggle", "phase3-group-close", "phase3-focus-group", "phase3-focus-group-toggle" -> SongBrowserModel.Group.ARTIST;
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
            case "phase3-group-first" -> { for(int i=0;i<12;i++)tapKey(processor, Input.Keys.LEFT); }
            case "phase3-group-last" -> { for(int i=0;i<12;i++)tapKey(processor, Input.Keys.RIGHT); }
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
    private static boolean tapKey(InputProcessor input, int key) {
        boolean handled = input.keyDown(key);
        input.keyUp(key);
        return handled;
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
        if (Boolean.getBoolean("osujava.selectionProfile")) profileSelection(screen, name);
        long total = 0, maximum = 0;
        for (int frame = 0; frame < 240; frame++) {
            long start = System.nanoTime(); screen.render(1f / 60); long elapsed = System.nanoTime() - start;
            if (frame >= 60) { total += elapsed; maximum = Math.max(maximum, elapsed); }
        }
        System.out.printf(Locale.ROOT, "Idle render CPU submission %s: mean %.3f ms, max %.3f ms (180 samples, shared artwork)%n",
                name, total / 180.0 / 1_000_000, maximum / 1_000_000.0);
    }

    /** Opt-in cold set activation + animated frames, rather than averaging events into idle. */
    private void profileSelection(SongSelectScreen screen, String name) {
        long[] activation = new long[40], firstFrame = new long[40], selectionFrame = new long[40], animation = new long[40 * 23];
        var thread = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long allocated = 0;
        for (int sample = -10; sample < 40; sample++) {
            int set = 100 + (sample + 10) * 2;
            long bytes = thread.getThreadAllocatedBytes(Thread.currentThread().threadId());
            long start = System.nanoTime(); screen.previewSelection(set, 0); long event = System.nanoTime() - start;
            for (int frame = 0; frame < 24; frame++) {
                start = System.nanoTime(); screen.render(1f / 60); long duration = System.nanoTime() - start;
                if (sample >= 0) {
                    if (frame == 0) { firstFrame[sample] = duration; selectionFrame[sample] = event + duration; }
                    else animation[sample * 23 + frame - 1] = duration;
                }
            }
            if (sample >= 0) {
                activation[sample] = event;
                allocated += thread.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
            }
        }
        for (var stage : java.util.Map.of("activation", activation, "first-frame", firstFrame,
                "selection-frame", selectionFrame, "animation", animation).entrySet()) {
            var samples = stage.getValue(); java.util.Arrays.sort(samples);
            System.out.printf(Locale.ROOT, "Selection profile %s %s: mean %.3f ms, p95 %.3f ms, max %.3f ms%n",
                    name, stage.getKey(), java.util.Arrays.stream(samples).average().orElseThrow() / 1e6,
                    samples[samples.length * 95 / 100] / 1e6, samples[samples.length - 1] / 1e6);
        }
        System.out.printf(Locale.ROOT, "Selection profile allocation: %.0f bytes/activation+24 frames%n", allocated / 40.0);
    }

    /** Production draw snapshots must exactly match Carousel output even during overlapping motion. */
    private void assertRenderedBounds(SongSelectScreen screen, UiLayout layout) {
        try {
            var field = SongSelectScreen.class.getDeclaredField("visibleRows"); field.setAccessible(true);
            var model = carousel(screen);
            for (Object snapshot : (List<?>) field.get(screen)) {
                var type = snapshot.getClass();
                String key = (String) value(type, snapshot, "key");
                var row = model.rows().stream().filter(r -> r.entry.key().equals(key)).findFirst().orElseThrow();
                if (!model.presents(row)) throw new AssertionError("Retired row was published for drawing");
                float x = (float) value(type,snapshot,"x"), y = (float) value(type,snapshot,"y");
                if (Math.abs(x - model.renderX(row,layout.width())) > .001f
                        || Math.abs(y - model.renderY(row,screen.chromeBounds(layout).carouselTop())) > .001f)
                    throw new AssertionError("Draw snapshot diverged from motion bounds");
                // Reference bounds: -340 base, -50 open/-45 hover, +200 curve and +200 unanchored reentry.
                float scale = SongSelectMetrics.carouselScale(layout.height());
                if (!Float.isFinite(x) || !Float.isFinite(y)
                        || x < layout.width() - 435 * scale - .01f || x > layout.width() + 60 * scale + .01f)
                    throw new AssertionError("Invalid row bounds: " + x + ", " + y);
                float bodyWidth = (float) value(type, snapshot, "width");
                if (x + bodyWidth < layout.width()) throw new AssertionError("Row ends inside the viewport");
                if (Math.abs(model.rowHeight() - SongSelectMetrics.rowPitch(layout.height())) > .001f)
                    throw new AssertionError("Skin changed row density");
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
