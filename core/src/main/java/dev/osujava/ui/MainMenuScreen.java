package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.ui.theme.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Artwork, cookie, then two strips. All animation uses screen-local UI seconds. */
public final class MainMenuScreen extends ScreenAdapter {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color CLEAR = new Color(0, 0, 0, 0);
    private static final Color DIM = new Color(.015f, .012f, .025f, .22f);
    private static final Color FOCUS = new Color(.018f, .012f, .025f, .24f);
    private static final Color EDGE = new Color(.015f, .012f, .025f, .48f);
    private static final Color STRIP = new Color(.065f, .055f, .085f, .85f);
    private static final Color PRIMARY = new Color(.20f, .085f, .145f, .89f);
    private static final Color HOVER = new Color(.65f, .19f, .39f, .96f);

    private final UiView view;
    private final OsuCookie cookie = new OsuCookie();
    private final BeatmapBackdrop artwork = new BeatmapBackdrop();
    private final UiNavigation outgoing = new UiNavigation();
    private final MainMenuInput input;
    private final Supplier<String> clock;
    private final String ambientTitle;
    private final int count;
    private final float[] hover = new float[MainMenuLayout.ITEMS];
    private float seconds, cookieHover;
    private boolean cookiePressed;

    public MainMenuScreen(OsuJavaGame game) {
        this(game, () -> LocalTime.now().format(CLOCK_FORMAT),
                AmbientArtworkSelection.choose(game.library().all(), RandomGenerator.getDefault()),
                () -> game.navigate(new SongSelectScreen(game)), () -> Gdx.app.exit());
    }

    /** Small seams for capture clock, local artwork and real navigation callback regression checks. */
    MainMenuScreen(OsuJavaGame game, Supplier<String> clock, BeatmapSet ambient, Runnable play, Runnable exit) {
        view = new UiView(game);
        this.clock = clock;
        input = new MainMenuInput(outgoing, play, exit);
        count = game.library().all().stream().mapToInt(set -> set.difficulties().size()).sum();
        ambientTitle = ambient == null ? "" : ambient.artist() + " — " + ambient.title();
        if (ambient != null) artwork.select(ambient, ambient.difficulties().stream()
                .filter(d -> d.backgroundPath() != null && java.nio.file.Files.isRegularFile(d.backgroundPath()))
                .findFirst().orElse(null));
    }

