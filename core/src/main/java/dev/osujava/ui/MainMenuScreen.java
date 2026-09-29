package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.ui.theme.*;
import java.util.List;
import java.util.random.RandomGenerator;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.function.Function;

/** Music-first local menu. Owns a local track queue and one active stream. */
public final class MainMenuScreen extends ScreenAdapter {
    private final UiView view;
    private final OsuJavaGame game;
    private final MainMenuFrame frame = new MainMenuFrame();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final MainMenuLogo logo = new MainMenuLogo();
    private final BeatmapBackdrop artwork = new BeatmapBackdrop();
    private final MenuMusicPlayer audio;
    private final MenuAudioAnalysis analysis;
    private final MenuAudioAnalysis silence = new DeterministicMenuAudioFallback();
    private double idleMs;
    private List<TimingPoint> timing = List.of();
    private final MenuVisualiser visualiser = new MenuVisualiser();
    private final MainMenuModel model = new MainMenuModel();
    private final MainMenuInput input;
    private String title = "";
    private MenuBeatTiming.Beat beat = MenuBeatTiming.at(List.of(), 0, false);
    private boolean pointerWasDown, disposed;
    private int pressedTarget = -1;

    public MainMenuScreen(OsuJavaGame game) {
        this(game, AmbientArtworkSelection.choose(game.library().all(), RandomGenerator.getDefault()),
                game.createMenuAudioAnalysis(), () -> game.navigate(new SongSelectScreen(game)), () -> Gdx.app.exit());
    }
    /** Harness injects fixed amplitudes; decoding never enters the renderer. */
    MainMenuScreen(OsuJavaGame game, BeatmapSet ambient, MenuAudioAnalysis analysis, Runnable play, Runnable exit) {
        this(game,ambient,analysis,play,exit,path -> new MenuAmbientAudio(path, game.audioVolumes()));
    }
    MainMenuScreen(OsuJavaGame game, BeatmapSet ambient, MenuAudioAnalysis analysis, Runnable play, Runnable exit,
                   Function<Path, MenuAmbientAudio> audioFactory) {
        this.game = game; view = new UiView(game); this.analysis = analysis;
        audio = new MenuMusicPlayer(game.library().all(),ambient,audioFactory);
        selectTrack();
        input = new MainMenuInput(model, () -> { audio.close(); play.run(); }, () -> { audio.close(); exit.run(); });
    }
    @Override public void show() {
        if (!disposed) {
            audio.enter(); analysis.select(audio.current() == null ? null : audio.current().audioPath());
            visualiser.reset(); model.resetTrackAnalysis(); Gdx.input.setInputProcessor(input);
        }
    }
    @Override public void hide() { audio.close(); analysis.select(null); }
    private void selectTrack() {
        var track = audio.current();
        timing = track == null ? List.of() : track.difficulty().timingPoints();
        title = track == null ? "" : track.title();
        artwork.select(track == null ? null : track.set(),track == null ? null : track.difficulty());
        analysis.select(track == null ? null : track.audioPath());
        visualiser.reset(); model.resetTrackAnalysis();
    }
    private MainMenuFrame.Info frameInfo() {
        return new MainMenuFrame.Info(game.library().all().stream().mapToInt(set -> set.difficulties().size()).sum(),
                game.sessionUptimeSeconds(),LocalTime.now().format(CLOCK),MainMenuFrame.version(),
                audio.available() && !audio.paused(),audio.paused(),audio.canSkip());
    }
    private void musicControl(int control) {
        if (model.pending()) return;
        if (control == 1) audio.togglePause();
        else if (audio.skip(control == 0 ? -1 : 1)) selectTrack();
        beat = MenuBeatTiming.at(timing,audio.positionMs(),audio.available());
        analysis.sample(beat.positionMs(),beat);
    }
    @Override public void render(float delta) {
        if (disposed) return;
        double ms = Math.max(0, delta) * 1000;
        audio.advance(ms, model.fade());
        idleMs += ms;
        beat = MenuBeatTiming.at(timing, audio.positionMs(), audio.available());
        analysis.sample(audio.positionMs(), beat);
        boolean playing = audio.available() && !audio.paused();
        // stable predicts one update ahead for beat-synchronised motion; music transport stays untouched.
        var motionBeat = playing ? MenuBeatTiming.at(timing, beat.positionMs() + ms, true)
                : MenuBeatTiming.at(List.of(), idleMs, false);
        var motionAnalysis = playing ? analysis : silence;
        visualiser.advance(ms, motionAnalysis, motionBeat.kiai());
        if (model.advance(ms, motionBeat, motionAnalysis)) return;
        audio.advance(0, model.fade());
        MainMenuLayout m = MainMenuLayout.from(view.prepare());
        UiLayout ui = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float x = ui.pointerX(Gdx.input.getX()), y = ui.pointerY(Gdx.input.getY());
        m = m.parallax(x, y);
        boolean overLogo = m.logoHit(x, y, model.scale());
        int button = m.buttonAt(x, y, model);
        int control = MainMenuFrame.controlAt(MainMenuFrame.layout(ui),x,y,frameInfo(),model.pending());
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (down && !pointerWasDown) pressedTarget = control >= 0 ? 10 + control : overLogo ? -2 : button;
        if (!down && pointerWasDown) {
            if (pressedTarget == -2 && overLogo) input.click(true, -1);
            else if (pressedTarget >= 10 && control == pressedTarget - 10) musicControl(control);
            else if (pressedTarget >= 0 && pressedTarget < 2 && button == pressedTarget) input.click(false, button);
            pressedTarget = -1;
        }
        pointerWasDown = down;
        model.pointer(overLogo, button, down && pressedTarget == -2);
        var info = frameInfo();
        draw(m, model, visualiser.amplitudes(), info,MainMenuFrame.controlAt(MainMenuFrame.layout(ui),x,y,info,model.pending()));
    }
    /** Each capture reconstructs animation/analysis history from explicit inputs, not prior frames. */
    void capture(MainMenuState state, double stateMs, double playbackMs, float x, float y, boolean pressed,
                 MainMenuFrame.Info info) {
        MainMenuModel snapshot = new MainMenuModel(); snapshot.captureState(state, stateMs);
        beat = MenuBeatTiming.at(timing, playbackMs, true);
        analysis.sample(playbackMs, beat);
        MenuVisualiser spectrum = new MenuVisualiser();
        MainMenuLayout m = MainMenuLayout.from(view.prepare()).parallax(x, y);
        var motionAnalysis = info.playing() ? analysis : silence;
        snapshot.advance(0, beat, motionAnalysis);
        snapshot.pointer(m.logoHit(x, y, snapshot.scale()), m.buttonAt(x, y, snapshot), pressed);
        // Settle hover/amplitude layers without advancing the requested transition time.
        double start = Math.max(0, playbackMs - 2000);
        for (double t = start; t < playbackMs;) {
            double next = Math.min(playbackMs, t + 1000.0 / 60);
            var sampleBeat = MenuBeatTiming.at(timing, info.playing() ? next + next - t : next, info.playing());
            analysis.sample(next, MenuBeatTiming.at(timing, next, info.playing()));
            snapshot.cookie().advance(next - t, sampleBeat, motionAnalysis);
            spectrum.advance(next - t, motionAnalysis, sampleBeat.kiai());
            t = next;
        }
        snapshot.settleCapture();
        draw(m, snapshot, spectrum.amplitudes(), info,
                MainMenuFrame.controlAt(MainMenuFrame.layout(UiLayout.fromPixels(Gdx.graphics.getWidth(),Gdx.graphics.getHeight())),x,y,info,false));
    }
    private void draw(MainMenuLayout m, MainMenuModel snapshot, float[] bins, MainMenuFrame.Info info, int hoveredControl) {
        var frameLayout = MainMenuFrame.layout(UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        view.clear(); artwork.drawAt(view, 1);
        view.beginShapes();
        if (!artwork.available()) view.gradient(0, 0, m.width(), m.height(),
                new Color(.035f,.032f,.055f,1), new Color(.035f,.032f,.055f,1),
                new Color(.12f,.075f,.11f,1), new Color(.12f,.075f,.11f,1));
        view.box(0, 0, m.width(), m.height(), 0, new Color(.015f,.012f,.025f,.16f + snapshot.reveal() * .17f));
        // Light edge falloff beneath the permanent information frame.
        Color clear = new Color(0,0,0,0), edge = new Color(.01f,.008f,.02f,.32f);
        view.gradient(0,0,m.width(),m.height() * .23f,edge,edge,clear,clear);
        view.gradient(0,m.height() * .77f,m.width(),m.height() * .23f,clear,clear,edge,edge);
        view.endShapes();
        logo.visualiser(view, m, snapshot, bins);
        view.beginShapes();
        for (int b = 0; b < 2; b++) buttonShape(m, snapshot, b);
        view.endShapes();
        logo.draw(view, m, snapshot);
        view.beginShapes();
        frame.shapes(view, frameLayout, snapshot.frameEmphasis(),info,hoveredControl,snapshot.pending());
        view.endShapes();
        view.beginText();
        for (int b = 0; b < 2; b++) {
            float extent = m.extent(snapshot.reveal(), snapshot.hover(b), snapshot.explosion(b));
            float inner = m.radius() * snapshot.scale() + 22, width = extent - inner - MainMenuLayout.WEDGE - 20;
            if (width < 60) continue;
            float x = b == 0 ? m.cx() + inner : m.cx() - extent + MainMenuLayout.WEDGE + 20;
            Color color = new Color(1, .96f, .98f, snapshot.buttonAlpha(b) * MainMenuMotion.clamp((snapshot.reveal() - .5) / .3));
            view.textSmooth(b == 0 ? "PLAY" : "EXIT", x + m.direction(b) * snapshot.hover(b) * 5,
                    m.cy() + 11, width, b == 0 ? 1.5f : 1.25f, color, Align.center);
        }
        // Metadata uses map timing even for a missing stream; never label fallback animation timing as map BPM.
        frame.text(view, frameLayout, snapshot, info, title, trackBpm());
        view.endText(); view.cover(snapshot.fade());
    }
    private void buttonShape(MainMenuLayout m, MainMenuModel snapshot, int button) {
        float reveal = snapshot.reveal(); if (reveal <= 0) return;
        float bottom = m.cy() - m.buttonHeight() / 2, top = bottom + m.buttonHeight();
        float low = m.outer(button, bottom, reveal, snapshot.hover(button), snapshot.explosion(button));
        float high = m.outer(button, top, reveal, snapshot.hover(button), snapshot.explosion(button));
        Color color = new Color(button == 0 ? .27f : .11f, .065f, button == 0 ? .17f : .13f, .86f);
        color.lerp(new Color(.53f,.17f,.32f,.92f), MainMenuMotion.clamp(snapshot.hover(button)));
        color.lerp(Color.WHITE, .5f * (1 - snapshot.fade()) * snapshot.explosion(button));
        color.a *= snapshot.buttonAlpha(button);
        view.quad(m.cx(), bottom, low, bottom, high, top, m.cx(), top, color);
        Color accent = new Color(1,.58f,.76f,.25f * snapshot.buttonAlpha(button));
        view.quad(low - m.direction(button) * 3,bottom,low,bottom,high,top,high - m.direction(button) * 3,top,accent);
    }
    MainMenuState menuState() { return model.state(); }
    String trackTitle() { return title; }
    String trackBpm() { return MainMenuFrame.bpm(timing,beat.positionMs()); }
    boolean hasTrackArtwork() { return artwork.available(); }
    boolean musicPaused() { return audio.paused(); }
    double musicPositionMs() { return audio.positionMs(); }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true; audio.close(); analysis.close(); artwork.close(); logo.close();
    }
}
