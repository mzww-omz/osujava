package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.ui.theme.BeatmapBackdrop;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public final class MainMenuScreen extends ScreenAdapter {
    private static final Color TOP = new Color(.045f, .034f, .065f, .76f);
    private static final Color FOOT = new Color(.045f, .034f, .065f, .74f);
    private static final Color STRIP = new Color(.37f, .31f, .71f, .84f);
    private static final Color STRIP_HOVER = new Color(.52f, .43f, .86f, .98f);
    private static final Color STRIP_DISABLED = new Color(.30f, .27f, .43f, .8f);
    private static final Color BG = new Color(.075f, .052f, .10f, .76f);
    private static final Color GLOW = new Color(.23f, .13f, .25f, .10f);
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final OsuJavaGame game;
    private final UiView view;
    private final OsuCookie cookie = new OsuCookie();
    private final BeatmapBackdrop ambientArtwork = new BeatmapBackdrop();
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private float seconds;
    private float cx, cy, radius, stripX, stripW, stripH, stripGap, stripBottom;
    private float reveal;
    private String ambientTitle = "";

    private static final class Metrics {
        static final float COOKIE_X = .36f;
        static final float COOKIE_Y = .50f;
        static final float COOKIE_RADIUS_H = .285f;
        static final float STRIP_HEIGHT_H = .096f;
        static final float STRIP_GAP = 5;
        static final float TOP_HEIGHT = 35;
        static final float FOOT_HEIGHT = 31;
    }

    public MainMenuScreen(OsuJavaGame game) { this.game = game; view = new UiView(game); }

    @Override public void show() {
        if (!game.library().all().isEmpty()) {
            BeatmapSet set = game.library().all().stream()
                    .min(Comparator.comparing(BeatmapSet::title, String.CASE_INSENSITIVE_ORDER))
                    .orElseThrow();
            ambientArtwork.select(set, set.difficulties().get(0));
            ambientTitle = set.artist() + " - " + set.title();
        }
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (key == Input.Keys.P || key == Input.Keys.ENTER || key == Input.Keys.SPACE) { openSongs(); return true; }
                if (key == Input.Keys.ESCAPE) { Gdx.app.exit(); return true; }
                return false;
            }
        });
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        seconds += Math.min(delta, .05f);
        reveal = Math.min(1f, reveal + Math.max(0, delta) / .22f);
        calculateLayout(layout);
        float px = layout.pointerX(Gdx.input.getX()), py = layout.pointerY(Gdx.input.getY());
        boolean onCookie = cookie.hit(px, py);
        int hoveredStrip = stripAt(px, py);
        boolean pressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (onCookie || hoveredStrip == 0) { openSongs(); return; }
            if (hoveredStrip == 2) { outgoing.request(Gdx.app::exit); return; }
        }

        view.clear();
        ambientArtwork.draw(view, delta);
        view.beginShapes();
        drawBackground(layout);
        drawStrips(hoveredStrip);
        cookie.drawShape(view, seconds, onCookie, pressed && onCookie);
        view.box(0, layout.height() - Metrics.TOP_HEIGHT, layout.width(), Metrics.TOP_HEIGHT, 0, TOP);
        view.box(0, 0, layout.width(), Metrics.FOOT_HEIGHT, 0, FOOT);
        view.endShapes();
        view.beginText();
        cookie.drawText(view);
        String[] labels = {"Play", "Options", "Exit"};
        for (int i = 0; i < labels.length; i++) {
            float y = stripBottom + (2 - i) * (stripH + stripGap);
            float labelX = cx + radius + 17;
            view.textSmooth(labels[i], labelX, y + stripH * .66f,
                    Math.max(60, stripX + stripW - labelX - 18), 1.48f,
                    i == 1 ? UiTheme.MUTED : UiTheme.TEXT);
        }
        int count = game.library().all().stream().mapToInt(set -> set.difficulties().size()).sum();
        view.textSmooth("osu!java", 15, layout.height() - 11, 160, 1.03f, UiTheme.TEXT);
        view.textSmooth(count + " local difficulties", 170, layout.height() - 11, 230, .74f, UiTheme.TEXT);
        view.textSmooth(ambientTitle, layout.width() * .56f, layout.height() - 11,
                layout.width() * .23f, .72f, UiTheme.MUTED, Align.right);
        view.textSmooth("LOCAL  /  " + LocalTime.now().format(CLOCK_FORMAT), layout.width() - 208,
                layout.height() - 11, 195, .74f, UiTheme.TEXT, Align.right);
        view.textSmooth("Play local beatmaps  ·  P / Enter", 15, 10, layout.width() - 30, .72f, UiTheme.MUTED);
        view.endText();
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    private void calculateLayout(UiLayout layout) {
        radius = Math.min(layout.height() * Metrics.COOKIE_RADIUS_H, layout.width() * .215f);
        cx = Math.max(radius + 26, layout.width() * Metrics.COOKIE_X);
        cy = layout.height() * Metrics.COOKIE_Y;
        cookie.bounds(cx, cy, radius);
        stripX = cx + radius * .20f;
        stripW = Math.min(layout.width() - stripX - 38, radius * 2.15f);
        stripH = Math.max(55, Math.min(72, layout.height() * Metrics.STRIP_HEIGHT_H));
        stripGap = Metrics.STRIP_GAP;
        stripBottom = cy - (stripH * 3 + stripGap * 2) / 2;
    }

    private int stripAt(float x, float y) {
        if (x < cx + radius * .65f || x > stripX + stripW) return -1;
        for (int i = 0; i < 3; i++) {
            float rowY = stripBottom + (2 - i) * (stripH + stripGap);
            if (y >= rowY && y <= rowY + stripH) return i;
        }
        return -1;
    }

    private void drawBackground(UiLayout layout) {
        view.box(0, 0, layout.width(), layout.height(), 0, BG);
        for (int i = 0; i < 12; i++) {
            float x = layout.width() * (.07f + i * .089f);
            float y = layout.height() * (.24f + (i % 4) * .15f);
            view.circle(x, y, 48 + i % 3 * 22, UiTheme.ORBIT);
        }
        view.circle(cx, cy, radius + 105, GLOW);
    }

    private void drawStrips(int hovered) {
        float easing = 1 - (1 - reveal) * (1 - reveal);
        for (int i = 0; i < 3; i++) {
            float y = stripBottom + (2 - i) * (stripH + stripGap);
            float slide = (1 - easing) * (stripW + 35 + i * 30);
            float lift = hovered == i && i != 1 ? 8 : 0;
            float left = stripX - slide;
            float right = stripX + stripW - slide + lift;
            view.quad(left, y, right - 18, y, right, y + stripH, left, y + stripH,
                    i == 1 ? STRIP_DISABLED : hovered == i ? STRIP_HOVER : STRIP);
        }
    }

    private void openSongs() { outgoing.request(() -> game.navigate(new SongSelectScreen(game))); }

    @Override public void dispose() { ambientArtwork.close(); }
}
