package dev.osujava.skin;

import com.badlogic.gdx.graphics.Texture;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.osujava.skin.ResultsSkinAssets.Image.*;

class ResultsSkinAssetsTest {
    @TempDir Path root;
    @Test void judgementStaticWinsWithinProviderButCustomFrameBeatsFallbackStatic() throws Exception {
        Path fallback=Files.createDirectory(root.resolve("fallback"));
        for(String name:List.of("hit300","hit300-0","hit100-0")) Files.createFile(root.resolve(name+".png"));
        Files.createFile(fallback.resolve("hit100.png"));
        var assets=new ResultsSkinAssets(new SkinAssetResolver(root,fallback),file->new TestTexture());
        assertEquals(root.resolve("hit300.png"),assets.get(HIT300).file().path());
        assertEquals(root.resolve("hit100-0.png"),assets.get(HIT100).file().path());
        assets.dispose();
    }
    @Test void densityAndCorruptCandidatesFallBackAndAllTexturesAreDisposedExactlyOnce() throws Exception {
        Files.createFile(root.resolve("hit300@2x.png")); Files.createFile(root.resolve("hit300.png"));
        var textures=new ArrayList<TestTexture>();
        var assets=new ResultsSkinAssets(SkinAssetResolver.withBundledDefault(root,null),file->{
            if(file.path().getFileName().toString().equals("hit300@2x.png")) throw new IllegalArgumentException("corrupt");
            var texture=new TestTexture(); textures.add(texture); return texture;
        });
        assertEquals(1,assets.get(HIT300).file().density());
        assertTrue(assets.hasText("123.00%x")); assertNotNull(assets.get(RETRY));
        assets.dispose(); assets.dispose();
        assertTrue(textures.stream().allMatch(t->t.disposed==1));
    }
    @Test void modernRetryIgnoresFallbackRankingNameButLegacyMayUseIt() throws Exception {
        Path fallback=Files.createDirectory(root.resolve("fallback"));
        Files.createFile(fallback.resolve("ranking-retry.png")); Files.createFile(fallback.resolve("pause-retry.png"));
        Files.writeString(root.resolve("skin.ini"),"[General]\nVersion:2");
        var modern=new ResultsSkinAssets(new SkinAssetResolver(root,fallback),file->new TestTexture());
        assertFalse(modern.legacy()); assertEquals("pause-retry.png",modern.get(RETRY).file().path().getFileName().toString());
        modern.dispose();
        Files.writeString(root.resolve("skin.ini"),"[General]\nVersion:1");
        var old=new ResultsSkinAssets(new SkinAssetResolver(root,fallback),file->new TestTexture());
        assertTrue(old.legacy()); assertEquals("ranking-retry.png",old.get(RETRY).file().path().getFileName().toString());
        old.dispose();
    }
    private static class TestTexture extends Texture {
        int disposed;
        TestTexture(){super();}
        @Override public int getWidth(){return 32;}
        @Override public int getHeight(){return 32;}
        @Override public void setFilter(TextureFilter min,TextureFilter mag){}
        @Override public void dispose(){disposed++;}
    }
}
