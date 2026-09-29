package dev.osujava.score;

import dev.osujava.gameplay.GameplayRunMode;
import dev.osujava.gameplay.ScoreState;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One versioned Properties record per play, matching the library storage style.
 * Reads once at startup; queries return cached immutable sorted lists. */
public final class LocalScoreStore {
    public enum Status { READY, PARTIAL, UNAVAILABLE }
    private final Path directory;
    private final Map<UUID, LocalScore> plays = new HashMap<>();
    private final Map<DifficultyIdentity, List<LocalScore>> scores = new HashMap<>();
    private Status status = Status.READY;
    private long revision;
    /** In-memory store for explicit fixtures and GL-free screen tests. */
    public LocalScoreStore() { directory = null; }
    public LocalScoreStore(Path directory) { this.directory = directory; load(); }
    public Status status() { return status; }
    public long revision() { return revision; }
    public List<LocalScore> query(DifficultyIdentity identity) { return scores.getOrDefault(identity, List.of()); }
    public LocalScore best(DifficultyIdentity identity) {
        var list = query(identity); return list.isEmpty() ? null : list.getFirst();
    }
    public boolean save(LocalScore score, GameplayRunMode mode) {
        if (mode != GameplayRunMode.MANUAL || plays.containsKey(score.playId())) return false;
        score = score.forStorage();
        if (directory != null) {
            Path temporary = null;
            try {
                Files.createDirectories(directory);
                Path target = directory.resolve(score.playId() + ".properties");
                if (Files.exists(target)) return false; // Never overwrite an existing/corrupt/future record.
                temporary = Files.createTempFile(directory, ".score-", ".tmp");
                try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    encode(score).store(out, "osu!java local gameplay score");
                }
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target); }
            } catch (IOException | RuntimeException e) {
                status = Status.UNAVAILABLE; warn(e); return false;
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException e) { warn(e); }
            }
        }
        add(score); return true;
    }
    private void add(LocalScore score) {
        plays.put(score.playId(), score);
        var list = new ArrayList<>(query(score.difficulty())); list.add(score); list.sort(LocalScore.ORDER);
        scores.put(score.difficulty(), List.copyOf(list)); revision++;
    }
    private void load() {
        if (Files.notExists(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".properties")).sorted().toList()) {
                try {
                    Properties p = new Properties();
                    try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(in); }
                    String schema = p.getProperty("schemaVersion");
                    if (!"1".equals(schema) && !"2".equals(schema)) throw new IOException("Unsupported score schema: " + file.getFileName());
                    UUID id = UUID.fromString(p.getProperty("playId"));
                    if (!file.getFileName().toString().equals(id + ".properties")) throw new IOException("Score filename mismatch");
                    LocalScore score = new LocalScore(id, new DifficultyIdentity(p.getProperty("setId"), p.getProperty("osuPath")),
                            Long.parseLong(p.getProperty("playedAt")), new ScoreState(Long.parseLong(p.getProperty("score")),
                            Integer.parseInt(p.getProperty("combo")), Integer.parseInt(p.getProperty("maxCombo")),
                            Integer.parseInt(p.getProperty("count300")), Integer.parseInt(p.getProperty("count100")),
                            Integer.parseInt(p.getProperty("count50")), Integer.parseInt(p.getProperty("misses")),
                            Double.parseDouble(p.getProperty("accuracy"))), "1".equals(schema) ? ScoreDetails.LEGACY : decodeDetails(p));
                    add(score);
                } catch (IOException | RuntimeException e) { status = Status.PARTIAL; warn(e); }
            }
        } catch (IOException | RuntimeException e) { status = Status.UNAVAILABLE; warn(e); }
    }
    private Properties encode(LocalScore s) {
        Properties p = new Properties();
        p.setProperty("schemaVersion", s.details().equals(ScoreDetails.LEGACY) ? "1" : "2"); p.setProperty("playId", s.playId().toString());
        p.setProperty("setId", s.difficulty().setId()); p.setProperty("osuPath", s.difficulty().osuPath());
        p.setProperty("playedAt", Long.toString(s.playedAt()));
        var r = s.result();
        p.setProperty("score", Long.toString(r.score())); p.setProperty("accuracy", Double.toString(r.accuracy()));
        p.setProperty("combo", Integer.toString(r.combo())); p.setProperty("maxCombo", Integer.toString(r.maxCombo()));
        p.setProperty("count300", Integer.toString(r.count300())); p.setProperty("count100", Integer.toString(r.count100()));
        p.setProperty("count50", Integer.toString(r.count50())); p.setProperty("misses", Integer.toString(r.misses()));
        if (!s.details().equals(ScoreDetails.LEGACY)) {
            var d = s.details();
            p.setProperty("scoringVersion", d.scoringVersion());
            p.setProperty("beatmapSha256", d.beatmapSha256()); p.setProperty("beatmapMd5", d.beatmapMd5());
            put(p, "geki", d.geki()); put(p, "katu", d.katu()); put(p, "possibleCombo", d.possibleCombo());
            put(p, "perfect", d.perfect()); put(p, "passed", d.passed());
            if (d.health() != null) p.setProperty("health", d.health().stream()
                    .map(point -> point.timeMs() + ":" + point.value()).collect(java.util.stream.Collectors.joining(";")));
        }
        return p;
    }

    private static void put(Properties p, String key, Object value) { if (value != null) p.setProperty(key, value.toString()); }

    private static ScoreDetails decodeDetails(Properties p) {
        List<ScoreDetails.HealthPoint> health = null;
        if (p.containsKey("health")) {
            health = new ArrayList<>();
            String raw = p.getProperty("health");
            if (raw.length() > 4_000_000) throw new IllegalArgumentException("Health data exceeds limit");
            if (!raw.isEmpty()) {
                String[] points = raw.split(";", -1);
                if (points.length > 100_000) throw new IllegalArgumentException("Too many health points");
                float previous = Float.NEGATIVE_INFINITY;
                for (String point : points) {
                    String[] pair = point.split(":", -1);
                    if (pair.length != 2) throw new IllegalArgumentException("Invalid health pair");
                    var sample = new ScoreDetails.HealthPoint(Float.parseFloat(pair[0]), Float.parseFloat(pair[1]));
                    if (sample.timeMs() < previous) throw new IllegalArgumentException("Health times out of order");
                    health.add(sample); previous = sample.timeMs();
                }
            }
        }
        return new ScoreDetails(p.getProperty("scoringVersion"), p.getProperty("beatmapSha256", ""),
                p.getProperty("beatmapMd5", ""), optionalInt(p, "geki"), optionalInt(p, "katu"),
                optionalInt(p, "possibleCombo"), optionalBoolean(p, "perfect"), optionalBoolean(p, "passed"),
                health, null, null);
    }

    private static Integer optionalInt(Properties p, String key) {
        return p.containsKey(key) ? Integer.valueOf(p.getProperty(key)) : null;
    }

    private static Boolean optionalBoolean(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) return null;
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean: " + key);
        return Boolean.valueOf(value);
    }
    private static void warn(Exception e) { System.err.println("Local score storage: " + e.getMessage()); }
}
