package dev.osujava.skin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small, section-oriented skin.ini reader. Only hit-circle Fonts settings are supported. */
public record SkinConfiguration(Fonts fonts, boolean hasIni) {
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
            if (!section.equalsIgnoreCase("Fonts") || separator < 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
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
        return new SkinConfiguration(new Fonts(prefix, overlap), true);
    }
}
