package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Matrix4;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;
import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ElapsedGameClock;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.GameplayVisualConfig;
import dev.osujava.gameplay.MusicGameClock;
import dev.osujava.ruleset.osu.SliderPath;
import dev.osujava.ruleset.osu.SliderTiming;
import dev.osujava.ruleset.osu.DebugAutoPlayer;
import dev.osujava.skin.OsuSkinAssets;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GameplayScreen extends ScreenAdapter {
    private final OsuJavaGame game;
    private final BeatmapSet set;
    private final BeatmapDifficulty difficulty;
    private final GameClock clock;
    private final GameplaySession session;
    private final GameplayRunMode runMode;
    private final DebugAutoPlayer autoPlayer;
    private final GameplayRenderer renderer;
    private final OsuSkinAssets skinAssets;
    private final GameplayAudioPlayer audioPlayer;
    private final GameplayInputProcessor input;
    private final Music music;
    private final Texture background;
    private final String notice;
    private PlayfieldViewport viewport;
    private final UiView transitionView;
    private final UiTransition entrance = new UiTransition();
    private final Matrix4 pixelProjection = new Matrix4();

    public GameplayScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty) {
        this(game, set, difficulty, GameplayRunMode.MANUAL);
    }

    public GameplayScreen(OsuJavaGame game, BeatmapSet set, BeatmapDifficulty difficulty, GameplayRunMode runMode) {
        this.game = game;
        this.transitionView = new UiView(game);
        this.set = set;
        this.difficulty = difficulty;
        this.runMode = runMode;
        this.skinAssets = new OsuSkinAssets(game.skinDirectory());
        this.renderer = new GameplayRenderer(game, GameplayVisualConfig.defaults(), skinAssets);
        this.audioPlayer = new GameplayAudioPlayer(difficulty.beatmapPath() == null
                ? null : difficulty.beatmapPath().getParent());
        long lastObjectEnd = 0;
        for (HitObject object : difficulty.hitObjects()) {
            if (object.type() == HitObject.Type.CIRCLE) {
                lastObjectEnd = Math.max(lastObjectEnd, object.timeMs());
            } else if (object.type() == HitObject.Type.SLIDER && object.sliderData() != null) {
                SliderPath path = new SliderPath(object.x(), object.y(), object.sliderData());
                SliderTiming timing = SliderTiming.calculate(difficulty, object, path);
                lastObjectEnd = Math.max(lastObjectEnd, (long) Math.ceil(timing.endTimeMs()));
            } else if (object.type() == HitObject.Type.SPINNER) {
                lastObjectEnd = Math.max(lastObjectEnd, (long) Math.ceil(object.endTimeMs()));
            }
        }
        long finishAt = lastObjectEnd + 2500;

        Music loadedMusic = null;
        GameClock selectedClock;
        String audioNotice = "";
        Path audioPath = difficulty.audioPath() != null ? difficulty.audioPath() : set.audioPath();
        if (audioPath != null && Files.isRegularFile(audioPath)) {
            try {
                loadedMusic = Gdx.audio.newMusic(Gdx.files.absolute(audioPath.toString()));
                MusicGameClock musicClock = new MusicGameClock(loadedMusic);
                musicClock.start();
                selectedClock = musicClock;
            } catch (GdxRuntimeException | IllegalArgumentException e) {
                audioNotice = "Audio could not be opened; running with a local timer.";
                selectedClock = new ElapsedGameClock(finishAt);
            }
        } else {
            audioNotice = "No audio file was found; running with a local timer.";
            selectedClock = new ElapsedGameClock(finishAt);
        }
        this.music = loadedMusic;
        this.clock = selectedClock;
        this.notice = audioNotice;
        this.session = game.osuRuleset().createSession(difficulty, clock);
        this.input = new GameplayInputProcessor(session, () -> game.navigate(
                new SongSelectScreen(game, set.id(), set.difficulties().indexOf(difficulty))),
                runMode == GameplayRunMode.MANUAL);
        this.autoPlayer = runMode == GameplayRunMode.DEBUG_AUTO
                ? new DebugAutoPlayer(difficulty, clock, session) : null;
        this.background = loadBackground(difficulty.backgroundPath() != null ? difficulty.backgroundPath() : set.backgroundPath());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        pixelProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        game.batch().setProjectionMatrix(pixelProjection);
        game.shapes().setProjectionMatrix(pixelProjection);
        viewport = PlayfieldViewport.fit(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        input.setViewport(viewport);
        if (autoPlayer != null) autoPlayer.update();
        GameplayState state = session.update();
        audioPlayer.play(session.drainAudioCues());
        if (autoPlayer != null) autoPlayer.afterSessionUpdate();
        if (clock.finished()) {
            session.finish();
            game.navigate(new ResultsScreen(game, set, difficulty, session.state().score(), runMode));
            return;
        }
        renderer.render(set, difficulty, state, viewport, background, notice,
                autoPlayer == null ? null : new BeatmapPoint(autoPlayer.cursorX(), autoPlayer.cursorY()));
        transitionView.prepare();
        transitionView.fade(entrance, delta);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        skinAssets.dispose();
        audioPlayer.close();
        if (music != null) {
            music.stop();
            music.dispose();
        }
        if (background != null) background.dispose();
    }

    private Texture loadBackground(Path path) {
        if (path == null || !Files.isRegularFile(path)) return null;
        try {
            Texture texture = new Texture(Gdx.files.absolute(path.toString()));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } catch (GdxRuntimeException ignored) {
            return null;
        }
    }
}
