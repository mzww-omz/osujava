package dev.osujava.skin;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OsuSkinAssetsTest {
    @TempDir Path directory;

    @Test
    void loadsOnceReusesTexturesAndDisposesWithScreen() throws IOException {
        createFont();
        Files.createFile(directory.resolve("hitcircle.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        assertTrue(assets.hasHitCircleDigits());
        assertEquals(11, loaded.size());
        assertSame(assets.hitCircleDigit(1), assets.hitCircleDigit(1));
        assertEquals(32f, assets.hitCircleDigit(1).logicalWidth());
        assertEquals(-2f, assets.hitCircleOverlap());
        for (int frame = 0; frame < 100; frame++) assets.hitCircleDigit(frame % 10);
        assertEquals(11, loaded.size());
        assets.dispose();
        assertFalse(assets.hasHitCircleDigits());
        assertNull(assets.get(OsuSkinAssets.Image.HIT_CIRCLE));
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @Test
    void failedDigitLoadDisposesPartialFontAndKeepsCircleAsset() throws IOException {
        createFont();
        Files.createFile(directory.resolve("hitcircle.png"));
        List<TestTexture> loaded = new ArrayList<>();
        var assets = new OsuSkinAssets(directory, file -> {
            if (file.path().getFileName().toString().equals("default-5.png")) {
                throw new GdxRuntimeException("Broken PNG");
            }
            var texture = new TestTexture();
            loaded.add(texture);
            return texture;
        });
        assertFalse(assets.hasHitCircleDigits());
        assertEquals(6, loaded.size()); // circle + digits 0..4
        assertEquals(0, loaded.getFirst().disposals);
        assertTrue(loaded.subList(1, loaded.size()).stream().allMatch(texture -> texture.disposals == 1));
        assertNotNull(assets.get(OsuSkinAssets.Image.HIT_CIRCLE));
        assets.dispose();
        assertTrue(loaded.stream().allMatch(texture -> texture.disposals == 1));
    }

    @Test
    void absentIniOrMissingDigitNeverLoadsPartialFont() throws IOException {
        createFont();
        Files.delete(directory.resolve("skin.ini"));
        var assets = new OsuSkinAssets(directory, file -> { fail("Must not load without ini"); return null; });
        assertFalse(assets.hasHitCircleDigits());
        Files.createFile(directory.resolve("skin.ini"));
        Files.delete(directory.resolve("default-9.png"));
        assets = new OsuSkinAssets(directory, file -> { fail("Must not load an incomplete font"); return null; });
        assertFalse(assets.hasHitCircleDigits());
    }

    private void createFont() throws IOException {
        Files.writeString(directory.resolve("skin.ini"), "[Fonts]\n");
        for (int digit = 0; digit < 10; digit++) Files.createFile(directory.resolve("default-" + digit + ".png"));
    }

    /** Texture's protected empty constructor requires no GL/native libraries. */
    private static class TestTexture extends Texture {
        int disposals;
        TestTexture() { super(); }
        @Override public int getWidth() { return 32; }
        @Override public int getHeight() { return 48; }
        @Override public void setFilter(TextureFilter min, TextureFilter mag) { }
        @Override public void dispose() { disposals++; }
    }
}
