package dev.osujava.skin;

import com.badlogic.gdx.utils.GdxRuntimeException;
import dev.osujava.ruleset.osu.render.LegacyJudgementAnimation.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimationProviderTest {
    @TempDir Path directory;

    // Exercise the file resolver and the loaded assets for every judgement and sliderb.
    @ParameterizedTest
    @CsvSource({
            "0, '0 1 2', 0, false",
            "static, '0 1', static, false",
            "none, '0 1', '0 1', true",
            "'0 1', static, '0 1', false",
            "'0 2', 1, 0, false",
            "1, '0 1', '0 1', true",
            "none, none, none, false"
    })
    void selectsOneProviderForAllFrames(String custom, String fallback, String expected, boolean fromFallback) throws Exception {
        Path other = Files.createDirectory(directory.resolve("fallback"));
        for (String name : List.of("hit300", "hit100", "hit50", "hit0", "sliderb")) {
            create(directory, name, custom);
            create(other, name, fallback);
            var resolver = new SkinAssetResolver(directory, other);
            var frames = name.equals("sliderb") ? resolver.resolveSliderBall() : resolver.resolveAnimation(name);
            assertEquals(paths(fromFallback ? other : directory, name, expected), frames.stream().map(SkinAssetResolver.AssetFile::path).toList());
            assertTrue(frames.stream().allMatch(f -> f.fallback() == fromFallback));
        }
        var assets = new OsuSkinAssets(directory, other, file -> new JudgementAssetsTest.TestTexture());
        assertFrames(assets.sliderBallFrames(), fromFallback ? other : directory, "sliderb", expected, fromFallback);
        for (Result result : List.of(Result.GREAT, Result.OK, Result.MEH, Result.MISS)) {
            var judgement = assets.judgement(result);
            if (expected.equals("none")) assertNull(judgement);
            else assertFrames(judgement.frames(), fromFallback ? other : directory, result.image, expected, fromFallback);
        }
        assets.dispose();
    }

    @ParameterizedTest
    @CsvSource({"hit300, -", "sliderb, ''"})
    void densityIsResolvedOnlyWithinSelectedProvider(String name, String separator) throws Exception {
        Path other = Files.createDirectory(directory.resolve("fallback"));
        for (Path provider : List.of(directory, other)) {
            Files.createFile(provider.resolve(name + separator + "0.png"));
            Files.createFile(provider.resolve(name + separator + "0@2x.png"));
            Files.createFile(provider.resolve(name + separator + "1.png"));
        }
        Files.createFile(other.resolve(name + separator + "1@2x.png"));
        Files.createFile(other.resolve(name + separator + "2@2x.png"));
        var resolver = new SkinAssetResolver(directory, other);
        var frames = name.equals("sliderb") ? resolver.resolveSliderBall() : resolver.resolveAnimation(name);
        assertEquals(List.of(2, 1), frames.stream().map(SkinAssetResolver.AssetFile::density).toList());
        assertTrue(frames.stream().noneMatch(SkinAssetResolver.AssetFile::fallback));
        Files.delete(directory.resolve(name + separator + "0.png"));
        Files.delete(directory.resolve(name + separator + "0@2x.png"));
        frames = name.equals("sliderb") ? resolver.resolveSliderBall() : resolver.resolveAnimation(name);
        assertEquals(List.of(2, 2, 2), frames.stream().map(SkinAssetResolver.AssetFile::density).toList());
        assertTrue(frames.stream().allMatch(SkinAssetResolver.AssetFile::fallback));
    }

    @ParameterizedTest
    @CsvSource({
            "0, none, '0 1 2', true",
            "static, static, '0 1 2', true",
            "'0 static', static, '0 1 2', true",
            "0, static, static, false",
            "1, static, 0, false",
            "2, none, '0 1', false",
            "0@2x, none, '0 1 2', false"
    })
    void brokenLoadsDetermineSelectionAndTerminateFrames(String broken, String customStatic, String expected, boolean fromFallback) throws Exception {
        Path other = Files.createDirectory(directory.resolve("fallback"));
        for (String name : List.of("hit300", "sliderb")) {
            if (!broken.equals("static")) create(directory, name, "0 1 2");
            create(directory, name, customStatic);
            create(other, name, "0 1 2");
            if (broken.equals("0@2x")) create(directory, name, broken);
        }
        List<Path> attempts = new ArrayList<>();
        var textures = new ArrayList<JudgementAssetsTest.TestTexture>();
        var assets = new OsuSkinAssets(directory, other, file -> {
            attempts.add(file.path());
            boolean bad = paths(directory, "hit300", broken).contains(file.path())
                    || paths(directory, "sliderb", broken).contains(file.path());
            if (bad) throw new GdxRuntimeException("Broken PNG");
            var texture = new JudgementAssetsTest.TestTexture();
            textures.add(texture);
            return texture;
        });
        assertFrames(assets.judgement(Result.GREAT).frames(), fromFallback ? other : directory, "hit300", expected, fromFallback);
        assertFrames(assets.sliderBallFrames(), fromFallback ? other : directory, "sliderb", expected, fromFallback);
        assertEquals(attempts.size(), attempts.stream().distinct().count());
        if (!fromFallback) assertTrue(attempts.stream().noneMatch(p -> p.startsWith(other)));
        assertTrue(textures.stream().allMatch(t -> t.disposals == 0));
        assets.dispose();
        assets.dispose();
        assertTrue(textures.stream().allMatch(t -> t.disposals == 1));
    }

    @Test void transparentStaticWinsWhileSingleTexturesParticlesAndGlyphsStillFallBackIndividually() throws Exception {
        Path other = Files.createDirectory(directory.resolve("fallback"));
        byte[] transparent = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAaX2RaAAAAAASUVORK5CYII=");
        for (String name : List.of("hit300", "sliderb", "cursor")) Files.write(directory.resolve(name + ".png"), transparent);
        assertEquals(0, javax.imageio.ImageIO.read(directory.resolve("hit300.png").toFile()).getRGB(0, 0) >>> 24);
        create(other, "hit300", "0 1");
        create(other, "sliderb", "0 1");
        for (String name : List.of("cursor", "spinner-rpm", "spinner-metre", "hitcircle", "particle300", "score-6"))
            Files.createFile(other.resolve(name + ".png"));
        Files.createFile(directory.resolve("score-5.png"));
        Files.createFile(other.resolve("score-5@2x.png"));
        Files.writeString(directory.resolve("skin.ini"), "[Fonts]\nHitCirclePrefix: score\n");
        for (int i = 0; i < 10; i++) if (i != 6) Files.createFile(directory.resolve("score-" + i + "@2x.png"));
        var assets = new OsuSkinAssets(directory, other, file -> new JudgementAssetsTest.TestTexture());
        assertFrames(assets.judgement(Result.GREAT).frames(), directory, "hit300", "static", false);
        assertFrames(assets.sliderBallFrames(), directory, "sliderb", "static", false);
        assertTrue(assets.judgement(Result.GREAT).particle().file().fallback());
        assertFalse(assets.diagnostic(OsuSkinAssets.Image.CURSOR).fallback());
        for (var image : List.of(OsuSkinAssets.Image.SPINNER_RPM, OsuSkinAssets.Image.SPINNER_METRE, OsuSkinAssets.Image.HIT_CIRCLE))
            assertTrue(assets.diagnostic(image).fallback());
        assertFalse(assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '5').file().fallback());
        assertTrue(assets.hudGlyph(OsuSkinAssets.HudFont.SCORE, '6').file().fallback());
        assertTrue(assets.hasHitCircleDigits());
        assertFalse(assets.hitCircleDigit(5).file().fallback());
        assertTrue(assets.hitCircleDigit(6).file().fallback());
        assets.dispose();
    }

    private void create(Path provider, String name, String frames) throws Exception {
        for (Path path : paths(provider, name, frames)) Files.createFile(path);
    }

    private List<Path> paths(Path provider, String name, String frames) {
        if (frames.equals("none")) return List.of();
        String separator = name.equals("sliderb") ? "" : "-";
        return java.util.Arrays.stream(frames.split(" "))
                .map(frame -> provider.resolve(name + (frame.equals("static") ? "" : separator + frame) + ".png")).toList();
    }

    private void assertFrames(List<OsuSkinAssets.SkinTexture> frames, Path provider, String name, String expected, boolean fallback) {
        assertEquals(paths(provider, name, expected), frames.stream().map(f -> f.file().path()).toList());
        assertTrue(frames.stream().allMatch(f -> f.file().fallback() == fallback));
    }
}
