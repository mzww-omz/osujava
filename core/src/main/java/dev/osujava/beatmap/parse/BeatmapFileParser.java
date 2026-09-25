package dev.osujava.beatmap.parse;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapFile;
import dev.osujava.beatmap.DifficultySettings;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.TimingPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BeatmapFileParser {
    public BeatmapFile parse(Path path) throws IOException, BeatmapParseException {
        return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
    }

    public BeatmapFile parse(String source, String sourceName) throws BeatmapParseException {
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
                decimal(difficultyValues.get("slidertickrate"), 1));

        List<TimingPoint> timingPoints = parseTimingPoints(timingLines);
        List<HitObject> hitObjects = parseHitObjects(objectLines);
        BeatmapDifficulty difficulty = new BeatmapDifficulty(title, artist, creator, version, mode,
                audioFilename, backgroundFilename, settings, timingPoints, hitObjects, null, null);
        return new BeatmapFile(formatVersion, title, titleUnicode, artist, artistUnicode, creator, difficulty);
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

    private List<HitObject> parseHitObjects(List<String> lines) {
        List<HitObject> objects = new ArrayList<>();
        for (String line : lines) {
            String[] fields = line.split(",", -1);
            if (fields.length < 5) continue;
            try {
                int rawType = Integer.parseInt(fields[3].trim());
                objects.add(new HitObject(Double.parseDouble(fields[0].trim()), Double.parseDouble(fields[1].trim()),
                        Long.parseLong(fields[2].trim()), HitObject.typeFromBits(rawType), rawType,
                        Integer.parseInt(fields[4].trim())));
            } catch (NumberFormatException ignored) {
                // Invalid objects are skipped so one damaged line does not discard a whole set.
            }
        }
        objects.sort(Comparator.comparingLong(HitObject::timeMs));
        return objects;
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