    @Override public void show() { Gdx.input.setInputProcessor(input); }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        seconds += Math.max(0, delta);
        UiLayout ui = view.prepare();
        MainMenuLayout m = MainMenuLayout.from(ui);
        float px = ui.pointerX(Gdx.input.getX()), py = ui.pointerY(Gdx.input.getY());
        float scale = MainMenuMotion.scale(seconds, cookieHover, cookiePressed);
        boolean onCookie = MainMenuMotion.cookie(seconds) > 0 && m.cookieHit(px, py, scale);
        int row = m.stripAt(px, py, seconds, hover, scale);
        cookieHover = MainMenuMotion.approach(cookieHover, onCookie || row == 0, delta);
        for (int i = 0; i < hover.length; i++) hover[i] = MainMenuMotion.approach(hover[i], row == i, delta);
        cookiePressed = onCookie && (Gdx.input.isButtonPressed(Input.Buttons.LEFT) || outgoing.pending());
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) input.click(onCookie, row);
        draw(m);
    }

    /** Draw exactly the supplied state, independent of pointer, delta, wall clock and previous frames. */
    void capture(float time, float pointerX, float pointerY, boolean pressed) {
        seconds = time;
        MainMenuLayout m = MainMenuLayout.from(view.prepare());
        cookieHover = 0;
        java.util.Arrays.fill(hover, 0);
        boolean onCookie = m.cookieHit(pointerX, pointerY, MainMenuMotion.scale(time, 0, pressed));
        int row = m.stripAt(pointerX, pointerY, time, hover, MainMenuMotion.scale(time, 0, pressed));
        cookieHover = onCookie || row == 0 ? 1 : 0;
        if (row >= 0) hover[row] = 1;
        cookiePressed = pressed && onCookie;
        draw(m);
    }

    private void draw(MainMenuLayout m) {
        cookie.bounds(m.cx(), m.cy(), m.radius());
        view.clear();
        artwork.drawAt(view, MainMenuMotion.background(seconds));
        view.beginShapes();
        drawBackground(m);
        for (int i = 0; i < MainMenuLayout.ITEMS; i++) drawStrip(m, i);
        cookie.drawMainMenu(view, seconds, cookieHover, cookiePressed);
        // Edge information gets a fading backing instead of an opaque header/footer panel.
        view.gradient(0, m.height() - 100, m.width(), 100, CLEAR, CLEAR, EDGE, EDGE);
        view.gradient(0, 0, m.width(), 62, EDGE, EDGE, CLEAR, CLEAR);
        view.endShapes();
        view.beginText();
        cookie.drawMainMenuText(view, seconds, cookieHover, cookiePressed);
        for (int i = 0; i < MainMenuLayout.ITEMS; i++) {
            float reveal = MainMenuMotion.strip(seconds, i);
            float x = m.labelX() + hover[i] * 4;
            float width = m.right(reveal, hover[i]) - MainMenuLayout.SLANT - x - 24;
            if (width < 80) continue; // Labels appear only once their animated polygon contains them.
            Color text = new Color(i == 0 ? UiTheme.TEXT : UiTheme.MUTED).lerp(Color.WHITE, hover[i]);
            text.a = reveal;
            view.textSmooth(MainMenuLayout.LABELS.get(i), x, m.rowY(i) + m.rowHeight() * .39f, width,
                    i == 0 ? 1.85f : 1.32f, text);
        }
        float infoScale = m.height() > m.width() ? 1.3f : 1;
        float pad = MainMenuLayout.EDGE, groupWidth = (m.width() - pad * 3) * .5f;
        view.textSmooth("osu!java", pad, m.height() - 29, groupWidth, 1.05f * infoScale, UiTheme.TEXT);
        view.textSmooth(count + " local beatmaps", pad, m.height() - 50, groupWidth, .74f * infoScale, UiTheme.MUTED);
        float rightX = m.width() - pad - groupWidth;
        view.textSmooth(ambientTitle, rightX, m.height() - 29, groupWidth, .88f * infoScale, UiTheme.TEXT, Align.right);
        view.textSmooth("LOCAL · " + clock.get(), rightX, m.height() - 50, groupWidth, .72f * infoScale, UiTheme.MUTED, Align.right);
        view.textSmooth("P / Enter — Play", pad, 20, m.width() - pad * 2, .74f * infoScale, UiTheme.MUTED);
        view.endText();
        // Entrance is compositional; UiNavigation exclusively owns the outgoing full-screen fade.
        view.cover(outgoing.opacity());
    }

    private void drawBackground(MainMenuLayout m) {
        if (!artwork.available()) {
            float fade = MainMenuMotion.background(seconds);
            Color low = new Color(.035f, .032f, .055f, fade);
            Color high = new Color(.12f, .075f, .11f, fade);
            view.gradient(0, 0, m.width(), m.height(), low, low, high, high);
        } else view.box(0, 0, m.width(), m.height(), 0, DIM);
        // Broad horizontal contrast falloff behind the action area, no decorative circles.
        float focus = m.cx() + m.radius() * .25f;
        view.gradient(0, 0, focus, m.height(), CLEAR, FOCUS, FOCUS, CLEAR);
        view.gradient(focus, 0, m.width() - focus, m.height(), FOCUS, CLEAR, CLEAR, FOCUS);
    }

    private void drawStrip(MainMenuLayout m, int index) {
        float reveal = MainMenuMotion.strip(seconds, index);
        if (reveal <= 0) return;
        float y = m.rowY(index), right = m.right(reveal, hover[index]);
        Color tint = new Color(index == 0 ? PRIMARY : STRIP).lerp(HOVER, hover[index]);
        tint.a *= reveal;
        view.quad(m.stripLeft(), y, right - MainMenuLayout.SLANT, y, right, y + m.rowHeight(),
                m.stripLeft(), y + m.rowHeight(), tint);
        // Thin inset accent follows the slanted end, subordinate to the cookie's white ring.
        Color accent = new Color(UiTheme.ACCENT); accent.a = reveal * (.25f + hover[index] * .5f);
        view.quad(right - 4 - MainMenuLayout.SLANT, y, right - MainMenuLayout.SLANT, y,
                right, y + m.rowHeight(), right - 4, y + m.rowHeight(), accent);
    }

    @Override public void dispose() { artwork.close(); }
}
