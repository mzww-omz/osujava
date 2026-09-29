package dev.osujava.ui;

import dev.osujava.score.LocalScore;
import dev.osujava.score.ResultsSnapshot;
import dev.osujava.score.DifficultyIdentity;
import java.util.UUID;
import java.time.Instant;

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
import dev.osujava.ruleset.osu.render.LegacyCursorVisual;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.ElapsedGameClock;
import dev.osujava.gameplay.GameClock;
import dev.osujava.gameplay.GameplaySession;
import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.GameplayVisualConfig;
import dev.osujava.gameplay.MusicGameClock;
import dev.osujava.gameplay.GameplayCompletion;
import dev.osujava.ruleset.osu.SliderPath;
import dev.osujava.ruleset.osu.SliderTiming;
import dev.osujava.ruleset.osu.DebugAutoPlayer;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.SkinAssetResolver;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GameplayScreen extends ScreenAdapter {
    private final OsuJavaGame game;
    private final BeatmapSet set;
    private final BeatmapDifficulty difficulty;
    private final GameClock clock;
    private final GameplayCompletion completion;
    private final GameplaySession session;
    private final GameplayRunMode runMode;
    private final UUID playId = UUID.randomUUID();
    private boolean resultFinalized;
    private final DebugAutoPlayer autoPlayer;
    private final GameplayRenderer renderer;
    private final GameplayCursorRenderer cursorRenderer;
    private final LegacyCursorVisual cursorVisual;
    private final GameplayCursorVisibility cursorVisibility;
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
        this.skinAssets = new OsuSkinAssets(game.skinDirectory(), game.skinFallbackDirectory());
        this.renderer = new GameplayRenderer(game, GameplayVisualConfig.defaults(), skinAssets);
        this.cursorRenderer = new GameplayCursorRenderer(skinAssets);
        this.cursorVisual = cursorRenderer.createVisual();
        this.cursorVisibility = new GameplayCursorVisibility(Gdx.graphics);
        this.audioPlayer = new GameplayAudioPlayer(difficulty.beatmapPath() == null
                ? null : difficulty.beatmapPath().getParent(),
                SkinAssetResolver.withBundledDefault(game.skinDirectory(), game.skinFallbackDirectory()), game.audioVolumes());
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
        this.completion = new GameplayCompletion(finishAt);
        int leadIn = difficulty.playData().audioLeadInMs();

        Music loadedMusic = null;
        GameClock selectedClock;
        String audioNotice = "";
        Path audioPath = difficulty.audioPath() != null ? difficulty.audioPath() : set.audioPath();
        if (audioPath != null && Files.isRegularFile(audioPath)) {
            try {
                loadedMusic = Gdx.audio.newMusic(Gdx.files.absolute(audioPath.toString()));
                game.audioVolumes().musicGain(loadedMusic, 1);
                MusicGameClock musicClock = new MusicGameClock(loadedMusic, leadIn);
                musicClock.start();
                selectedClock = musicClock;
            } catch (GdxRuntimeException | IllegalArgumentException e) {
                if (loadedMusic != null) {
                    game.audioVolumes().removeMusic(loadedMusic);
                    loadedMusic.dispose();
                    loadedMusic = null;
                }
                audioNotice = "Audio could not be opened; running with a local timer.";
                selectedClock = new ElapsedGameClock(finishAt, leadIn);
            }
        } else {
            audioNotice = "No audio file was found; running with a local timer.";
            selectedClock = new ElapsedGameClock(finishAt, leadIn);
        }
        this.music = loadedMusic;
        this.clock = selectedClock;
        this.notice = audioNotice;
        this.session = new CursorTrackingSession(game.osuRuleset().createSession(difficulty, clock), clock, cursorVisual);
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
        cursorVisibility.show();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (autoPlayer != null) cursorVisual.move(clock.nowMs(), autoPlayer.cursorX(), autoPlayer.cursorY());
    }

    @Override
    public void hide() { cursorVisibility.hide(); }

    @Override
    public void resize(int width, int height) {
        viewport = PlayfieldViewport.fit(width, height);
        input.setViewport(viewport);
        if (autoPlayer == null) input.mouseMoved(Gdx.input.getX(), Gdx.input.getY());
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
        if (completion.ready(state) && !resultFinalized) {
            resultFinalized = true;
            var snapshot = new ResultsSnapshot(state.score(), session.resultDetails(), Instant.now().toEpochMilli(), runMode, false);
            var identity = DifficultyIdentity.of(set.id(), difficulty);
            if (identity != null && runMode == GameplayRunMode.MANUAL)
                game.localScores().save(new LocalScore(playId, identity,
                        snapshot.playedAt(), snapshot.score(), snapshot.details()), runMode);
            game.navigate(new ResultsScreen(game, set, difficulty, snapshot));
            return;
        }
        renderer.render(set, difficulty, state, viewport, background, notice);
        cursorVisual.advance(state.currentTimeMs());
        cursorRenderer.draw(game.batch(), game.shapes(), cursorVisual, state.currentTimeMs(), viewport);
        transitionView.prepare();
        transitionView.fade(entrance, delta);
    }

    @Override
    public void dispose() {
        cursorVisibility.close();
        renderer.dispose();
        skinAssets.dispose();
        audioPlayer.close();
        if (music != null) {
            game.audioVolumes().removeMusic(music);
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
