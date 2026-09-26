package dev.osujava.skin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small, section-oriented skin.ini reader. Supports legacy Fonts, General, cursor, slider and Spinner settings. */
public record SkinConfiguration(Fonts fonts, boolean hasIni, boolean hitCircleOverlayAboveNumber, double legacyVersion, Colours colours, Cursor cursor, Spinner spinner) {
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay, double version, Colours colours, Cursor cursor) {
        this(fonts, hasIni, overlay, version, colours, cursor, Spinner.defaults());
    }
    public record Spinner(boolean noBlink, Rgb background) {
        public static Spinner defaults() { return new Spinner(false, new Rgb(100 / 255f, 100 / 255f, 100 / 255f)); }
    }
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay, double version, Colours colours) {
        this(fonts, hasIni, overlay, version, colours, Cursor.defaults());
    }
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay, double version) {
        this(fonts, hasIni, overlay, version, Colours.defaults());
    }
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay) { this(fonts, hasIni, overlay, 1); }
    public SkinConfiguration(Fonts fonts, boolean hasIni) { this(fonts, hasIni, true); }

    /** OsuCursor / LegacyCursor / LegacyCursorTrail null-config defaults. */
    public record Cursor(boolean centre, boolean rotate, boolean expand, boolean trailRotate) {
        public static Cursor defaults() { return new Cursor(true, true, true, true); }
    }

    public record Rgb(float r, float g, float b) { }

    /** Null track means use the HitObject accent colour. */
    public record Colours(Rgb sliderBorder, Rgb sliderTrackOverride) {
        public static Colours defaults() { return new Colours(new Rgb(1, 1, 1), null); }
    }

    private static Rgb parseRgb(String value) {
        String[] components = value.split(",", -1);
        if (components.length != 3) return null;
        int[] rgb = new int[3];
        try {
            for (int i = 0; i < 3; i++) {
                rgb[i] = Integer.parseInt(components[i].trim());
                if (rgb[i] < 0 || rgb[i] > 255) return null;
            }
        } catch (NumberFormatException ignored) { return null; }
        return new Rgb(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
    }

    public record Fonts(String hitCirclePrefix, float hitCircleOverlap, String scorePrefix, float scoreOverlap,
                        String comboPrefix, float comboOverlap) {
        public Fonts(String prefix, float overlap) { this(prefix, overlap, "score", 0, "score", 0); }
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
        String scorePrefix = "score", comboPrefix = "score";
        float scoreOverlap = 0, comboOverlap = 0;
        // LegacySkinDecoder.CreateTemplateObject defaults to 1.0; SkinConfiguration.LATEST_VERSION = 2.7.
        double version = 1;
        Rgb border = Colours.defaults().sliderBorder();
        Rgb track = null;
        boolean centre = true, rotate = true, expand = true, trailRotate = true, noBlink = false;
        Rgb spinnerBackground = Spinner.defaults().background();
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
            if (separator < 0) separator = line.indexOf('=');
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
                    if (key.equalsIgnoreCase("SpinnerNoBlink")) noBlink = parsed;
                    if (key.equalsIgnoreCase("CursorCentre")) centre = parsed;
                    if (key.equalsIgnoreCase("CursorRotate")) rotate = parsed;
                    if (key.equalsIgnoreCase("CursorExpand")) expand = parsed;
                    if (key.equalsIgnoreCase("CursorTrailRotate")) trailRotate = parsed;
                    if (key.equalsIgnoreCase("HitCircleOverlayAboveNumber")) overlay = parsed;
                    if (key.equalsIgnoreCase("HitCircleOverlayAboveNumer")) typoOverlay = parsed;
                }
            }
            if (section.equalsIgnoreCase("Colours")) {
                if (key.equalsIgnoreCase("SliderBorder")) {
                    Rgb parsed = parseRgb(value);
                    border = parsed != null ? parsed : Colours.defaults().sliderBorder();
                }
                if (key.equalsIgnoreCase("SliderTrackOverride")) track = parseRgb(value);
                if (key.equalsIgnoreCase("SpinnerBackground")) {
                    Rgb parsed = parseRgb(value);
                    spinnerBackground = parsed != null ? parsed : Spinner.defaults().background();
                }
            }
            if (!section.equalsIgnoreCase("Fonts")) continue;
            if (key.equalsIgnoreCase("HitCirclePrefix") && !value.isEmpty()) prefix = value;
            if (key.equalsIgnoreCase("ScorePrefix") && !value.isEmpty()) scorePrefix = value;
            if (key.equalsIgnoreCase("ComboPrefix") && !value.isEmpty()) comboPrefix = value;
            if (key.equalsIgnoreCase("HitCircleOverlap") || key.equalsIgnoreCase("ScoreOverlap")
                    || key.equalsIgnoreCase("ComboOverlap")) {
                try {
                    float parsed = Float.parseFloat(value);
                    if (Float.isFinite(parsed)) {
                        if (key.equalsIgnoreCase("HitCircleOverlap")) overlap = parsed;
                        else if (key.equalsIgnoreCase("ScoreOverlap")) scoreOverlap = parsed;
                        else comboOverlap = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed optional settings retain their previous/default value.
                }
            }
        }
        return new SkinConfiguration(new Fonts(prefix, overlap, scorePrefix, scoreOverlap, comboPrefix, comboOverlap), true,
                overlay != null ? overlay : typoOverlay != null ? typoOverlay : true, version, new Colours(border, track), new Cursor(centre, rotate, expand, trailRotate), new Spinner(noBlink, spinnerBackground));
    }
}
