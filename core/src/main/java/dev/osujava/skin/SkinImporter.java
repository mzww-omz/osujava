package dev.osujava.skin;

import dev.osujava.archive.SafeArchiveExtractor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Imports once into local storage; gameplay only receives the resulting directory. */
public final class SkinImporter {
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private final Path skinsRoot;

    public SkinImporter(Path skinsRoot) {
        this.skinsRoot = skinsRoot.toAbsolutePath().normalize();
    }

    public Path importFile(Path source) throws SkinImportException {
        Path staging = null;
        try {
            Path archive = source.toAbsolutePath().normalize();
            if (!Files.isRegularFile(archive)) throw new SkinImportException("File does not exist: " + source);
            if (!archive.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osk")) {
                throw new SkinImportException("Choose an .osk file");
            }
            Files.createDirectories(skinsRoot);
            staging = Files.createTempDirectory(skinsRoot, ".import-");
            List<Path> files = SafeArchiveExtractor.extract(archive, staging, MAX_ARCHIVE_BYTES, MAX_ENTRIES);
            if (files.isEmpty()) throw new SkinImportException("The archive does not contain any skin files");
            Path destination = skinsRoot.resolve(contentId(staging, files));
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Skin storage path is not a directory: " + destination);
                }
                return destination;
            }
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, destination);
            }
            return destination;
        } catch (IOException | RuntimeException e) {
            throw new SkinImportException("Could not import skin: " + e.getMessage(), e);
        } finally {
            SafeArchiveExtractor.deleteTreeQuietly(staging);
        }
    }

    private String contentId(Path staging, List<Path> files) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
        byte[] buffer = new byte[16 * 1024];
        for (Path file : files.stream().sorted(Comparator.comparing(path -> staging.relativize(path).toString())).toList()) {
            byte[] name = staging.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(name.length).array());
            digest.update(name);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(Files.size(file)).array());
            try (var input = Files.newInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
