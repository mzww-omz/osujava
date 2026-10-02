package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.time.*;
import java.util.function.Function;

/** Compiled local predicates over one difficulty. No I/O, rating calculation or browser state. */
final class SongBrowserQuery {
    record Document(BeatmapDifficulty difficulty, String title, String artist, String artistUnicode,
                    String creator, String version, List<String> text) {
        static Document of(BeatmapDifficulty difficulty) {
            var metadata = difficulty.metadata();
            String title = normalize(difficulty.title()), artist = normalize(difficulty.artist());
            String artistUnicode = normalize(metadata.artistUnicode()), creator = normalize(difficulty.creator());
            String version = normalize(difficulty.version());
            return new Document(difficulty, title, artist, artistUnicode, creator, version,
                    List.of(title, artist, creator, version, normalize(metadata.titleUnicode()), artistUnicode,
                            normalize(metadata.source()), normalize(metadata.tags())));
        }
        boolean contains(String token) { return text.stream().anyMatch(field -> field.contains(token)); }
    }

    // Common stable field syntax. Unsupported fields/malformed terms remain literal searches;
    // Official server dates, mode conversion and rating still require actual sources.
    private static final Pattern FIELD = Pattern.compile("^([a-z]+)(==|!=|<=|>=|=|<|>)(.*)$");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    private final List<Predicate<Document>> predicates;
    private final Function<BeatmapDifficulty,SongBrowserActivity.Facts> activity;
    private final long now;
    private final ZoneId zone;

    SongBrowserQuery(String query) {
        this(query,d -> SongBrowserActivity.Facts.UNKNOWN,Clock.systemDefaultZone());
    }
    SongBrowserQuery(String query, Function<BeatmapDifficulty,SongBrowserActivity.Facts> activity, Clock clock) {
        this.activity=activity; now=clock.millis(); zone=clock.getZone();
        predicates = tokens(normalize(query)).stream().map(this::compile).toList();
    }

    boolean matches(Document document) { return predicates.stream().allMatch(p -> p.test(document)); }

    /** 060013a6/141f: ASCII spaces; paired quotes open only after '=' and close while quoted. */
    static List<String> tokens(String query) {
        int quotes = (int) query.chars().filter(c -> c == '"').count() & ~1;
        int used = 0;
        boolean quoted = false;
        char previous = ' ';
        var result = new ArrayList<String>();
        var token = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c != ' ' || quoted) {
                if (c == '"' && used < quotes && (quoted || previous == '=')) {
                    quoted = !quoted;
                    used++;
                } else token.append(c);
            } else if (!token.isEmpty()) {
                result.add(token.toString());
                token.setLength(0);
            }
            previous = c;
        }
        if (!token.isEmpty()) result.add(token.toString());
        return List.copyOf(result);
    }

    private Predicate<Document> compile(String token) {
        var field = FIELD.matcher(token);
        if (field.matches()) {
            String name = field.group(1), operator = field.group(2), value = field.group(3);
            boolean equal = operator.equals("=") || operator.equals("==");
            if (name.equals("unplayed") && value.isEmpty()) return d -> activity.apply(d.difficulty()).lastPlayedAt()==null;
            if (name.equals("played") && NUMBER.matcher(value).matches()) {
                double days=Double.parseDouble(value);
                if(Double.isFinite(days)) return d -> {
                    var facts=activity.apply(d.difficulty());
                    return facts.lastPlayedAt()!=null && compare(facts.daysSincePlayed(now),operator,days);
                };
            }
            // Local extension: import calendar date, not an official creation/ranked date.
            if (name.equals("added")) try {
                long date=LocalDate.parse(value).toEpochDay();
                return d -> {
                    var time=activity.apply(d.difficulty()).addedAt();
                    return time!=null && compare(Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay(),operator,date);
                };
            } catch(DateTimeException ignored) { }
            // 06001405–1408 compare Contains to (operator == '='); other operators invert it.
            Predicate<Document> text = switch (name) {
                case "title" -> d -> d.title().contains(value);
                case "artist" -> d -> d.artist().contains(value) || d.artistUnicode().contains(value);
                case "creator" -> d -> d.creator().contains(value);
                case "difficulty" -> d -> d.version().contains(value);
                default -> null;
            };
            if (text != null) return equal ? text : text.negate();
            if (name.equals("mode")) {
                // 060013aa/1401: the enum dictionary accepts prefixes and otherwise returns NaN.
                // Only the public osu/taiko/catch/mania aliases are established here.
                double target = mode(value);
                return d -> compare(d.difficulty().mode(), operator, target);
            }
            if (List.of("ar", "cs", "od", "hp", "bpm", "length", "drain").contains(name) && NUMBER.matcher(value).matches()) {
                double target = Double.parseDouble(value);
                if (Double.isFinite(target)) return d -> numeric(d.difficulty(), name, operator, target);
            }
        }
        // 06001411: an identifier match is ORed with ordinary full-text matching.
        try {
            int id = Integer.parseInt(token);
            if (id > 0) return d -> d.difficulty().metadata().beatmapId() == id
                    || d.difficulty().metadata().beatmapSetId() == id || d.contains(token);
        } catch (NumberFormatException ignored) { }
        return d -> d.contains(token);
    }

    private static boolean numeric(BeatmapDifficulty difficulty, String field, String operator, double target) {
        // 060013f8/13fb: CS and AR do not match taiko/mania, even for '!='.
        if ((field.equals("ar") || field.equals("cs")) && (difficulty.mode() == 1 || difficulty.mode() == 3)) return false;
        var settings = difficulty.settings();
        var timing = difficulty.timingStatistics();
        double value = switch (field) {
            case "ar" -> settings.approachRate();
            case "cs" -> settings.circleSize();
            case "od" -> settings.overallDifficulty();
            case "hp" -> settings.hpDrainRate();
            case "bpm" -> timing.commonBpm();
            case "length" -> timing.lengthSeconds();
            case "drain" -> timing.drainSeconds();
            default -> throw new IllegalArgumentException(field);
        };
        // Native fields are single precision, promoted to double then rounded to one decimal (ties to even).
        if (List.of("ar", "cs", "od", "hp").contains(field)) value = Math.rint((double) (float) value * 10) / 10;
        if (!Double.isFinite(value)) return false;
        return compare(value, operator, target);
    }

    private static double mode(String value) {
        if (!value.isEmpty()) {
            var names = List.of("osu", "taiko", "catch", "mania");
            for (int i = 0; i < names.size(); i++) if (names.get(i).startsWith(value)) return i;
        }
        return Double.NaN;
    }

    private static boolean compare(double value, String operator, double target) {
        // Double.CompareTo in .NET orders every finite mode above the NaN returned for an
        // unknown/empty enum name. Java's Double.compare orders NaN the other way around.
        int order = Double.isNaN(target) ? 1 : value == target ? 0 : value < target ? -1 : 1;
        return switch (operator) {
            case "=", "==" -> order == 0;
            case "!=" -> order != 0;
            case "<" -> order < 0;
            case ">" -> order > 0;
            case "<=" -> order <= 0;
            case ">=" -> order >= 0;
            default -> false;
        };
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
