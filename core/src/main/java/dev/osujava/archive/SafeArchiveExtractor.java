package dev.osujava.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Shared ZIP validation/extraction. Callers own a fresh staging directory and its cleanup. */
public final class SafeArchiveExtractor {
    private SafeArchiveExtractor() { }

    public static List<Path> extract(Path archive, Path staging, long maxBytes, int maxEntries) throws IOException {
        if (maxBytes <= 0 || maxEntries <= 0) throw new IllegalArgumentException("Invalid archive limits");
        if (Files.size(archive) > maxBytes) throw new IOException("Archive exceeds the " + maxBytes + " byte import limit");
        Path root = staging.toAbsolutePath().normalize();
        Map<String, ZipEntry> expected = new HashMap<>();
        Set<Path> seen = new HashSet<>();
        long declaredBytes = 0;
        // ZipInputStream alone accepts non-ZIP input or archives with a missing central directory.
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path relative = validatedEntryPath(entry.getName());
                if (!seen.add(relative)) throw new IOException("Duplicate archive path: " + entry.getName());
                if (seen.size() > maxEntries) throw new IOException("Archive exceeds the " + maxEntries + " entry import limit");
                if (entry.getSize() < 0 || entry.getSize() > maxBytes - declaredBytes) {
                    throw new IOException("Archive expands beyond the " + maxBytes + " byte import limit");
                }
                declaredBytes += entry.getSize();
                expected.put(entry.getName(), entry);
            }
        }

        List<Path> files = new ArrayList<>();
        seen.clear();
        long extractedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                Path relative = validatedEntryPath(entry.getName());
                if (!seen.add(relative)) throw new IOException("Duplicate archive path: " + entry.getName());
                ZipEntry declared = expected.remove(entry.getName());
                if (declared == null) throw new IOException("ZIP headers disagree: " + entry.getName());
                Path destination = root.resolve(relative).normalize();
                if (!destination.startsWith(root)) throw new IOException("Unsafe archive path: " + entry.getName());
                long entryBytes = 0;
                if (entry.isDirectory()) {
                    if (zip.read() != -1) throw new IOException("Archive directory contains data: " + entry.getName());
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    // Never overwrite a previously extracted file or follow an existing file link.
                    try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            extractedBytes += count;
                            entryBytes += count;
                            if (extractedBytes > maxBytes) {
                                throw new IOException("Archive expands beyond the " + maxBytes + " byte import limit");
                            }
                            output.write(buffer, 0, count);
                        }
                    }
                    files.add(destination);
                }
                zip.closeEntry();
                if (entryBytes != declared.getSize() || entry.getCrc() != declared.getCrc()) {
                    throw new IOException("ZIP headers disagree: " + entry.getName());
                }
            }
        }
        if (!expected.isEmpty()) throw new IOException("Archive is missing ZIP entries");
        return List.copyOf(files);
    }

    private static Path validatedEntryPath(String name) throws IOException {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.contains("\\")
                || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe archive path: " + name);
        }
        try {
            Path original = Path.of(name);
            Path path = original.normalize();
            if (path.isAbsolute() || path.toString().isEmpty() || path.startsWith("..")) {
                throw new IOException("Unsafe archive path: " + name);
            }
            for (Path component : original) {
                if (component.toString().equals("..")) throw new IOException("Unsafe archive path: " + name);
            }
            return path;
        } catch (RuntimeException e) {
            throw new IOException("Invalid archive path: " + name, e);
        }
    }

    public static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup after an import.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup after an import.
        }
    }
}
