package dev.osujava.skin;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Result;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Style;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JudgementAssetsTest {
    @TempDir Path dir;
    void file(String name) throws Exception { Files.createFile(dir.resolve(name + ".png")); }
    @Test void staticAndZeroBasedAnimationPriorityDensityOrderAndGaps() throws Exception {
        file("hit300"); file("hit300@2x");
        var resolver = new SkinAssetResolver(dir);
        assertEquals(List.of(new SkinAssetResolver.AssetFile(dir.resolve("hit300@2x.png"), 2)), resolver.resolveAnimation("hit300"));
        file("hit300-0"); file("hit300-0@2x"); file("hit300-1"); file("hit300-3");
        var frames = resolver.resolveAnimation("hit300");
        assertEquals(List.of("hit300-0@2x.png", "hit300-1.png"), frames.stream().map(f -> f.path().getFileName().toString()).toList());
        assertEquals(2, frames.getFirst().density()); assertEquals(1, frames.getLast().density());
        Files.delete(dir.resolve("hit300-0.png")); Files.delete(dir.resolve("hit300-0@2x.png"));
        assertEquals("hit300@2x.png", resolver.resolveAnimation("hit300").getFirst().path().getFileName().toString());
    }
    @Test void particleSwitchIsPerResultAndRequiresUsableMainAndParticle() throws Exception {
        file("hit300"); file("particle300@2x"); file("hit100"); file("hit50"); file("particle50"); file("hit0"); file("particle0");
        var assets = new OsuSkinAssets(dir, f -> {
            if (f.path().getFileName().toString().equals("particle50.png")) throw new GdxRuntimeException("broken");
            return new TestTexture();
        });
        assertEquals(Style.NEW, assets.judgementStyle(Result.GREAT));
        assertEquals(2, assets.judgement(Result.GREAT).particle().density());
        assertEquals(Style.OLD, assets.judgementStyle(Result.OK));
        assertEquals(Style.OLD, assets.judgementStyle(Result.MEH));
        assertEquals(Style.OLD, assets.judgementStyle(Result.MISS));
        Files.delete(dir.resolve("hit300.png"));
        var missing = new OsuSkinAssets(dir, f -> new TestTexture());
        assertEquals(Style.NONE, missing.judgementStyle(Result.GREAT));
        assertEquals(Style.NEW, missing.judgementStyle(Result.MEH));
        assets.dispose(); missing.dispose();
    }
    @Test void brokenAnimationOmitsOnlyItsResultAndDoesNotLeak() throws Exception {
        file("hit300-0"); file("hit300-1"); file("hit300"); file("hit100");
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(dir, f -> {
            if (f.path().endsWith("hit300-1.png")) throw new GdxRuntimeException("broken");
            assertFalse(f.path().endsWith("hit300.png"));
            var t = new TestTexture(); loaded.add(t); return t;
        });
        assertEquals(Style.NONE, assets.judgementStyle(Result.GREAT));
        assertEquals(Style.OLD, assets.judgementStyle(Result.OK));
        assertEquals(2, loaded.size()); assertEquals(1, loaded.getFirst().disposals);
        assets.dispose(); assets.dispose(); assertTrue(loaded.stream().allMatch(t -> t.disposals == 1));
    }
    @Test void animatedFramesAndParticlesShareExistingFontCacheAndDisposeOnce() throws Exception {
        Files.writeString(dir.resolve("skin.ini"), "[Fonts]\nScorePrefix: hit300\nComboPrefix: hit300\n");
        file("hit300-0@2x"); file("hit300-1"); file("particle300");
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(dir, f -> { var t = new TestTexture(); loaded.add(t); return t; });
        var asset = assets.judgement(Result.GREAT);
        assertEquals(2, asset.frames().size()); assertEquals(3, loaded.size());
        assertSame(assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '0'), asset.frames().getFirst());
        assertSame(assets.hudGlyph(OsuSkinAssets.HudFont.COMBO, '1'), asset.frames().getLast());
        assertEquals(16, asset.frames().getFirst().logicalWidth());
        for (int i = 0; i < 100; i++) assertSame(asset, assets.judgement(Result.GREAT));
        assertEquals(3, loaded.size()); assets.dispose(); assets.dispose();
        assertTrue(loaded.stream().allMatch(t -> t.disposals == 1)); assertNull(assets.judgement(Result.GREAT));
    }
    @Test void failedAnimationNeverDisposesFrameSharedWithAnotherComponent() throws Exception {
        Files.writeString(dir.resolve("skin.ini"), "[Fonts]\nScorePrefix: hit300\n");
        file("hit300-0"); file("hit300-1");
        var texture = new TestTexture();
        var attempts = new java.util.HashMap<Path, Integer>();
        var assets = new OsuSkinAssets(dir, f -> {
            attempts.merge(f.path(), 1, Integer::sum);
            if (f.path().endsWith("hit300-1.png")) throw new GdxRuntimeException("broken");
            return texture;
        });
        assertEquals(Style.NONE, assets.judgementStyle(Result.GREAT));
        assertSame(texture, assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '0').texture());
        assertEquals(0, texture.disposals);
        assertTrue(attempts.values().stream().allMatch(count -> count == 1));
        assets.dispose(); assets.dispose(); assertEquals(1, texture.disposals);
    }
    @Test void explicitSliderEndMissAndVersionGatedTailHit() throws Exception {
        file("sliderendmiss"); file("sliderpoint10");
        var old = new OsuSkinAssets(dir, f -> new TestTexture());
        assertEquals(Style.OLD, old.judgementStyle(Result.SLIDER_END_MISS));
        assertEquals(Style.SLIDER_POINT, old.judgementStyle(Result.SLIDER_TAIL_HIT));
        Files.writeString(dir.resolve("skin.ini"), "[General]\nVersion: 2\n");
        var modern = new OsuSkinAssets(dir, f -> new TestTexture());
        assertEquals(Style.NONE, modern.judgementStyle(Result.SLIDER_TAIL_HIT));
        old.dispose(); modern.dispose();
    }
    @Test void noSkinDoesNotLoadAnything() {
        var assets = new OsuSkinAssets(null, f -> { fail("Unexpected load"); return null; });
        for (Result r : Result.values()) assertEquals(Style.NONE, assets.judgementStyle(r));
        assets.dispose();
    }
    static class TestTexture extends Texture {
        int disposals;
        TestTexture() { super(); }
        @Override public int getWidth() { return 32; }
        @Override public int getHeight() { return 48; }
        @Override public void setFilter(TextureFilter min, TextureFilter mag) { }
        @Override public void dispose() { disposals++; }
    }
}
