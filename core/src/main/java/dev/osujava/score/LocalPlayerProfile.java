package dev.osujava.score;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/** Optional local profile. No implicit OS name and no automatic rewrite while reading. */
public final class LocalPlayerProfile {
    private LocalPlayerProfile() { }

    public static LocalPlayer load(Path file, String requestedName) {
        LocalPlayer current = null;
        try {
            if (Files.exists(file)) {
                var p = new Properties();
                try (var in = Files.newBufferedReader(file,StandardCharsets.UTF_8)) { p.load(in); }
                if (!"1".equals(p.getProperty("schemaVersion"))) throw new IOException("Unsupported local profile schema");
                current = new LocalPlayer(UUID.fromString(p.getProperty("playerId")),p.getProperty("playerName"));
            }
        } catch (IOException | RuntimeException e) {
            warn(e); return null; // Preserve damaged/future data, even with a supplied name.
        }
        if (requestedName == null || requestedName.isBlank()) return current;
        Path temporary = null;
        try {
            var next = new LocalPlayer(current == null ? UUID.randomUUID() : current.id(),requestedName);
            if (next.equals(current)) return current;
            Path target = file.toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(),".player-",".tmp");
            var p = new Properties();
            p.setProperty("schemaVersion","1"); p.setProperty("playerId",next.id().toString()); p.setProperty("playerName",next.name());
            try (var out = Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)) { p.store(out,"osu!java local player"); }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
            return next;
        } catch (IOException | RuntimeException e) { warn(e); return current; }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException e) { warn(e); } }
    }
    private static void warn(Exception e) { System.err.println("Local player profile: " + e.getMessage()); }
}
