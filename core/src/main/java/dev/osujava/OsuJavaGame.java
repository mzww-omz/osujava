package dev.osujava;

import dev.osujava.score.LocalScoreStore;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.audio.AudioVolumes;
import dev.osujava.library.BeatmapArchiveImporter;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.library.PropertiesBeatmapLibraryStorage;
import dev.osujava.ruleset.osu.OsuRuleset;
import dev.osujava.skin.SkinImporter;
import dev.osujava.skin.SkinImportException;
import dev.osujava.ui.BeatmapFileChooser;
import dev.osujava.ui.MainMenuScreen;
import dev.osujava.ui.SongSelectScreen;
import dev.osujava.ui.SongSelectWheelInput;
import dev.osujava.ui.VolumeHud;
import dev.osujava.ui.VolumeHudInput;
import dev.osujava.ui.VolumeHudRenderer;
import dev.osujava.ui.theme.SmoothUiFont;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class OsuJavaGame extends Game {
    private final long sessionStartNanos = System.nanoTime();
    private final BeatmapFileChooser fileChooser;
    private final Path skinDirectory;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SmoothUiFont smoothFont;
    private BeatmapLibrary library;
    private LocalScoreStore localScores = new LocalScoreStore();
    private dev.osujava.score.LocalPlayer localPlayer;
    private dev.osujava.score.LocalPlayHistory playHistory = new dev.osujava.score.LocalPlayHistory();
    private dev.osujava.collection.LocalCollectionStore collections = new dev.osujava.collection.LocalCollectionStore();
    private final java.time.Clock wallClock = java.time.Clock.systemDefaultZone();
    private BeatmapArchiveImporter importer;
    private OsuRuleset osuRuleset;
    private final AudioVolumes audioVolumes = new AudioVolumes();
    private final VolumeHud volumeHud = new VolumeHud(audioVolumes);
    private final VolumeHudInput volumeInput = new VolumeHudInput(volumeHud,
            () -> getScreen() instanceof SongSelectScreen songs
                    && songs.usesMouseWheelAt(Gdx.input.getX(), Gdx.input.getY()),
            () -> Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT));
    private VolumeHudRenderer volumeRenderer;

    public OsuJavaGame(BeatmapFileChooser fileChooser) {
        this(fileChooser, configuredSkinDirectory());
    }

    public OsuJavaGame(BeatmapFileChooser fileChooser, Path skinDirectory) {
        this.fileChooser = fileChooser;
        this.skinDirectory = skinDirectory;
    }

    public Path skinDirectory() { return skinDirectory; }

    /** Desktop supplies decoding; the core and renderer never depend on a platform codec. */
    public dev.osujava.ui.MenuAudioAnalysis createMenuAudioAnalysis() {
        return new dev.osujava.ui.DeterministicMenuAudioFallback();
    }

    /** Application lifetime, including time spent away from the Main Menu. */
    public long sessionUptimeSeconds() { return Math.max(0, (System.nanoTime() - sessionStartNanos) / 1_000_000_000L); }

    public Path skinFallbackDirectory() {
        String value = System.getProperty("osujava.skinFallbackDirectory");
        if (value == null || value.isBlank()) return null;
        try { return Path.of(value); }
        catch (InvalidPathException ignored) { return null; }
    }

    private static Path configuredSkinDirectory() {
        String archive = System.getProperty("osujava.skinArchive");
        if (archive != null && !archive.isBlank()) {
            try {
                return new SkinImporter(localDataRoot().resolve("skins")).importFile(Path.of(archive));
            } catch (SkinImportException | InvalidPathException e) {
                System.err.println("Could not import configured skin: " + e.getMessage());
            }
        }
        String value = System.getProperty("osujava.skinDirectory");
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static Path localDataRoot() {
        return Path.of(System.getProperty("user.home", "."), ".osujava");
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        smoothFont = new SmoothUiFont();
        volumeRenderer = new VolumeHudRenderer(this);
        Path libraryRoot = localDataRoot().resolve("library");
        library = new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(libraryRoot));
        importer = new BeatmapArchiveImporter(libraryRoot);
        localScores = new LocalScoreStore(localDataRoot().resolve("scores"));
        localPlayer = dev.osujava.score.LocalPlayerProfile.load(localDataRoot().resolve("player.properties"),
                System.getProperty("osujava.playerName"));
        playHistory = new dev.osujava.score.LocalPlayHistory(localDataRoot().resolve("history"));
        collections = new dev.osujava.collection.LocalCollectionStore(localDataRoot().resolve("collections.properties"));
        osuRuleset = new OsuRuleset();
        navigate(new MainMenuScreen(this));
    }

    public void navigate(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null && previous != next) previous.dispose();
    }

    @Override public void setScreen(Screen next) {
        super.setScreen(next);
        installGlobalInput();
    }

    private void installGlobalInput() {
        InputProcessor screenInput = Gdx.input.getInputProcessor();
        if (screenInput == volumeInput || screenInput instanceof InputMultiplexer multiplexer
                && multiplexer.getProcessors().contains(volumeInput, true)) return;
        // Route volume through Song Select's wheel poll, before key repeat and mouse gestures.
        if (screenInput instanceof SongSelectWheelInput wheel) wheel.addProcessor(0, volumeInput);
        else Gdx.input.setInputProcessor(screenInput == null ? volumeInput : new InputMultiplexer(volumeInput, screenInput));
    }

    @Override public void render() {
        volumeHud.advance(Gdx.graphics.getDeltaTime());
        super.render();
        if (volumeRenderer != null) volumeRenderer.draw(volumeHud);
    }

    @Override public void resume() { super.resume(); installGlobalInput(); }
    public AudioVolumes audioVolumes() { return audioVolumes; }
    public VolumeHud volumeHud() { return volumeHud; }

    public SpriteBatch batch() {
        return batch;
    }

    public ShapeRenderer shapes() {
        return shapes;
    }

    public BitmapFont font() {
        return font;
    }

    public SmoothUiFont smoothFont() { return smoothFont; }

    public LocalScoreStore localScores() { return localScores; }
    public dev.osujava.score.LocalPlayer localPlayer() { return localPlayer; }
    public dev.osujava.score.LocalPlayHistory playHistory() { return playHistory; }
    public dev.osujava.collection.LocalCollectionStore collections() { return collections; }
    public java.time.Clock wallClock() { return wallClock; }

    public BeatmapLibrary library() {
        return library;
    }

    public BeatmapArchiveImporter importer() {
        return importer;
    }

    public BeatmapFileChooser fileChooser() {
        return fileChooser;
    }

    public OsuRuleset osuRuleset() {
        return osuRuleset;
    }

    @Override
    public void dispose() {
        Screen current = getScreen();
        super.dispose();
        if (current != null) current.dispose();
        batch.dispose();
        shapes.dispose();
        font.dispose();
        smoothFont.close();
    }
}
