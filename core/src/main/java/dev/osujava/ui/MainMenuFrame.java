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
                  float pad, float leftWidth, float centerX, float centerWidth, float trackX, float trackWidth) { }
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
        float pad = 12 * unit, gap = 24 * unit;
        float available = ui.width() - 2 * pad - 2 * gap;
        float left = available * .29f, center = available * .32f;
        float centerX = pad + left + gap, trackX = centerX + center + gap;
        return new Layout(ui.width(), ui.height(), unit, 76 * unit, 22 * unit,
                pad, left, centerX, center, trackX, ui.width() - pad - trackX);
    }
    static String uptime(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    static String bpm(List<TimingPoint> timing, double positionMs) {
        boolean valid = timing.stream().anyMatch(point -> point.uninherited() && Double.isFinite(point.timeMs())
                && Double.isFinite(point.beatLength()) && point.beatLength() > 0);
        return valid ? Math.round(60000 / MenuBeatTiming.at(timing, positionMs, true).lengthMs()) + " BPM" : "";
    }
    static float controlX(Layout m, int control) { return m.width() - m.pad() - (72 - control * 24) * m.unit(); }
    static float controlY(Layout m) { return m.height() - 72 * m.unit(); }
    static boolean controlsVisible(Info info) { return info.playing() || info.paused() || info.canSkip(); }
    static boolean controlEnabled(Info info, int control) {
        return control == 1 ? info.playing() || info.paused() : info.canSkip();
    }
    static int controlAt(Layout m, float x, float y, Info info, boolean pending) {
        if (pending || !controlsVisible(info) || y < controlY(m) || y > controlY(m) + 22 * m.unit()) return -1;
        for (int control = 0; control < 3; control++)
            if (controlEnabled(info,control) && x >= controlX(m,control) && x <= controlX(m,control) + 22 * m.unit()) return control;
        return -1;
    }
    void shapes(UiView view, Layout m, float emphasis, Info info, int hovered, boolean pending) {
        Color topShade = new Color(.018f, .035f, .065f, .60f + emphasis * .07f);
        Color lowerShade = new Color(.018f, .035f, .065f, .44f + emphasis * .07f);
        view.gradient(0, m.height() - m.topHeight(), m.width(), m.topHeight(),
                lowerShade, lowerShade, topShade, topShade);
        view.box(0, 0, m.width(), m.bottomHeight(), 0,
                new Color(.018f, .03f, .055f, .46f + emphasis * .06f));
        Color edge = new Color(.65f, .73f, .84f, .075f + emphasis * .025f);
        view.box(0, m.height() - m.topHeight(), m.width(), m.unit(), 0, edge);
        view.box(0, m.bottomHeight() - m.unit(), m.width(), m.unit(), 0, edge);
        // A small local monogram, not an online avatar or profile.
        view.box(m.pad(), m.height() - 61 * m.unit(), 44 * m.unit(), 44 * m.unit(), 0,
                new Color(.65f, .29f, .43f, .30f));
        if (!controlsVisible(info)) return;
        for (int control = 0; control < 3; control++) {
            float x = controlX(m,control), y = controlY(m), u = m.unit();
            boolean enabled = !pending && controlEnabled(info,control);
            view.box(x,y,22 * u,22 * u,0,new Color(.6f,.7f,.85f,enabled && hovered == control ? .18f : 0));
            Color glyph = new Color(.94f,.96f,1,enabled ? .85f : .22f);
            if (control == 1 && !info.paused()) {
                view.box(x + 7 * u,y + 6 * u,3 * u,10 * u,0,glyph);
                view.box(x + 13 * u,y + 6 * u,3 * u,10 * u,0,glyph);
            } else {
                float a = x + (control == 0 ? 15 : 7) * u, b = x + (control == 0 ? 7 : 15) * u;
                view.quad(a,y + 6 * u,b,y + 11 * u,a,y + 16 * u,a,y + 16 * u,glyph);
                if (control != 1) view.box(x + (control == 0 ? 5 : 16) * u,y + 6 * u,2 * u,10 * u,0,glyph);
            }
        }
    }
    void text(UiView view, Layout m, MainMenuModel model, Info info, String title, String bpm) {
        float u = m.unit(), top = m.height();
        Color primary = new Color(.96f, .97f, 1, .86f + model.frameEmphasis() * .06f);
        Color secondary = new Color(.74f, .8f, .89f, .69f + model.frameEmphasis() * .07f);
        Color muted = new Color(.68f, .73f, .81f, .60f + model.frameEmphasis() * .07f);
        float playerX = m.pad() + 54 * u, playerWidth = m.leftWidth() - 54 * u;
        view.textSmooth("j!", m.pad(), top - 48 * u, 44 * u, 1.35f * u, primary, Align.center);
        view.textSmooth("LOCAL PLAYER", playerX, top - 20 * u, playerWidth, .58f * u, muted);
        view.textSmooth("osu!java", playerX, top - 40 * u, playerWidth, 1.02f * u, primary);
        view.textSmooth(info.beatmaps() + " imported beatmaps", playerX, top - 57 * u,
                playerWidth, .64f * u, secondary);

        view.textSmooth(info.beatmaps() + " beatmaps available", m.centerX(), top - 24 * u,
                m.centerWidth(), .68f * u, secondary);
        view.textSmooth("Session runtime  " + uptime(info.uptimeSeconds()), m.centerX(), top - 41 * u,
                m.centerWidth(), .64f * u, muted);
        view.textSmooth("Local time  " + info.clock(), m.centerX(), top - 58 * u,
                m.centerWidth(), .64f * u, muted);

        String trackLabel = title.isBlank() ? "LOCAL MUSIC" : info.paused() ? "PAUSED"
                : info.playing() ? "NOW PLAYING" : "SELECTED TRACK";
        view.textSmooth(trackLabel, m.trackX(), top - 17 * u, m.trackWidth(), .56f * u, muted, Align.right);
        // UiView's smooth font fits to this column using a Unicode-safe ellipsis.
        view.textSmooth(title.isBlank() ? "No local track selected" : title,
                m.trackX(), top - 37 * u, m.trackWidth(), .82f * u, primary, Align.right);
        view.textSmooth(bpm, m.trackX(), top - 65 * u,
                m.trackWidth() - (controlsVisible(info) ? 84 * u : 0), .60f * u, secondary, Align.right);

        String branding = "osu!java" + (info.version().isBlank() ? "" : "  " + info.version());
        view.textSmooth(branding, m.pad(), 7 * u, m.leftWidth(), .57f * u, muted);
        // No permanent banner: the centre is reserved for future real local notifications.
        view.textSmooth("LOCAL", m.trackX(), 7 * u, m.trackWidth(), .54f * u, muted, Align.right);
    }
}
