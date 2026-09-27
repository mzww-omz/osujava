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
                  float pad, float leftWidth, float trackX, float trackWidth) { }
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
        float pad = 20 * unit, left = (ui.width() - pad * 3) * .46f;
        float trackX = pad * 2 + left;
        return new Layout(ui.width(), ui.height(), unit, 56 * unit, 34 * unit,
                pad, left, trackX, ui.width() - pad - trackX);
    }
    static String uptime(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    static String tip(MainMenuState state) {
        return state == MainMenuState.CLOSED || state == MainMenuState.CLOSING
                ? "click the logo to open menu" : "PLAY to browse · Esc to close";
    }
    static String bpm(List<TimingPoint> timing, double positionMs) {
        boolean valid = timing.stream().anyMatch(point -> point.uninherited() && Double.isFinite(point.timeMs())
                && Double.isFinite(point.beatLength()) && point.beatLength() > 0);
        return valid ? Math.round(60000 / MenuBeatTiming.at(timing, positionMs, true).lengthMs()) + " BPM" : "";
    }
    static float controlX(Layout m, int control) { return m.width() - m.pad() - (86 - control * 30) * m.unit(); }
    static float controlY(Layout m) { return m.height() - 52 * m.unit(); }
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
        view.box(0, m.height() - m.topHeight(), m.width(), m.topHeight(), 0,
                new Color(.018f, .035f, .065f, .62f + emphasis * .07f));
        view.box(0, 0, m.width(), m.bottomHeight(), 0,
                new Color(.018f, .03f, .055f, .46f + emphasis * .06f));
        Color edge = new Color(.65f, .73f, .84f, .075f + emphasis * .025f);
        view.box(0, m.height() - m.topHeight(), m.width(), m.unit(), 0, edge);
        view.box(0, m.bottomHeight() - m.unit(), m.width(), m.unit(), 0, edge);
        if (!controlsVisible(info)) return;
        for (int control = 0; control < 3; control++) {
            float x = controlX(m,control), y = controlY(m), u = m.unit();
            boolean enabled = !pending && controlEnabled(info,control);
            view.box(x,y,26 * u,26 * u,4 * u,new Color(.6f,.7f,.85f,enabled && hovered == control ? .18f : .055f));
            Color glyph = new Color(.94f,.96f,1,enabled ? .85f : .22f);
            if (control == 1 && !info.paused()) {
                view.box(x + 9 * u,y + 8 * u,3 * u,10 * u,0,glyph);
                view.box(x + 15 * u,y + 8 * u,3 * u,10 * u,0,glyph);
            } else {
                float a = x + (control == 0 ? 17 : 9) * u, b = x + (control == 0 ? 9 : 17) * u;
                view.quad(a,y + 8 * u,b,y + 13 * u,a,y + 18 * u,a,y + 18 * u,glyph);
                if (control != 1) view.box(x + (control == 0 ? 7 : 18) * u,y + 8 * u,2 * u,10 * u,0,glyph);
            }
        }
    }
    void text(UiView view, Layout m, MainMenuModel model, Info info, String title, String bpm) {
        float u = m.unit(), top = m.height();
        Color primary = new Color(.96f, .97f, 1, .86f + model.frameEmphasis() * .06f);
        Color secondary = new Color(.74f, .8f, .89f, .69f + model.frameEmphasis() * .07f);
        view.textSmooth("osu!java", m.pad(), top - 23 * u, 85 * u, .86f * u, primary);
        view.textSmooth("LOCAL", m.pad() + 88 * u, top - 22 * u, m.leftWidth() - 88 * u,
                .60f * u, new Color(.96f, .64f, .77f, primary.a));
        view.textSmooth(info.beatmaps() + " beatmaps · session " + uptime(info.uptimeSeconds()),
                m.pad(), top - 43 * u, m.leftWidth(), .67f * u, secondary);
        view.textSmooth(title.isBlank() ? "No local track selected" : title,
                m.trackX(), top - 23 * u, m.trackWidth(), .80f * u, primary, Align.right);
        String trackDetail = title.isBlank() ? "" : (info.paused() ? "PAUSED" : info.playing() ? "NOW PLAYING" : "SELECTED TRACK")
                + (bpm.isBlank() ? "" : " · " + bpm);
        view.textSmooth(trackDetail, m.trackX(), top - 43 * u, m.trackWidth() - (controlsVisible(info) ? 98 * u : 0), .60f * u, secondary, Align.right);
        float footerY = 12 * u, side = 140 * u;
        if (!info.version().isBlank()) view.textSmooth("osujava " + info.version(), m.pad(), footerY,
                side, .62f * u, secondary);
        view.textSmooth(tip(model.state()), m.pad() + side, footerY, m.width() - 2 * (m.pad() + side),
                .64f * u, secondary, Align.center);
        view.textSmooth("LOCAL · " + info.clock(), m.width() - m.pad() - side, footerY, side,
                .62f * u, secondary, Align.right);
    }
}
