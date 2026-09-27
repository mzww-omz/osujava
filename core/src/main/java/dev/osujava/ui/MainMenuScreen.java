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

/** Music-first local menu. Owns one ambient difficulty and its stream for this screen lifetime. */
public final class MainMenuScreen extends ScreenAdapter {
    private final UiView view;
    private final MainMenuLogo logo = new MainMenuLogo();
    private final BeatmapBackdrop artwork = new BeatmapBackdrop();
    private final MenuAmbientAudio audio;
    private final MenuAudioAnalysis analysis;
    private final List<TimingPoint> timing;
    private final MenuVisualiser visualiser = new MenuVisualiser();
    private final MainMenuModel model = new MainMenuModel();
    private final MainMenuInput input;
    private final String title;
    private MenuBeatTiming.Beat beat = MenuBeatTiming.at(List.of(), 0, false);
    private boolean pointerWasDown, disposed;
    private int pressedTarget = -1;

    public MainMenuScreen(OsuJavaGame game) {
        this(game, AmbientArtworkSelection.choose(game.library().all(), RandomGenerator.getDefault()),
                new DeterministicMenuAudioFallback(), () -> game.navigate(new SongSelectScreen(game)), () -> Gdx.app.exit());
    }
    /** Harness injects fixed amplitudes; decoding never enters the renderer. */
    MainMenuScreen(OsuJavaGame game, BeatmapSet ambient, MenuAudioAnalysis analysis, Runnable play, Runnable exit) {
        view = new UiView(game); this.analysis = analysis;
        BeatmapDifficulty difficulty = ambient == null ? null : ambient.difficulties().stream()
                .filter(d -> d.backgroundPath() != null && java.nio.file.Files.isRegularFile(d.backgroundPath()))
                .findFirst().orElse(ambient.difficulties().getFirst());
        timing = difficulty == null ? List.of() : difficulty.timingPoints();
        audio = new MenuAmbientAudio(difficulty == null ? null : difficulty.audioPath() != null ? difficulty.audioPath() : ambient.audioPath());
        title = difficulty == null ? "" : difficulty.artist() + " — " + difficulty.title();
        if (ambient != null) artwork.select(ambient, difficulty);
        input = new MainMenuInput(model, () -> { audio.close(); play.run(); }, () -> { audio.close(); exit.run(); });
    }
    @Override public void show() { if (!disposed) { audio.enter(); visualiser.reset(); Gdx.input.setInputProcessor(input); } }
    @Override public void hide() { audio.close(); }
    @Override public void render(float delta) {
        if (disposed) return;
        double ms = Math.max(0, delta) * 1000;
        audio.advance(ms, model.fade());
        beat = MenuBeatTiming.at(timing, audio.positionMs(), audio.available());
        analysis.sample(beat.positionMs(), beat);
        visualiser.advance(ms, analysis, beat.kiai());
        if (model.advance(ms, beat, analysis)) return;
        audio.advance(0, model.fade());
        MainMenuLayout m = MainMenuLayout.from(view.prepare());
        UiLayout ui = UiLayout.fromPixels(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float x = ui.pointerX(Gdx.input.getX()), y = ui.pointerY(Gdx.input.getY());
        boolean overLogo = m.logoHit(x, y, model.scale());
        int button = m.buttonAt(x, y, model);
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (down && !pointerWasDown) pressedTarget = overLogo ? -2 : button;
        if (!down && pointerWasDown) {
            if (pressedTarget == -2 && overLogo) input.click(true, -1);
            else if (pressedTarget >= 0 && button == pressedTarget) input.click(false, button);
            pressedTarget = -1;
        }
        pointerWasDown = down;
        model.pointer(overLogo, button, down && pressedTarget == -2);
        draw(m, model, visualiser.amplitudes());
    }
    /** Each capture reconstructs animation/analysis history from explicit inputs, not prior frames. */
    void capture(MainMenuState state, double stateMs, double playbackMs, float x, float y, boolean pressed) {
        MainMenuModel snapshot = new MainMenuModel(); snapshot.captureState(state, stateMs);
        beat = MenuBeatTiming.at(timing, playbackMs, true);
        analysis.sample(playbackMs, beat);
        MenuVisualiser spectrum = new MenuVisualiser(); spectrum.advance(0, analysis, beat.kiai());
        MainMenuLayout m = MainMenuLayout.from(view.prepare());
        snapshot.advance(0, beat, analysis);
        snapshot.pointer(m.logoHit(x, y, snapshot.scale()), m.buttonAt(x, y, snapshot), pressed);
        // Settle hover/amplitude layers without advancing the requested transition time.
        snapshot.settleCapture(beat, analysis);
        draw(m, snapshot, spectrum.amplitudes());
    }
    private void draw(MainMenuLayout m, MainMenuModel snapshot, float[] bins) {
        view.clear(); artwork.drawAt(view, 1);
        view.beginShapes();
        if (!artwork.available()) view.gradient(0, 0, m.width(), m.height(),
                new Color(.035f,.032f,.055f,1), new Color(.035f,.032f,.055f,1),
                new Color(.12f,.075f,.11f,1), new Color(.12f,.075f,.11f,1));
        view.box(0, 0, m.width(), m.height(), 0, new Color(.015f,.012f,.025f,.16f + snapshot.reveal() * .17f));
        // Vertical edge falloff leaves the artwork visible; no permanent horizontal action strip.
        Color clear = new Color(0,0,0,0), edge = new Color(.01f,.008f,.02f,.32f);
        view.gradient(0,0,m.width(),m.height() * .23f,edge,edge,clear,clear);
        view.gradient(0,m.height() * .77f,m.width(),m.height() * .23f,clear,clear,edge,edge);
        for (int b = 0; b < 2; b++) buttonShape(m, snapshot, b);
        view.endShapes();
        logo.visualiser(view, m, snapshot.scale(), bins);
        logo.draw(view, m, snapshot);
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
        if (!title.isBlank()) {
            view.textSmooth(title, 24, 38, m.width() - 48, .72f, new Color(1,1,1,.65f), Align.right);
            view.textSmooth(Math.round(60000 / beat.lengthMs()) + " BPM", 24, 20, m.width() - 48, .62f,
                    new Color(1,1,1,.48f), Align.right);
        }
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
    @Override public void dispose() {
        if (disposed) return;
        disposed = true; audio.close(); analysis.close(); artwork.close(); logo.close();
    }
}
