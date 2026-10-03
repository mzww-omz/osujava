package dev.osujava.difficulty;

import dev.osujava.beatmap.BeatmapContentKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** A calculation belongs to content, mode, normalized Mods and both numerical contracts. */
public record DifficultyKey(BeatmapContentKey content, List<String> mods, String algorithm, String preprocessing) {
    public DifficultyKey {
        Objects.requireNonNull(content);
        if(mods==null || mods.size()>16 || mods.stream().anyMatch(m->m==null || !m.matches("[A-Z0-9]{1,8}")))
            throw new IllegalArgumentException("Invalid mods");
        mods=mods.stream().distinct().sorted().toList();
        if(algorithm==null || preprocessing==null || !algorithm.matches("[a-zA-Z0-9-]{1,40}") || !preprocessing.matches("[a-zA-Z0-9-]{1,40}"))
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
