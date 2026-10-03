package dev.osujava.difficulty;

import dev.osujava.beatmap.BeatmapContentKey;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyKeyTest {
    @Test void contentValidationKeepsExactLowercaseAsciiLengthAndModeBoundaries() {
        String hash="0123456789abcdef".repeat(4);
        assertEquals(hash,new BeatmapContentKey(hash,0).sha256());
        assertEquals(3,new BeatmapContentKey(hash,3).mode());
        for(String invalid:Arrays.asList(null,"",hash.substring(1),hash+"0",hash.toUpperCase(Locale.ROOT),hash+"\n","ａ".repeat(64)))
            assertThrows(IllegalArgumentException.class,()->new BeatmapContentKey(invalid,0));
        assertThrows(IllegalArgumentException.class,()->new BeatmapContentKey(hash,-1));
    }

    @Test void normalizedModsAndVersionValidationRemainImmutableAndStrict() {
        var content=new BeatmapContentKey("a".repeat(64),0);
        var input=new ArrayList<>(List.of("HR","HD","HR"));
        var key=new DifficultyKey(content,input,"V-"+"1".repeat(38),"pre");
        input.clear();assertEquals(List.of("HD","HR"),key.mods());
        assertThrows(UnsupportedOperationException.class,()->key.mods().add("EZ"));
        assertEquals(List.of(),new DifficultyKey(content,new ArrayList<>(),"alg","pre").mods());
        for(String invalid:Arrays.asList(null,"","v".repeat(41),"alg/1","v\n","版本")) {
            assertThrows(IllegalArgumentException.class,()->new DifficultyKey(content,List.of(),invalid,"pre"));
            assertThrows(IllegalArgumentException.class,()->new DifficultyKey(content,List.of(),"alg",invalid));
        }
        for(String invalid:Arrays.asList(null,"","hr","123456789","HR\n","ＨＲ"))
            assertThrows(IllegalArgumentException.class,()->new DifficultyKey(content,Arrays.asList(invalid),"alg","pre"));
        assertThrows(IllegalArgumentException.class,()->new DifficultyKey(content,null,"alg","pre"));
        assertThrows(IllegalArgumentException.class,()->new DifficultyKey(content,Collections.nCopies(17,"HR"),"alg","pre"));
        assertEquals(List.of("12345678"),new DifficultyKey(content,List.of("12345678"),"alg","pre").mods());
    }
}
