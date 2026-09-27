package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import dev.osujava.OsuJavaGame;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.SkinAssetResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Opt-in real GameplayScreen/Debug Auto check; no gameplay state or clock manipulation. */
public final class BundledSkinVisualHarness extends OsuJavaGame {
    private final Path output;
    private long started;
    private int capture;
    private final long[] captureAt = {900, 1650, 2900, 3650, 5750, 7350, 8750};

    private BundledSkinVisualHarness(Path output) { super(null, null); this.output = output; }

    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"))
            Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java bundled skin Debug Auto verification");
        config.setWindowedMode(1100, 720);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new BundledSkinVisualHarness(Path.of(args[0])), config);
    }

    @Override public void create() {
        super.create();
        try {
            Files.createDirectories(output);
            var resolver = SkinAssetResolver.withBundledDefault(null, null);
            var assets = new OsuSkinAssets(null);
            for (var image : List.of(OsuSkinAssets.Image.HIT_CIRCLE, OsuSkinAssets.Image.APPROACH_CIRCLE,
                    OsuSkinAssets.Image.CURSOR, OsuSkinAssets.Image.SLIDER_FOLLOW_CIRCLE,
                    OsuSkinAssets.Image.REVERSE_ARROW, OsuSkinAssets.Image.SPINNER_BACKGROUND))
                if (assets.diagnostic(image).status() != OsuSkinAssets.LoadStatus.LOADED)
                    throw new IllegalStateException("Missing bundled texture: " + image);
            if (!assets.hasHitCircleDigits() || assets.sliderBallFrames().isEmpty())
                throw new IllegalStateException("Missing bundled font/ball");
            assets.dispose();
            for (String name : List.of("taikohitcircle", "taikobigcircle", "taiko-drum-inner", "taiko-hit300",
                    "fruit-apple", "fruit-drop", "fruit-bananas-overlay")) {
                Pixmap image = new Pixmap(resolver.resolve(name).orElseThrow().handle());
                image.dispose();
            }
            for (String bank : List.of("normal", "soft", "drum")) {
                for (String sample : List.of("hitnormal", "hitclap", "hitfinish", "hitwhistle", "slidertick")) {
                    var sound = Gdx.audio.newSound(resolver.resolveSound(bank + "-" + sample).orElseThrow().handle());
                    sound.dispose();
                }
            }
            Path fixture = output.resolve("skin-check.osu");
            Files.writeString(fixture, """
                    osu file format v14
                    [General]
                    AudioFilename:
                    Mode: 0
                    [Metadata]
                    Title:Bundled Skin Check
                    Artist:Local Fixture
                    Creator:osujava
                    Version:Debug Auto
                    [Difficulty]
                    HPDrainRate:5
                    CircleSize:5
                    OverallDifficulty:5
                    ApproachRate:5
                    SliderMultiplier:1.4
                    SliderTickRate:1
                    [TimingPoints]
                    0,500,4,1,1,100,1,0
                    [HitObjects]
                    100,100,1500,1,0,0:0:0:0:
                    350,100,2200,5,0,0:0:0:0:
                    100,240,3200,6,0,L|380:240,2,280
                    256,192,5500,12,0,8500,0:0:0:0:
                    """);
            var set = importer().importFile(fixture).beatmapSet();
            navigate(new GameplayScreen(this, set, set.difficulties().getFirst(), GameplayRunMode.DEBUG_AUTO));
            started = System.nanoTime(); // Capture schedule only; GameplayScreen owns its usual GameClock.
            System.out.println("SKIN CHECK: textures/fonts, taiko/catch image decode and 15 skin WAVs loaded; resource="
                    + BundledSkinVisualHarness.class.getResource("/skins/default/skin.ini"));
        } catch (Exception e) { throw new IllegalStateException("Bundled skin visual check failed", e); }
    }

    @Override public void render() {
        super.render();
        if (capture == captureAt.length) { Gdx.app.exit(); return; }
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (elapsed < captureAt[capture]) return;
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(Gdx.files.absolute(output.resolve("standard-" + captureAt[capture] + "ms.png").toString()), frame, -1, true);
        } finally { frame.dispose(); }
        capture++;
    }
}
