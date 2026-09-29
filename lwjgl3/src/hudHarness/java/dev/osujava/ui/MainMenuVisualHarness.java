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
    private record Scene(int width, int height, int density, boolean background, String name,
                         MainMenuState state, double transitionMs, double playbackMs, float amplitude) { }
    private static final class FixedAnalysis implements MenuAudioAnalysis {
        private final float amplitude;
        private final float[] bins = new float[1024];
        FixedAnalysis(float amplitude) {
            this.amplitude = amplitude;
            for (int i = 0; i < bins.length; i++) bins[i] = (float) ((.005 + amplitude * .18) * Math.exp(-i / 90.0)
                    * (.5 + .3 * Math.sin(i * .137) + .2 * Math.cos(i * .071)));
        }
        public float maximumAmplitude() { return amplitude; }
        public float[] frequencyAmplitudes() { return bins; }
        public boolean available() { return true; }
    }
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
            for (int[] size : new int[][]{{1024,768,1},{1280,720,1},{1366,768,1},{1920,1080,1},{2560,1440,1},{600,800,1},{1280,720,2},{600,800,2}})
                for (boolean bg : new boolean[]{false,true}) {
                    for (String name : new String[]{"closed", "beat-phase-0", "beat-impact", "between-beats", "audio-low", "audio-high", "visualiser-active", "logo-pressed", "closed-hover", "parallax-top-left", "parallax-bottom-right"}) {
                        double playback = switch(name) { case "beat-phase-0" -> 0; case "beat-impact" -> 1000; case "between-beats" -> 1250; default -> 1170; };
                        float amplitude = (name.equals("audio-high") || name.equals("beat-impact")) ? .95f : name.equals("audio-low") ? .03f : .4f;
                        scenes.add(new Scene(size[0],size[1],size[2],bg,name,MainMenuState.CLOSED,0,playback,amplitude));
                    }
                    for (int ms : new int[]{2000,2083,2167,2250,2333,2417})
                        scenes.add(new Scene(size[0],size[1],size[2],bg,"idle-cycle-"+ms,MainMenuState.CLOSED,0,ms,.4f));
                    for (int ms : new int[]{0,100,200,190,380})
                        scenes.add(new Scene(size[0],size[1],size[2],bg,"opening-"+ms,MainMenuState.OPENING,ms,1170,.4f));
                    for (String name : new String[]{"open-idle","play-hover","exit-hover","logo-hover"})
                        scenes.add(new Scene(size[0],size[1],size[2],bg,name,MainMenuState.OPEN,0,1170,.4f));
                    for (String name : new String[]{"frame-closed-long-title", "frame-open-long-title", "frame-opening-midpoint", "frame-no-track", "frame-selected-track", "music-paused", "music-single-track", "music-unavailable", "music-previous-hover", "music-pause-hover", "music-next-hover"})
                        scenes.add(new Scene(size[0],size[1],size[2],bg,name,
                                name.contains("opening") ? MainMenuState.OPENING : name.contains("open-long") ? MainMenuState.OPEN : MainMenuState.CLOSED,
                                name.contains("opening") ? 100 : 0,1170,.4f));
                    for (int ms : new int[]{150,300})
                        scenes.add(new Scene(size[0],size[1],size[2],bg,"closing-"+ms,MainMenuState.CLOSING,ms,1170,.4f));
                }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private BeatmapSet set(Scene scene) {
        Path bg = scene.background ? artwork : null;
        String title = scene.name.startsWith("frame-")
                ? "An extraordinarily long local track title — 夜空の向こうへ — with enough metadata to require an ellipsis even on a wide desktop window"
                : "A long ambient title for responsive typography";
        var diff = new BeatmapDifficulty(title, "Local artist", "Harness", "Normal", 0, "", "",
                new DifficultySettings(5,5,5,5,1.4,1), List.of(new TimingPoint(0,500,4,0,0,100,true,1)), List.of(), null, bg);
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
        var screen = new MainMenuScreen(game,scene.name.equals("frame-no-track") ? null : set(scene),new FixedAnalysis(scene.amplitude), () -> {}, () -> {});
        try {
            MainMenuLayout m = MainMenuLayout.from(UiLayout.fromPixels(scene.width,scene.height));
            float x = -100, y = -100;
            if (scene.name.equals("logo-hover") || scene.name.equals("logo-pressed") || scene.name.equals("closed-hover")) { x = m.cx(); y = m.cy(); }
            if (scene.name.equals("parallax-top-left")) { x = 0; y = m.height(); }
            if (scene.name.equals("parallax-bottom-right")) { x = m.width(); y = 0; }
            if (scene.name.equals("play-hover") || scene.name.equals("exit-hover")) {
                int button = scene.name.equals("play-hover") ? 0 : 1;
                x = m.cx() + m.direction(button) * m.targetWidth() * .85f; y = m.cy();
            }
            if (scene.name.endsWith("-hover") && scene.name.startsWith("music-")) {
                var frame = MainMenuFrame.layout(UiLayout.fromPixels(scene.width,scene.height));
                int control = scene.name.contains("previous") ? 0 : scene.name.contains("pause") ? 1 : 2;
                x = MainMenuFrame.controlX(frame,control) + 13 * frame.unit();
                y = MainMenuFrame.controlY(frame) + 13 * frame.unit();
            }
            byte[] first = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                fb.begin();
                screen.capture(scene.state,scene.transitionMs,scene.playbackMs,x,y,scene.name.equals("logo-pressed"),
                        new MainMenuFrame.Info(scene.name.equals("frame-no-track") ? 0 : 12345,359999,"18:20",MainMenuFrame.version(),
                                !scene.name.equals("frame-no-track") && !scene.name.equals("frame-selected-track")
                                        && !scene.name.equals("music-paused") && !scene.name.equals("music-unavailable"),
                                scene.name.equals("music-paused"), !scene.name.equals("frame-no-track") && !scene.name.equals("music-single-track")));
                Pixmap capture = Pixmap.createFromFrameBuffer(0,0,fb.getWidth(),fb.getHeight());
                byte[] pixels = new byte[capture.getPixels().remaining()]; capture.getPixels().duplicate().get(pixels);
                String name = scene.width + "x" + scene.height + "-" + scene.density + "x-" + (scene.background ? "artwork-" : "fallback-") + scene.name;
                if (attempt == 0) {
                    first = pixels;
                    PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),capture,-1,true);
                    if (scene.name.startsWith("frame-") || scene.name.startsWith("music-")) {
                        var frame = MainMenuFrame.layout(UiLayout.fromPixels(scene.width,scene.height));
                        float pixelScale = UiLayout.fromPixels(scene.width,scene.height).scale() * scene.density;
                        captureBar(capture,name + "-top",Math.round(frame.topHeight() * pixelScale),true);
                        captureBar(capture,name + "-bottom",Math.round(frame.bottomHeight() * pixelScale),false);
                    }
                }
                else if (!Arrays.equals(first,pixels)) throw new AssertionError("Pixels differ: " + name);
                capture.dispose(); fb.end();
            }
            if (scene.name.equals("open-idle") && scene.background) { checkInteraction(scene); checkMusicInteraction(scene); }
        } finally { screen.dispose(); fb.dispose(); Gdx.graphics = actual; }
        if (++index == scenes.size()) {
            System.out.println("Main Menu harness: " + index + " captures, all repeated framebuffer bytes identical; 80 navigation and 8 music Screen interaction sequences passed: " + output);
            Gdx.app.exit();
        }
    }
    private void captureBar(Pixmap capture, String name, int height, boolean top) {
        var bar = new Pixmap(capture.getWidth(),height,Pixmap.Format.RGBA8888);
        bar.setBlending(Pixmap.Blending.None);
        bar.drawPixmap(capture,0,top ? capture.getHeight() - height : 0,capture.getWidth(),height,0,0,capture.getWidth(),height);
        PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name + ".png").toString()),bar,-1,true);
        bar.dispose();
    }
    private void checkMusicInteraction(Scene scene) {
        var library = new BeatmapLibrary();
        var first = set(scene);
        var difficulty = new BeatmapDifficulty("Next local song","Next artist","Harness","Normal",0,"","",
                new DifficultySettings(5,5,5,5,1.4,1),List.of(new TimingPoint(0,250,4,0,0,100,true,0)),List.of(),null,null);
        var second = new BeatmapSet("next",difficulty.title(),difficulty.artist(),"Harness",Path.of("next.ogg"),null,List.of(difficulty),List.of());
        library.add(first); library.add(second);
        var musicGame = new OsuJavaGame(null) {
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
            @Override public BitmapFont font() { return font; }
            @Override public SmoothUiFont smoothFont() { return smooth; }
            @Override public BeatmapLibrary library() { return library; }
        };
        // Give the first track a synthetic path; injected Music never reads files or uses the sound device.
        first = new BeatmapSet(first.id(),first.title(),first.artist(),first.creator(),Path.of("first.ogg"),first.backgroundPath(),first.difficulties(),first.assets());
        library.add(first);
        int[] streams = {0}, disposals = {0}, calls = {0}; float[] position = {0};
        var screen = new MainMenuScreen(musicGame,first,new FixedAnalysis(.4f),() -> calls[0]++,() -> calls[0]++,path -> {
            streams[0]++; position[0] = 0;
            boolean[] dead = {false};
            return new MenuAmbientAudio(path,p -> (com.badlogic.gdx.audio.Music) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.audio.Music.class.getClassLoader(),new Class[]{com.badlogic.gdx.audio.Music.class},(proxy,method,args) -> {
                        if (dead[0]) throw new AssertionError("Disposed music accessed");
                        if (method.getName().equals("dispose")) { dead[0] = true; disposals[0]++; }
                        return method.getName().equals("getPosition") ? position[0] : null;
                    }));
        });
        Input actual = Gdx.input;
        var ui = UiLayout.fromPixels(scene.width,scene.height); var frame = MainMenuFrame.layout(ui);
        float[] pointer = {-100,-100}; boolean[] down = {false};
        Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},
                (proxy,method,args) -> switch (method.getName()) {
                    case "getX" -> Math.round(pointer[0] * ui.scale());
                    case "getY" -> Math.round((ui.height() - pointer[1]) * ui.scale());
                    case "isButtonPressed" -> down[0];
                    case "setInputProcessor" -> null;
                    default -> method.invoke(actual,args);
                });
        java.util.function.IntConsumer click = control -> {
            pointer[0] = MainMenuFrame.controlX(frame,control) + 13 * frame.unit();
            pointer[1] = MainMenuFrame.controlY(frame) + 13 * frame.unit();
            down[0] = true; screen.render(0); down[0] = false; screen.render(0);
        };
        try {
            screen.show(); screen.render(0); position[0] = 1.25f;
            if (!screen.hasTrackArtwork() || !screen.trackBpm().equals("120 BPM")) throw new AssertionError("Initial track assets missing");
            click.accept(1);
            if (!screen.musicPaused() || screen.musicPositionMs() != 1250) throw new AssertionError("Pause failed");
            screen.render(.5f);
            if (screen.musicPositionMs() != 1250) throw new AssertionError("Paused clock advanced");
            click.accept(2);
            if (!screen.trackTitle().equals("Next artist — Next local song") || !screen.musicPaused() || screen.musicPositionMs() != 0)
                throw new AssertionError("Paused next failed");
            if (!screen.trackBpm().equals("240 BPM") || screen.hasTrackArtwork()) throw new AssertionError("Displayed timing/artwork did not switch");
            click.accept(1); if (screen.musicPaused()) throw new AssertionError("Resume failed");
            click.accept(2);
            if (screen.trackTitle().equals("Next artist — Next local song") || !screen.trackBpm().equals("120 BPM") || !screen.hasTrackArtwork())
                throw new AssertionError("Next did not wrap with initial assets");
            click.accept(0); if (!screen.trackTitle().equals("Next artist — Next local song")) throw new AssertionError("Previous did not wrap");
            int created = streams[0];
            pointer[0] = MainMenuFrame.controlX(frame,0) + 13 * frame.unit();
            down[0] = true; screen.render(0); pointer[0] = -100; down[0] = false; screen.render(0);
            if (streams[0] != created) throw new AssertionError("Release outside control was not cancelled");
            if (calls[0] != 0 || screen.menuState() != MainMenuState.CLOSED) throw new AssertionError("Music controls navigated or opened menu");
        } finally { screen.dispose(); Gdx.input = actual; }
        if (disposals[0] != streams[0]) throw new AssertionError("Music stream leaked");
    }
    /** Full Screen input/render path with scripted physical pointer and key input. */
    private void checkInteraction(Scene scene) {
        Input actual = Gdx.input;
        UiLayout ui = UiLayout.fromPixels(scene.width, scene.height);
        MainMenuLayout m = MainMenuLayout.from(ui);
        for (int action = 0; action < 10; action++) {
            int[] calls = new int[2];
            var screen = new MainMenuScreen(game,set(scene),new FixedAnalysis(.4f), () -> calls[0]++, () -> calls[1]++);
            float[] pointer = {m.cx(),m.cy()}; boolean[] down = {false};
            InputProcessor[] processor = {null};
            Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> Math.round(pointer[0] * ui.scale());
                        case "getY" -> Math.round((ui.height() - pointer[1]) * ui.scale());
                        case "isButtonPressed" -> down[0];
                        case "setInputProcessor" -> { processor[0] = (InputProcessor) args[0]; yield null; }
                        default -> method.invoke(actual, args);
                    });
            try {
                screen.show(); screen.render(0);
                if (action <= 2 || action == 7 || action == 8 || action == 9) {
                    down[0] = true; screen.render(0);
                    if (action == 9) pointer[0] = -100; // Releasing outside the logo cancels the click.
                    down[0] = false; screen.render(0);
                    if (action != 8) screen.render(.4f);
                }
                switch(action) {
                    case 0 -> { // Logo opens then closes, without navigating.
                        if (screen.menuState() != MainMenuState.OPEN) throw new AssertionError("Logo did not open");
                        down[0] = true; screen.render(0); down[0] = false; screen.render(0); screen.render(.3f);
                    }
                    case 1,2 -> {
                        int button = action - 1;
                        pointer[0] = m.cx() + m.direction(button) * m.targetWidth() * .85f;
                        down[0] = true; screen.render(0); down[0] = false; screen.render(0);
                    }
                    case 3,4,5 -> processor[0].keyDown(new int[]{Input.Keys.P,Input.Keys.ENTER,Input.Keys.SPACE}[action-3]);
                    case 6,7,8 -> processor[0].keyDown(Input.Keys.ESCAPE);
                    default -> { }
                }
                screen.render(.3f);
                int exit = action == 2 || action == 6 ? 1 : 0;
                int play = action == 1 || action >= 3 && action <= 5 ? 1 : 0;
                if (calls[1] != exit || calls[0] != play) throw new AssertionError("Screen interaction failed: " + action + " at " + scene);
                if ((action == 0 || action == 7 || action == 8 || action == 9) && screen.menuState() != MainMenuState.CLOSED)
                    throw new AssertionError("Menu did not close: " + action);
            } finally { screen.dispose(); Gdx.input = actual; }
        }
    }
    @Override public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); smooth.close(); }
}
