package dev.osujava.skin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

/** Small, section-oriented skin.ini reader. Supports legacy Fonts, General, cursor, slider and Spinner settings. */
public record SkinConfiguration(Fonts fonts, boolean hasIni, boolean hitCircleOverlayAboveNumber, double legacyVersion, Colours colours, Cursor cursor, Spinner spinner, SongSelect songSelect, int animationFramerate) {
    public static final double LATEST_VERSION = 2.7;
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay, double version, Colours colours, Cursor cursor, Spinner spinner, SongSelect songSelect) {
        this(fonts, hasIni, overlay, version, colours, cursor, spinner, songSelect, -1);
    }
    /** Official skin.ini defaults, independent of the artwork provider. */
    public record SongSelect(Rgb activeText, Rgb inactiveText, boolean activeTextSpecified,
                             boolean inactiveTextSpecified) {
        public SongSelect(Rgb activeText, Rgb inactiveText) { this(activeText, inactiveText, true, true); }
        public static SongSelect defaults() {
            return new SongSelect(new Rgb(0, 0, 0), new Rgb(1, 1, 1), false, false);
        }
        /** Local fallback palette only; explicitly authored colours always keep ownership. */
        SongSelect withFallback(SongSelect fallback) {
            return new SongSelect(activeTextSpecified ? activeText : fallback.activeText,
                    inactiveTextSpecified ? inactiveText : fallback.inactiveText,
                    activeTextSpecified, inactiveTextSpecified);
        }
    }
    public SkinConfiguration(Fonts fonts, boolean hasIni, boolean overlay, double version, Colours colours, Cursor cursor, Spinner spinner) {
        this(fonts, hasIni, overlay, version, colours, cursor, spinner, SongSelect.defaults());
    }
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

    /** b20230727.9 SkinOsu defaults: CursorTrailRotate is not initialised by the constructor. */
    public record Cursor(boolean centre, boolean rotate, boolean expand, boolean trailRotate) {
        public static Cursor defaults() { return new Cursor(true, true, true, false); }
    }

    public record Rgb(float r, float g, float b) { }

    /** Null track means use the HitObject accent colour. */
    public record Colours(Rgb sliderBorder, Rgb sliderTrackOverride, List<Rgb> comboColours, boolean allowSliderBallTint) {
        public Colours { comboColours = List.copyOf(comboColours); }
        public Colours(Rgb sliderBorder, Rgb sliderTrackOverride) { this(sliderBorder, sliderTrackOverride, List.of(), false); }
        public static Colours defaults() { return new Colours(new Rgb(1, 1, 1), null); }
    }

    private static Rgb parseRgb(String value) {
        String[] components = value.split(",", -1);
        // Stable Colours reads RGB or RGBA with AllowTransparentColours=false.
        if (components.length != 3 && components.length != 4) return null;
        int[] rgb = new int[3];
        try {
            for (int i = 0; i < 3; i++) {
                rgb[i] = Integer.parseInt(components[i].trim());
                if (rgb[i] < 0 || rgb[i] > 255) return null;
            }
        } catch (NumberFormatException ignored) { return null; }
        return new Rgb(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
    }

    private static Boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        try { return Integer.parseInt(value) != 0; }
        catch (NumberFormatException ignored) { return null; }
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

    /** Selected Song Select skin without an ini: official latest-version semantics. */
    public static SkinConfiguration withoutIni() {
        return new SkinConfiguration(Fonts.defaults(), false, true, LATEST_VERSION);
    }

    public static SkinConfiguration read(Path directory) throws IOException {
        Path ini = SkinFiles.find(directory, "skin.ini");
        if (ini == null) return defaults();
        try (var reader = Files.newBufferedReader(ini)) {
            var parsed = parse(reader);
            if (directory.getFileName() != null && directory.getFileName().toString().equalsIgnoreCase("User"))
                return new SkinConfiguration(parsed.fonts, parsed.hasIni, parsed.hitCircleOverlayAboveNumber,
                        LATEST_VERSION, parsed.colours, parsed.cursor, parsed.spinner, parsed.songSelect, parsed.animationFramerate);
            return parsed;
        }
    }

    static SkinConfiguration parse(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String section = "";
        var sectionKeys = new HashSet<String>();
        String prefix = Fonts.defaults().hitCirclePrefix();
        float overlap = Fonts.defaults().hitCircleOverlap();
        String scorePrefix = "score", comboPrefix = "score";
        float scoreOverlap = 0, comboOverlap = 0;
        // LegacySkinDecoder.CreateTemplateObject defaults to 1.0; SkinConfiguration.LATEST_VERSION = 2.7.
        double version = 1;
        int animationFramerate = -1;
        Rgb border = Colours.defaults().sliderBorder();
        Rgb track = null;
        Rgb activeText = SongSelect.defaults().activeText(), inactiveText = SongSelect.defaults().inactiveText();
        boolean activeTextSpecified = false, inactiveTextSpecified = false;
        var comboColours = new TreeMap<Integer, Rgb>();
        boolean allowSliderBallTint = false;
        boolean centre = true, rotate = true, expand = true, trailRotate = false, noBlink = false;
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
                sectionKeys.clear();
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String key = line.substring(0, separator).trim();
            // Stable Section stores the first spelling-exact key, even if its value is invalid.
            if (!sectionKeys.add(key)) continue;
            String value = line.substring(separator + 1).trim();
            if (section.equals("General")) {
                if (key.equals("Version")) {
                    if (value.equals("latest")) version = LATEST_VERSION;
                    else if (value.matches("[0-9]+(?:\\.[0-9]*)?")) {
                        double parsedVersion = Double.parseDouble(value);
                        if (Double.isFinite(parsedVersion)) version = parsedVersion;
                    }
                }
                if (key.equals("AnimationFramerate")) {
                    try {
                        int rate = Integer.parseInt(value);
                        if (rate == -1 || rate > 0) animationFramerate = rate;
                    } catch (NumberFormatException ignored) { }
                }
                Boolean parsed = parseBoolean(value);
                if (parsed != null) {
                    if (key.equals("AllowSliderBallTint")) allowSliderBallTint = parsed;
                    if (key.equals("SpinnerNoBlink")) noBlink = parsed;
                    if (key.equals("CursorCentre")) centre = parsed;
                    if (key.equals("CursorRotate")) rotate = parsed;
                    if (key.equals("CursorExpand")) expand = parsed;
                    if (key.equals("CursorTrailRotate")) trailRotate = parsed;
                    if (key.equals("HitCircleOverlayAboveNumber")) overlay = parsed;
                    if (key.equals("HitCircleOverlayAboveNumer")) typoOverlay = parsed;
                }
            }
            if (section.equals("Colours")) {
                if (key.equals("SongSelectActiveText")) {
                    Rgb parsed = parseRgb(value);
                    if (parsed != null) { activeText = parsed; activeTextSpecified = true; }
                }
                if (key.equals("SongSelectInactiveText")) {
                    Rgb parsed = parseRgb(value);
                    if (parsed != null) { inactiveText = parsed; inactiveTextSpecified = true; }
                }
                if (key.matches("Combo[1-8]")) {
                    Rgb parsed = parseRgb(value);
                    if (parsed != null) comboColours.put(Integer.parseInt(key.substring(5)), parsed);
                }
                if (key.equals("SliderBorder")) {
                    Rgb parsed = parseRgb(value);
                    border = parsed != null ? parsed : Colours.defaults().sliderBorder();
                }
                if (key.equals("SliderTrackOverride")) track = parseRgb(value);
                if (key.equals("SpinnerBackground")) {
                    Rgb parsed = parseRgb(value);
                    spinnerBackground = parsed != null ? parsed : Spinner.defaults().background();
                }
            }
            if (!section.equals("Fonts")) continue;
            if (key.equals("HitCirclePrefix") && !value.isEmpty()) prefix = value;
            if (key.equals("ScorePrefix") && !value.isEmpty()) scorePrefix = value;
            if (key.equals("ComboPrefix") && !value.isEmpty()) comboPrefix = value;
            if (key.equals("HitCircleOverlap") || key.equals("ScoreOverlap")
                    || key.equals("ComboOverlap")) {
                try {
                    float parsed = Float.parseFloat(value);
                    if (Float.isFinite(parsed)) {
                        if (key.equals("HitCircleOverlap")) overlap = parsed;
                        else if (key.equals("ScoreOverlap")) scoreOverlap = parsed;
                        else comboOverlap = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed optional settings retain their previous/default value.
                }
            }
        }
        return new SkinConfiguration(new Fonts(prefix, overlap, scorePrefix, scoreOverlap, comboPrefix, comboOverlap), true,
                overlay != null ? overlay : typoOverlay != null ? typoOverlay : true, version, new Colours(border, track, List.copyOf(comboColours.values()), allowSliderBallTint), new Cursor(centre, rotate, expand, trailRotate), new Spinner(noBlink, spinnerBackground), new SongSelect(activeText, inactiveText, activeTextSpecified, inactiveTextSpecified), animationFramerate);
    }
}
