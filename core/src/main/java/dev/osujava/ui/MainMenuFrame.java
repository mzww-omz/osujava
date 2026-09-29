package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.theme.UiView;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;
import java.util.List;
import dev.osujava.beatmap.TimingPoint;

/** Always-visible local information frame. All changing data is supplied before drawing. */
final class MainMenuFrame {
    record Info(int beatmaps, long uptimeSeconds, String clock, String version,
                boolean playing, boolean paused, boolean canSkip) { }
    record Layout(float width, float height, float unit, float topHeight, float bottomHeight,
                  float pad, float leftWidth, float centerX, float centerWidth, float trackX, float trackWidth, boolean compact) { }
    private static final String VERSION = readVersion();

    static String version() { return VERSION; }
    private static String readVersion() {
        try (var stream = MainMenuFrame.class.getResourceAsStream("/osujava-build.properties")) {
            if (stream == null) return "";
            var properties = new Properties(); properties.load(stream);
            return properties.getProperty("version", "").strip();
        } catch (IOException ignored) { return ""; }
    }
    static Layout layout(UiLayout ui) {
        // Retain legible physical heights in narrow windows, then scale up at desktop/Retina sizes.
        float unit = Math.max(.95f, ui.scale()) / ui.scale();
        // b20230727.9 uses a 480-high HUD canvas: information x=210, edge bands h=54.
        // Convert those observed anchors to our existing 720-high UI; retain a compact narrow fallback.
        boolean compact = ui.width() / unit < 1000;
        float pad = 5 * unit, gap = 10 * unit;
        float centerX = compact ? ui.width() * .31f : 315 * unit;
        float trackWidth = Math.min(360 * unit, ui.width() * .35f);
        float trackX = ui.width() - pad - trackWidth;
        float centerWidth = trackX - gap - centerX;
        return new Layout(ui.width(), ui.height(), unit, 81 * unit, 81 * unit,
                pad, centerX - pad - gap, centerX, centerWidth, trackX, trackWidth, compact);
    }
    static float informationScale(Layout m) { return (m.compact() ? .66f : .94f) * m.unit(); }
    static float avatarSize(Layout m) { return (m.compact() ? 44 : 70) * m.unit(); }
    static String uptime(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    static String bpm(List<TimingPoint> timing, double positionMs) {
        boolean valid = timing.stream().anyMatch(point -> point.uninherited() && Double.isFinite(point.timeMs())
                && Double.isFinite(point.beatLength()) && point.beatLength() > 0);
        return valid ? Math.round(60000 / MenuBeatTiming.at(timing, positionMs, true).lengthMs()) + " BPM" : "";
    }
    static float controlX(Layout m, int control) { return m.width() - m.pad() - (90 - control * 30) * m.unit(); }
    static float controlY(Layout m) { return m.height() - 57 * m.unit(); }
    static boolean controlsVisible(Info info) { return info.playing() || info.paused() || info.canSkip(); }
    static boolean controlEnabled(Info info, int control) {
        return control == 1 ? info.playing() || info.paused() : info.canSkip();
    }
    static int controlAt(Layout m, float x, float y, Info info, boolean pending) {
        if (pending || !controlsVisible(info) || y < controlY(m) || y > controlY(m) + 26 * m.unit()) return -1;
        for (int control = 0; control < 3; control++)
            if (controlEnabled(info,control) && x >= controlX(m,control) && x <= controlX(m,control) + 26 * m.unit()) return control;
        return -1;
    }
    void shapes(UiView view, Layout m, float emphasis, Info info, int hovered, boolean pending) {
        // Stable's chrome is a neutral black scrim, not a blue application navbar.
        Color shade = new Color(0, 0, 0, .38f + emphasis * .04f);
        view.box(0, m.height() - m.topHeight(), m.width(), m.topHeight(), 0, shade);
        view.box(0, 0, m.width(), m.bottomHeight(), 0, shade);
        Color edge = new Color(1, 1, 1, .035f + emphasis * .015f);
        view.box(0, m.height() - m.topHeight(), m.width(), m.unit(), 0, edge);
        view.box(0, m.bottomHeight() - m.unit(), m.width(), m.unit(), 0, edge);
        float size = avatarSize(m);
        // Original procedural local emblem: no extracted stable avatar or skin asset.
        view.gradient(m.pad(), m.height() - m.pad() - size, size, size,
                new Color(.39f,.12f,.22f,.80f), new Color(.39f,.12f,.22f,.80f),
                new Color(.66f,.25f,.37f,.80f), new Color(.66f,.25f,.37f,.80f));
        if (!controlsVisible(info)) return;
        for (int control = 0; control < 3; control++) {
            float x = controlX(m,control), y = controlY(m), u = m.unit();
            boolean enabled = !pending && controlEnabled(info,control);
            view.box(x,y,26 * u,26 * u,0,new Color(.6f,.7f,.85f,enabled && hovered == control ? .18f : 0));
            Color glyph = new Color(.94f,.96f,1,enabled ? .85f : .22f);
            if (control == 1 && !info.paused()) {
                view.box(x + 9 * u,y + 7 * u,3 * u,12 * u,0,glyph);
                view.box(x + 15 * u,y + 7 * u,3 * u,12 * u,0,glyph);
            } else {
                float a = x + (control == 0 ? 18 : 8) * u, b = x + (control == 0 ? 8 : 18) * u;
                view.quad(a,y + 7 * u,b,y + 13 * u,a,y + 19 * u,a,y + 19 * u,glyph);
                if (control != 1) view.box(x + (control == 0 ? 6 : 19) * u,y + 7 * u,2 * u,12 * u,0,glyph);
            }
        }
    }
    void text(UiView view, Layout m, MainMenuModel model, Info info, String title, String bpm) {
        float u = m.unit(), top = m.height();
        float alpha = .94f + model.frameEmphasis() * .06f;
        Color primary = new Color(1, 1, 1, alpha);
        Color secondary = new Color(.92f, .92f, .94f, alpha * .91f);
        Color muted = new Color(.80f, .80f, .83f, alpha * .83f);
        float avatar = avatarSize(m), playerX = m.pad() + avatar + 5 * u;
        float playerWidth = m.pad() + m.leftWidth() - playerX;
        view.textSmooth("java!", m.pad(), top - m.pad() - avatar * .61f, avatar,
                (m.compact() ? .88f : 1.3f) * u, primary, Align.center);
        view.textSmooth("osu!java", playerX, top - 18 * u, playerWidth, .98f * u, primary);
        view.textSmooth("LOCAL PLAYER", playerX, top - 34 * u, playerWidth, .60f * u, secondary);
        view.textSmooth("Imported beatmaps", playerX, top - 49 * u, playerWidth, .61f * u, muted);
        // The prominent number is the real local difficulty count, never a fabricated score/PP/level.
        view.textSmooth(Integer.toString(info.beatmaps()), playerX, top - 73 * u,
                playerWidth, 1.35f * u, secondary, Align.right);

        float infoScale = informationScale(m);
        view.textSmooth(info.beatmaps() + " beatmaps available", m.centerX(), top - 18 * u,
                m.centerWidth(), infoScale, primary);
        view.textSmooth("Session runtime  " + uptime(info.uptimeSeconds()), m.centerX(), top - 39 * u,
                m.centerWidth(), infoScale, secondary);
        view.textSmooth("Local time  " + info.clock(), m.centerX(), top - 60 * u,
                m.centerWidth(), infoScale, secondary);

        // Compact two-line label beside the title, then a separate right-anchored transport row.
        float labelWidth = 52 * u, titleX = m.trackX() + labelWidth;
        String label = title.isBlank() ? "Local" : info.paused() ? "Paused" : info.playing() ? "Now" : "Local";
        view.textSmooth(label, m.trackX(), top - 11 * u, labelWidth, .62f * u, muted);
        view.textSmooth(info.playing() ? "Playing" : "Music", m.trackX(), top - 23 * u,
                labelWidth, .62f * u, muted);
        view.textSmooth(title.isBlank() ? "No track selected" : title,
                titleX, top - 19 * u, m.trackWidth() - labelWidth,
                (m.compact() ? .76f : 1.0f) * u, primary, Align.right);
        view.textSmooth(bpm, m.trackX(), top - 72 * u, m.trackWidth() - 4 * u,
                .64f * u, muted, Align.right);

        // Bottom chrome retains space, but no invented banner, service buttons or progress bar.
        view.textSmooth("osu!java", m.pad(), 13 * u, 104 * u, 1.05f * u, secondary);
        view.textSmooth(info.version(), m.pad() + 105 * u, 14 * u,
                Math.max(0, m.leftWidth() - 105 * u), .59f * u, muted);
        view.textSmooth("LOCAL", m.trackX(), 10 * u, m.trackWidth() - 4 * u, .62f * u, muted, Align.right);
    }
}
