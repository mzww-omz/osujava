package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.Align;
import dev.osujava.OsuJavaGame;
import dev.osujava.ui.theme.UiButton;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiNavigation;
import dev.osujava.ui.theme.UiTheme;
import dev.osujava.ui.theme.UiTransition;
import dev.osujava.ui.theme.UiView;

public final class MainMenuScreen extends ScreenAdapter {
    private final OsuJavaGame game;
    private final UiView view;
    private final UiTransition entrance = new UiTransition();
    private final UiNavigation outgoing = new UiNavigation();
    private final UiButton play = new UiButton("PLAY", true);
    private final UiButton options = new UiButton("OPTIONS", false);
    private final UiButton exit = new UiButton("EXIT", false);
    private float elapsed;

    public MainMenuScreen(OsuJavaGame game) { this.game = game; view = new UiView(game); }

    @Override public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (AppShortcuts.handleQuit(key)) return true;
                if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) { openSongs(); return true; }
                if (key == Input.Keys.I) {
                    SongSelectScreen songs = new SongSelectScreen(game);
                    game.navigate(songs);
                    songs.requestImport();
                    return true;
                }
                if (key == Input.Keys.ESCAPE) { Gdx.app.exit(); return true; }
                return false;
            }
        });
    }

    @Override public void render(float delta) {
        if (outgoing.advance(delta)) return;
        UiLayout layout = view.prepare();
        elapsed += Math.min(delta, 0.05f);
        float centerX = layout.width() * 0.45f;
        float centerY = layout.height() * 0.53f;
        float radius = Math.min(layout.height() * 0.25f, layout.width() * 0.19f);
        float menuX = Math.min(layout.width() - 255, centerX + radius + 34);
        float menuY = centerY - 98;
        play.bounds(menuX, menuY + 108, 190, 58);
        options.bounds(menuX, menuY + 46, 190, 50);
        options.enabled(false);
        exit.bounds(menuX, menuY - 10, 190, 46);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float x = layout.pointerX(Gdx.input.getX()), y = layout.pointerY(Gdx.input.getY());
            if (play.hit(x, y) || (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY) <= radius * radius) { openSongs(); return; }
            if (exit.hit(x, y)) { outgoing.request(Gdx.app::exit); return; }
        }
        boolean logoHover = (layout.pointerX(Gdx.input.getX()) - centerX) * (layout.pointerX(Gdx.input.getX()) - centerX)
                + (layout.pointerY(Gdx.input.getY()) - centerY) * (layout.pointerY(Gdx.input.getY()) - centerY) <= radius * radius;
        view.clear();
        view.beginShapes();
        for (int i = 0; i < 6; i++) {
            float drift = (float) Math.sin(elapsed * 0.25f + i * 1.3f) * 15;
            view.circle(layout.width() * (0.12f + i * 0.17f), layout.height() * (0.18f + (i % 3) * 0.34f) + drift,
                    55 + i * 17, UiTheme.ORBIT);
        }
        view.circle(centerX, centerY, radius + 15 + (float) Math.sin(elapsed * 1.5f) * 3, UiTheme.SURFACE_RAISED);
        view.circle(centerX, centerY, radius, logoHover
                ? Gdx.input.isButtonPressed(Input.Buttons.LEFT) ? UiTheme.ACCENT_PRESSED : UiTheme.ACCENT_HOVER
                : UiTheme.ACCENT);
        view.circle(centerX, centerY, radius - 10, UiTheme.LOGO_INNER);
        play.drawShape(view, layout, delta);
        options.drawShape(view, layout, delta);
        exit.drawShape(view, layout, delta);
        view.box(0, 0, layout.width(), 42, 0, UiTheme.SURFACE);
        view.endShapes();
        view.beginText();
        view.text("osu!java", centerX - radius, centerY + 24, radius * 2, 3.0f, UiTheme.TEXT, Align.center);
        view.text("LOCAL RHYTHM GAME", centerX - radius, centerY - 18, radius * 2, UiTheme.META, UiTheme.TEXT, Align.center);
        play.drawText(view); options.drawText(view); exit.drawText(view);
        view.text("PLAY YOUR LOCAL BEATMAPS", menuX, menuY + 196, 260, UiTheme.META, UiTheme.MUTED);
        view.text("OPTIONS COMING LATER", menuX, menuY + 30, 210, 0.68f, UiTheme.MUTED);
        view.text("LOCAL LIBRARY  /  NO ACCOUNT REQUIRED", UiTheme.PAD, 26, layout.width() - 2 * UiTheme.PAD,
                UiTheme.META, UiTheme.MUTED);
        view.endText();
        view.fade(entrance, delta);
        view.cover(outgoing.opacity());
    }

    private void openSongs() { outgoing.request(() -> game.navigate(new SongSelectScreen(game))); }
}
