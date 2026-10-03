package dev.osujava.difficulty;

import dev.osujava.beatmap.BeatmapContentKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/** A calculation belongs to content, mode, normalized Mods and both numerical contracts. */
public record DifficultyKey(BeatmapContentKey content, List<String> mods, String algorithm, String preprocessing) {
    private static final Pattern MOD = Pattern.compile("[A-Z0-9]{1,8}");
    private static final Pattern VERSION = Pattern.compile("[a-zA-Z0-9-]{1,40}");

    public DifficultyKey {
        Objects.requireNonNull(content);
        if(mods==null || mods.size()>16)
            throw new IllegalArgumentException("Invalid mods");
        if(mods.isEmpty()) mods=List.of();
        else {
            if(mods.stream().anyMatch(m->m==null || !MOD.matcher(m).matches()))
                throw new IllegalArgumentException("Invalid mods");
            mods=mods.stream().distinct().sorted().toList();
        }
        if(algorithm==null || preprocessing==null || !VERSION.matcher(algorithm).matches() || !VERSION.matcher(preprocessing).matches())
            throw new IllegalArgumentException("Invalid calculator version");
    }
    public static DifficultyKey nm(BeatmapContentKey content) {
        return new DifficultyKey(content,List.of(),StandardDifficultyCalculator.ALGORITHM_VERSION,StandardDifficultyCalculator.PREPROCESS_VERSION);
    }
    String filename() {
        try {
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join(",",mods).getBytes(StandardCharsets.UTF_8)));
            return content.sha256()+"-"+content.mode()+"-"+digest+"-"+algorithm+"-"+preprocessing+".properties";
        } catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
