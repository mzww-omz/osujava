package dev.osujava.beatmap.parse;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapMetadata;
import dev.osujava.beatmap.BeatmapTimingStatistics;
import dev.osujava.beatmap.BeatmapFile;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.BeatmapPlayData;
import dev.osujava.beatmap.BreakPeriod;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import dev.osujava.beatmap.SpinnerData;
import dev.osujava.beatmap.TimingPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BeatmapFileParser {
    public BeatmapFile parse(Path path) throws IOException, BeatmapParseException {
        byte[] bytes = Files.readAllBytes(path);
        return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(),
                path.getFileName().toString(), bytes);
    }

    public BeatmapFile parse(String source, String sourceName) throws BeatmapParseException {
        return parse(source, sourceName, source.getBytes(StandardCharsets.UTF_8));
    }

    private BeatmapFile parse(String source, String sourceName, byte[] bytes) throws BeatmapParseException {
        String text = source.startsWith("\uFEFF") ? source.substring(1) : source;
        int formatVersion = readFormatVersion(text, sourceName);
        Map<String, Map<String, String>> keyValues = new HashMap<>();
        List<String> eventLines = new ArrayList<>();
        List<String> timingLines = new ArrayList<>();
        List<String> objectLines = new ArrayList<>();
        String section = "";

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                continue;
            }
            switch (section) {
                case "events" -> eventLines.add(line);
                case "timingpoints" -> timingLines.add(line);
                case "hitobjects" -> objectLines.add(line);
                default -> {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        keyValues.computeIfAbsent(section, ignored -> new HashMap<>())
                                .put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                    }
                }
            }
        }

        Map<String, String> general = keyValues.getOrDefault("general", Map.of());
        Map<String, String> metadata = keyValues.getOrDefault("metadata", Map.of());
        Map<String, String> difficultyValues = keyValues.getOrDefault("difficulty", Map.of());

        String title = value(metadata, "title", "Unknown title");
        String artist = value(metadata, "artist", "Unknown artist");
        String creator = value(metadata, "creator", "Unknown creator");
        int beatmapSetId = integer(metadata.get("beatmapsetid"), -1);
        String version = value(metadata, "version", "Normal");
        String titleUnicode = blankToNull(value(metadata, "titleunicode", null));
        String artistUnicode = blankToNull(value(metadata, "artistunicode", null));
        String audioFilename = value(general, "audiofilename", "");
        int mode = integer(general.get("mode"), 0);
        String backgroundFilename = findBackground(eventLines);

        DifficultySettings settings = new DifficultySettings(
                decimal(difficultyValues.get("hpdrainrate"), 5),
                decimal(difficultyValues.get("circlesize"), 5),
                decimal(difficultyValues.get("overalldifficulty"), 5),
                decimal(difficultyValues.get("approachrate"), decimal(difficultyValues.get("overalldifficulty"), 5)),
                decimal(difficultyValues.get("slidermultiplier"), 1.4),
                decimal(difficultyValues.get("slidertickrate"), 1),
                decimal(general.get("stackleniency"), 0.7), formatVersion);

        List<TimingPoint> timingPoints = parseTimingPoints(timingLines);
        ParsedObjects parsedObjects = parseHitObjects(objectLines);
        var statistics = BeatmapTimingStatistics.calculate(timingPoints, parsedObjects.firstStartMs(),
                parsedObjects.lastStartMs(), parsedObjects.lastEndMs(), breakDuration(eventLines));
        BeatmapPlayData playData;
        try {
            int leadIn = Integer.parseInt(general.getOrDefault("audioleadin", "0").trim());
            playData = new BeatmapPlayData(parseBreaks(eventLines), leadIn,
                    digest(bytes, "SHA-256"), digest(bytes, "MD5"));
        } catch (IllegalArgumentException e) {
            throw new BeatmapParseException("Invalid break or AudioLeadIn in " + sourceName + ": " + e.getMessage(), e);
        }
        BeatmapDifficulty difficulty = new BeatmapDifficulty(title, artist, creator, version, mode,
                audioFilename, backgroundFilename, settings, timingPoints, parsedObjects.objects(), null, null, null, integer(general.get("previewtime"), -1),
                new BeatmapMetadata(titleUnicode, artistUnicode, value(metadata, "source", ""), value(metadata, "tags", ""),
                        integer(metadata.get("beatmapid"), -1), beatmapSetId), statistics, playData, parsedObjects.skippedCount());
        return new BeatmapFile(formatVersion, title, titleUnicode, artist, artistUnicode, creator,
                beatmapSetId, difficulty);
    }

    private int readFormatVersion(String text, String sourceName) throws BeatmapParseException {
        for (String rawLine : text.split("\\R", 4)) {
            String line = rawLine.trim();
            if (line.startsWith("osu file format v")) {
                try {
                    return Integer.parseInt(line.substring("osu file format v".length()).trim());
                } catch (NumberFormatException e) {
                    throw new BeatmapParseException("Invalid .osu format version in " + sourceName, e);
                }
            }
            if (!line.isEmpty() && !line.startsWith("//")) break;
        }
        throw new BeatmapParseException("Missing osu file format header in " + sourceName);
    }

    private List<TimingPoint> parseTimingPoints(List<String> lines) {
        List<TimingPoint> points = new ArrayList<>();
        for (String line : lines) {
            String[] fields = line.split(",", -1);
            if (fields.length < 2) continue;
            points.add(new TimingPoint(decimal(fields[0], 0), decimal(fields[1], 0),
                    integer(field(fields, 2), 4), integer(field(fields, 3), 0), integer(field(fields, 4), 0),
                    integer(field(fields, 5), 100), bool(field(fields, 6), true), integer(field(fields, 7), 0)));
        }
        points.sort(Comparator.comparingDouble(TimingPoint::timeMs));
        return points;
    }

    private record ParsedObjects(List<HitObject> objects, int firstStartMs, int lastStartMs, int lastEndMs, int skippedCount) { }

    private ParsedObjects parseHitObjects(List<String> lines) {
        List<HitObject> objects = new ArrayList<>();
        int firstStartMs = -1, lastStartMs = -1, lastEndMs = -1;
        int skippedCount = 0;
        for (String line : lines) {
            String[] fields = line.split(",", -1);
            if (fields.length < 5) { skippedCount++; continue; }
            try {
                double x = Double.parseDouble(fields[0].trim());
                double y = Double.parseDouble(fields[1].trim());
                int rawType = Integer.parseInt(fields[3].trim());
                HitObject.Type type = HitObject.typeFromBits(rawType);
                SliderData slider = type == HitObject.Type.SLIDER ? parseSliderData(fields, x, y) : null;
                long timeMs = Long.parseLong(fields[2].trim());
                SpinnerData spinner = type == HitObject.Type.SPINNER ? parseSpinnerData(fields, timeMs) : null;
                // Library-only metadata. This does not add mania gameplay or resolve slider tails.
                double endMs = spinner == null ? timeMs : spinner.endTimeMs();
                if (type == HitObject.Type.HOLD) {
                    if (fields.length < 6) throw new IllegalArgumentException("Hold is missing its end time");
                    endMs = Double.parseDouble(fields[5].split(":", 2)[0].trim());
                    if (!Double.isFinite(endMs)) throw new IllegalArgumentException("Hold end time must be finite");
                }
                if (type == HitObject.Type.SPINNER) {
                    // Legacy osu! spinners always occupy the centre of the playfield.
                    x = 256;
                    y = 192;
                }
                objects.add(new HitObject(x, y, timeMs, type, rawType,
                        Integer.parseInt(fields[4].trim()), slider, spinner));
                if (firstStartMs == -1) firstStartMs = BeatmapTimingStatistics.milliseconds(timeMs);
                if (type != HitObject.Type.UNKNOWN) {
                    lastStartMs = BeatmapTimingStatistics.milliseconds(type == HitObject.Type.HOLD ? endMs : timeMs);
                    lastEndMs = BeatmapTimingStatistics.milliseconds(endMs);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid objects are skipped so one damaged line does not discard a whole set.
                skippedCount++;
            }
        }
        objects.sort(Comparator.comparingLong(HitObject::timeMs));
        return new ParsedObjects(objects, firstStartMs, lastStartMs, lastEndMs, skippedCount);
    }

    private long breakDuration(List<String> lines) {
        long total = 0;
        for (String line : lines) {
            String[] fields = line.split(",", -1);
            if (fields.length < 3 || !(fields[0].trim().equals("2") || fields[0].trim().equalsIgnoreCase("Break"))) continue;
            try {
                int start = Integer.parseInt(fields[1].trim()), end = Integer.parseInt(fields[2].trim());
                if (end >= start) total += (long) end - start;
            } catch (NumberFormatException ignored) {
                // A malformed event does not terminate archive import or discard valid objects.
            }
        }
        return total;
    }

    private SpinnerData parseSpinnerData(String[] fields, long startTimeMs) {
        if (fields.length < 6) throw new IllegalArgumentException("Spinner is missing its end time");
        double endTimeMs = Double.parseDouble(fields[5].trim());
        if (!Double.isFinite(endTimeMs)) throw new IllegalArgumentException("Spinner end time must be finite");
        return new SpinnerData(Math.max(startTimeMs, endTimeMs));
    }

    private SliderData parseSliderData(String[] fields, double startX, double startY) {
        if (fields.length < 7) throw new IllegalArgumentException("Slider is missing path or repeat fields");

        int slides = Integer.parseInt(fields[6].trim());
        if (slides < 1 || slides > 9000) throw new IllegalArgumentException("Slider slides are outside the supported range");
        double pixelLength = fields.length > 7 ? Double.parseDouble(fields[7].trim()) : 0;

        List<SliderData.Segment> segments = new ArrayList<>();
        List<BeatmapPoint> points = new ArrayList<>();
        SliderData.CurveType curveType = SliderData.CurveType.CATMULL;
        int degree = 0;
        boolean hasType = false;
        String[] pathFields = fields[5].trim().split("\\|", -1);
        for (String pathField : pathFields) {
            String token = pathField.trim();
            if (token.isEmpty()) throw new IllegalArgumentException("Slider path contains an empty point");
            if (Character.isLetter(token.charAt(0))) {
                if (hasType) {
                    segments.add(new SliderData.Segment(curveType, degree, points));
                    BeatmapPoint previousEnd = points.getLast();
                    points = new ArrayList<>();
                    points.add(previousEnd);
                } else {
                    points.add(new BeatmapPoint(startX, startY));
                }
                ParsedCurve parsedType = parseCurveType(token);
                curveType = parsedType.curveType();
                degree = parsedType.degree();
                hasType = true;
            } else {
                String[] coordinates = token.split(":", -1);
                if (coordinates.length != 2) throw new IllegalArgumentException("Invalid slider control point");
                double pointX = Double.parseDouble(coordinates[0].trim());
                double pointY = Double.parseDouble(coordinates[1].trim());
                if (!Double.isFinite(pointX) || !Double.isFinite(pointY)) {
                    throw new IllegalArgumentException("Slider control point must be finite");
                }
                if (!hasType) {
                    // Old encoders always emit a curve letter, but treating an omitted one as Catmull
                    // matches lazer's legacy parser fallback and keeps the line recoverable.
                    points.add(new BeatmapPoint(startX, startY));
                    hasType = true;
                }
                points.add(new BeatmapPoint(pointX, pointY));
            }
        }
        if (!hasType) throw new IllegalArgumentException("Slider path has no curve type");
        segments.add(new SliderData.Segment(curveType, degree, points));
        return new SliderData(segments, slides - 1, pixelLength);
    }

    private ParsedCurve parseCurveType(String token) {
        return switch (Character.toUpperCase(token.charAt(0))) {
            case 'B' -> {
                if (token.length() > 1) {
                    int parsedDegree = Integer.parseInt(token.substring(1));
                    if (parsedDegree <= 0) throw new IllegalArgumentException("B-spline degree must be positive");
                    yield new ParsedCurve(SliderData.CurveType.BSPLINE, parsedDegree);
                }
                yield new ParsedCurve(SliderData.CurveType.BEZIER, 0);
            }
            case 'L' -> new ParsedCurve(SliderData.CurveType.LINEAR, 0);
            case 'P' -> new ParsedCurve(SliderData.CurveType.PERFECT, 0);
            case 'C' -> new ParsedCurve(SliderData.CurveType.CATMULL, 0);
            default -> new ParsedCurve(SliderData.CurveType.CATMULL, 0);
        };
    }

    private record ParsedCurve(SliderData.CurveType curveType, int degree) {
    }

    private String findBackground(List<String> lines) {
        for (String line : lines) {
            List<String> fields = csv(line);
            if (fields.size() < 3) continue;
            String eventType = fields.get(0).trim();
            boolean background = eventType.equalsIgnoreCase("Background") || (eventType.equals("0") && fields.get(1).trim().equals("0"));
            if (background) return fields.get(2).trim();
        }
        return "";
    }

    private List<BreakPeriod> parseBreaks(List<String> lines) {
        List<BreakPeriod> breaks = new ArrayList<>();
        for (String line : lines) {
            List<String> fields = csv(line);
            String type = fields.getFirst().trim();
            if (!type.equals("2") && !type.equalsIgnoreCase("Break")) continue;
            if (fields.size() != 3) throw new IllegalArgumentException("Break requires start and end time: " + line);
            breaks.add(new BreakPeriod(Integer.parseInt(fields.get(1).trim()), Integer.parseInt(fields.get(2).trim())));
        }
        // Keep duplicate/overlapping intervals as source facts; don't silently merge or clip them here.
        breaks.sort(Comparator.comparingInt(BreakPeriod::startTimeMs));
        return breaks;
    }

    private static String digest(byte[] bytes, String algorithm) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required digest unavailable: " + algorithm, e);
        }
    }

    private List<String> csv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private String value(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "";
    }

    private double decimal(String value, double fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int integer(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean bool(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().equals("1") || Boolean.parseBoolean(value.trim());
    }
}
