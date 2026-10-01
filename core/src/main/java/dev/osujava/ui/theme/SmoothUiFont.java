package dev.osujava.ui.theme;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/** Antialiased system-font labels for menu UI, including local Unicode beatmap metadata. */
public final class SmoothUiFont implements AutoCloseable {
    private static final int OVERSAMPLE = 2;
    private static final int LIMIT = 256;
    private final LinkedHashMap<String, Label> labels = new LinkedHashMap<>(300, .75f, true);
    private final LinkedHashMap<String, String> fittedLabels = new LinkedHashMap<>(300, .75f, true);
    private final Map<Integer, Font> fonts = new HashMap<>();

    private record Label(Texture texture, float width, float height, float descent) { }

    public void draw(SpriteBatch batch, String text, float x, float baseline, float maxWidth,
                     float scale, Color color, int align) {
        draw(batch, text, x, baseline, maxWidth, scale, color, align, false);
    }

    public void draw(SpriteBatch batch, String text, float x, float baseline, float maxWidth,
                     float scale, Color color, int align, boolean bold) {
        draw(batch, text, x, baseline, maxWidth, scale, color, align, bold, false);
    }

    /** Centre-left sprite origin, independent of a particular font's ascent/descent. */
    public void drawCenteredVertically(SpriteBatch batch, String text, float x, float centreY, float maxWidth,
                                       float scale, Color color, boolean bold) {
        draw(batch, text, x, centreY, maxWidth, scale, color, Align.left, bold, true);
    }

    private void draw(SpriteBatch batch, String text, float x, float y, float maxWidth,
                      float scale, Color color, int align, boolean bold, boolean centeredVertically) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return;
        int size = Math.max(10, Math.round(17 * scale * OVERSAMPLE));
        Font font = fonts.computeIfAbsent(size * 2 + (bold ? 1 : 0), key -> new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        int fitWidth = Math.max(0, (int) Math.floor(maxWidth * OVERSAMPLE) - 4);
        String fitKey = size + ":" + bold + ":" + fitWidth + ":" + text;
        String fitted = fittedLabels.computeIfAbsent(fitKey, unused -> UiTextFit.fit(text, font, fitWidth));
        if (fitted.isEmpty()) return;
        Label label = labels.computeIfAbsent(size + ":" + bold + ":" + fitted, ignored -> rasterize(fitted, font));
        float drawX = align == Align.center ? x + (maxWidth - label.width()) / 2
                : align == Align.right ? x + maxWidth - label.width() : x;
        batch.setColor(color);
        batch.draw(label.texture(), drawX, y - (centeredVertically ? label.height() / 2 : label.descent()), label.width(), label.height());
        batch.setColor(Color.WHITE);
        if (labels.size() > LIMIT) {
            Map.Entry<String, Label> eldest = labels.entrySet().iterator().next();
            eldest.getValue().texture().dispose();
            labels.remove(eldest.getKey());
        }
        if (fittedLabels.size() > 512) fittedLabels.remove(fittedLabels.entrySet().iterator().next().getKey());
    }

    private Label rasterize(String text, Font font) {
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = measure.createGraphics();
        metricsGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        metricsGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        metricsGraphics.setFont(font);
        FontMetrics metrics = metricsGraphics.getFontMetrics();
        int width = Math.max(2, (int) Math.ceil(font.getStringBounds(text, metricsGraphics.getFontRenderContext()).getWidth()) + 4);
        int height = metrics.getHeight() + 4;
        int descent = metrics.getDescent();
        metricsGraphics.dispose();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setFont(font);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.drawString(text, 2, 2 + metrics.getAscent());
        graphics.dispose();
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int argb = image.getRGB(x, y);
            pixels.drawPixel(x, y, (argb << 8) | ((argb >>> 24) & 0xff));
        }
        Texture texture = new Texture(pixels);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixels.dispose();
        return new Label(texture, width / (float) OVERSAMPLE, height / (float) OVERSAMPLE,
                (descent + 2) / (float) OVERSAMPLE);
    }

    @Override public void close() {
        for (Label label : labels.values()) label.texture().dispose();
        labels.clear();
        fittedLabels.clear();
        fonts.clear();
    }
}
