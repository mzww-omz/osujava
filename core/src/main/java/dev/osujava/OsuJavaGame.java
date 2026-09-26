package dev.osujava;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.library.BeatmapArchiveImporter;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.library.PropertiesBeatmapLibraryStorage;
import dev.osujava.ruleset.osu.OsuRuleset;
import dev.osujava.ui.BeatmapFileChooser;
import dev.osujava.ui.MainMenuScreen;
import dev.osujava.ui.theme.SmoothUiFont;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class OsuJavaGame extends Game {
    private final BeatmapFileChooser fileChooser;
    private final Path skinDirectory;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SmoothUiFont smoothFont;
    private BeatmapLibrary library;
    private BeatmapArchiveImporter importer;
    private OsuRuleset osuRuleset;

    public OsuJavaGame(BeatmapFileChooser fileChooser) {
        this(fileChooser, configuredSkinDirectory());
    }

    public OsuJavaGame(BeatmapFileChooser fileChooser, Path skinDirectory) {
        this.fileChooser = fileChooser;
        this.skinDirectory = skinDirectory;
    }

    public Path skinDirectory() { return skinDirectory; }

    private static Path configuredSkinDirectory() {
        String value = System.getProperty("osujava.skinDirectory");
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        smoothFont = new SmoothUiFont();
        Path libraryRoot = Path.of(System.getProperty("user.home", "."), ".osujava", "library");
        library = new BeatmapLibrary(new PropertiesBeatmapLibraryStorage(libraryRoot));
        importer = new BeatmapArchiveImporter(libraryRoot);
        osuRuleset = new OsuRuleset();
        navigate(new MainMenuScreen(this));
    }

    public void navigate(Screen next) {
        Screen previous = getScreen();
        super.setScreen(next);
        if (previous != null && previous != next) previous.dispose();
    }

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
