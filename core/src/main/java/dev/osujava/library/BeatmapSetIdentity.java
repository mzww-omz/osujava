package dev.osujava.library;

import dev.osujava.beatmap.BeatmapFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class BeatmapSetIdentity {
    private BeatmapSetIdentity() {
    }

    static String from(List<BeatmapFile> files) {
        int beatmapSetId = files.stream().map(BeatmapFile::beatmapSetId)
                .filter(value -> value > 0).findFirst().orElse(-1);
        if (beatmapSetId > 0) return "osu-set-" + beatmapSetId;

        BeatmapFile first = files.getFirst();
        List<String> difficulties = files.stream()
                .map(file -> file.difficulty().mode() + ":" + normalize(file.difficulty().version()))
                .sorted()
                .toList();
        String identity = String.join("\n", List.of(
                normalize(first.displayTitle()),
                normalize(first.displayArtist()),
                normalize(first.creator()),
                String.join("\n", difficulties)));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return "local-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
