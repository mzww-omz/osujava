package dev.osujava.skin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small, section-oriented skin.ini reader. Supports hit-circle Fonts and overlay ordering settings. */
public record SkinConfiguration(Fonts fonts, boolean hasIni, boolean hitCircleOverlayAboveNumber, double legacyVersion) {
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay) { this(fonts, hasIni, overlay, 1); }
    public SkinConfiguration(Fonts fonts, boolean hasIni) { this(fonts, hasIni, true); }

    public record Fonts(String hitCirclePrefix, float hitCircleOverlap) {
        // osu.Game/Skinning/LegacySkinExtensions.cs: GetFontPrefix / GetFontOverlap.
        public static Fonts defaults() { return new Fonts("default", -2f); }
    }

    public static SkinConfiguration defaults() {
        return new SkinConfiguration(Fonts.defaults(), false);
    }

    public static SkinConfiguration read(Path directory) throws IOException {
        if (directory == null || !Files.isRegularFile(directory.resolve("skin.ini"))) return defaults();
        try (var reader = Files.newBufferedReader(directory.resolve("skin.ini"))) {
            return parse(reader);
        }
    }

    static SkinConfiguration parse(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String section = "";
        String prefix = Fonts.defaults().hitCirclePrefix();
        float overlap = Fonts.defaults().hitCircleOverlap();
        // LegacySkinDecoder.CreateTemplateObject defaults to 1.0; SkinConfiguration.LATEST_VERSION = 2.7.
        double version = 1;
        Boolean overlay = null;
        Boolean typoOverlay = null;
        String line;
        while ((line = reader.readLine()) != null) {
            // UTF-8 BOM and legacy // comments, including comments after values.
            line = line.replace("\uFEFF", "");
            int comment = line.indexOf("//");
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (section.equalsIgnoreCase("General")) {
                if (key.equalsIgnoreCase("Version")) {
                    if (value.equals("latest")) version = 2.7;
                    else if (value.matches("[0-9]+(?:\\.[0-9]*)?")) {
                        double parsedVersion = Double.parseDouble(value);
                        if (Double.isFinite(parsedVersion)) version = parsedVersion;
                    }
                }
                Boolean parsed = value.equals("1") ? Boolean.TRUE : value.equals("0") ? Boolean.FALSE : null;
                if (parsed != null) {
                    if (key.equalsIgnoreCase("HitCircleOverlayAboveNumber")) overlay = parsed;
                    if (key.equalsIgnoreCase("HitCircleOverlayAboveNumer")) typoOverlay = parsed;
                }
            }
            if (!section.equalsIgnoreCase("Fonts")) continue;
            if (key.equalsIgnoreCase("HitCirclePrefix") && !value.isEmpty()) prefix = value;
            if (key.equalsIgnoreCase("HitCircleOverlap")) {
                try {
                    float parsed = Float.parseFloat(value);
                    if (Float.isFinite(parsed)) overlap = parsed;
                } catch (NumberFormatException ignored) {
                    // Malformed optional settings retain their previous/default value.
                }
            }
        }
        return new SkinConfiguration(new Fonts(prefix, overlap), true,
                overlay != null ? overlay : typoOverlay != null ? typoOverlay : true, version);
    }
}
