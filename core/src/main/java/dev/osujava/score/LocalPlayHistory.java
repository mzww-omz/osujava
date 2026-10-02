package dev.osujava.score;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.gameplay.GameplayRunMode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local manual attempts, separate from scores. Reading an unfinished attempt never invents an end. */
public final class LocalPlayHistory {
    public enum Outcome { UNKNOWN, COMPLETED, FAILED, ABORTED }
    public enum Status { READY, PARTIAL, UNAVAILABLE }
    public record Attempt(UUID playId, DifficultyIdentity location, BeatmapContentKey content,
                          long startedAt, Long endedAt, Outcome outcome) {
        public Attempt {
            Objects.requireNonNull(playId); Objects.requireNonNull(location); Objects.requireNonNull(content);
            Objects.requireNonNull(outcome);
            if (startedAt < 0 || (outcome == Outcome.UNKNOWN) != (endedAt == null)
                    || endedAt != null && endedAt < startedAt) throw new IllegalArgumentException("Invalid attempt times/outcome");
        }
    }
    private final Path directory;
    private final Map<UUID,Attempt> attempts = new HashMap<>();
    private final Map<BeatmapContentKey,Long> lastPlayed = new HashMap<>();
    private long revision;
    private Status status = Status.READY;
    public LocalPlayHistory() { directory = null; }
    public LocalPlayHistory(Path directory) { this.directory = directory; load(); }
    public long revision() { return revision; }
    public Status status() { return status; }
    public Attempt attempt(UUID id) { return attempts.get(id); }
    public Long lastPlayed(BeatmapContentKey content) { return lastPlayed.get(content); }
    public boolean start(UUID id, DifficultyIdentity location, BeatmapContentKey content, long time, GameplayRunMode mode) {
        if (mode != GameplayRunMode.MANUAL || location == null || content == null || attempts.containsKey(id)) return false;
        var next = new Attempt(id,location,content,time,null,Outcome.UNKNOWN);
        if (!write(next,false)) return false;
        publish(next); return true;
    }
    public boolean finish(UUID id, long time, Outcome outcome) {
        var previous = attempts.get(id);
        if (previous == null || previous.outcome() != Outcome.UNKNOWN || outcome == Outcome.UNKNOWN) return false;
        var next = new Attempt(id,previous.location(),previous.content(),previous.startedAt(),
                Math.max(previous.startedAt(),time),outcome);
        if (!write(next,true)) return false;
        publish(next); return true;
    }
    private void publish(Attempt next) {
        attempts.put(next.playId(),next); lastPlayed.merge(next.content(),next.startedAt(),Math::max); revision++;
    }
    private boolean write(Attempt a, boolean replacing) {
        if (directory == null) return true;
        Path temporary = null;
        try {
            Files.createDirectories(directory); var target=directory.resolve(a.playId()+".properties");
            if (!replacing && Files.exists(target)) return false;
            if (replacing && !Objects.equals(attempts.get(a.playId()),read(target)))
                throw new IOException("Attempt changed on disk; preserving external record");
            var p=new Properties(); p.setProperty("schemaVersion","1"); p.setProperty("playId",a.playId().toString());
            p.setProperty("setId",a.location().setId()); p.setProperty("osuPath",a.location().osuPath());
            p.setProperty("sha256",a.content().sha256()); p.setProperty("mode",Integer.toString(a.content().mode()));
            p.setProperty("startedAt",Long.toString(a.startedAt())); p.setProperty("outcome",a.outcome().name());
            if (a.endedAt()!=null) p.setProperty("endedAt",Long.toString(a.endedAt()));
            temporary=Files.createTempFile(directory,".attempt-",".tmp");
            try (var out=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)) { p.store(out,"osu!java local manual attempt"); }
            var options = replacing ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
            try { Files.move(temporary,target,options); }
            catch (AtomicMoveNotSupportedException e) {
                if (replacing) Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary,target);
            }
            return true;
        } catch (IOException | RuntimeException e) { status=Status.UNAVAILABLE; warn(e); return false; }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException e) { warn(e); } }
    }
    private void load() {
        if (Files.notExists(directory)) return;
        try (var files=Files.list(directory)) {
            for (var file:files.filter(p -> p.toString().endsWith(".properties")).sorted().toList()) {
                try {
                    publish(read(file));
                } catch(IOException | RuntimeException e) { status=Status.PARTIAL; warn(e); }
            }
        } catch(IOException | RuntimeException e) { status=Status.UNAVAILABLE; warn(e); }
    }
    private static Attempt read(Path file) throws IOException {
        var p=new Properties(); try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { p.load(in); }
        if (!"1".equals(p.getProperty("schemaVersion"))) throw new IOException("Unsupported attempt schema");
        var id=UUID.fromString(p.getProperty("playId"));
        if (!file.getFileName().toString().equals(id+".properties")) throw new IOException("Attempt filename mismatch");
        return new Attempt(id,new DifficultyIdentity(p.getProperty("setId"),p.getProperty("osuPath")),
                new BeatmapContentKey(p.getProperty("sha256"),Integer.parseInt(p.getProperty("mode"))),
                Long.parseLong(p.getProperty("startedAt")),p.containsKey("endedAt") ? Long.valueOf(p.getProperty("endedAt")) : null,
                Outcome.valueOf(p.getProperty("outcome")));
    }
    private static void warn(Exception e) { System.err.println("Local play history: "+e.getMessage()); }
}
