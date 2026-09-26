package dev.osujava.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkinAssetResolverTest {
    @TempDir Path directory;

    @Test
    void sliderBallAnimationWinsUsesNumericOrderAndPerFrameDensity() throws IOException {
        Files.createFile(directory.resolve("sliderb.png"));
        for (int frame = 0; frame < 12; frame++) Files.createFile(directory.resolve("sliderb" + frame + ".png"));
        Files.createFile(directory.resolve("sliderb0@2x.png"));
        Files.createFile(directory.resolve("sliderb9@2x.png"));
        var frames = new SkinAssetResolver(directory).resolveSliderBall();
        assertEquals(12, frames.size());
        for (int frame = 0; frame < 12; frame++) {
            boolean retina = frame == 0 || frame == 9;
            assertEquals(directory.resolve("sliderb" + frame + (retina ? "@2x" : "") + ".png"), frames.get(frame).path());
            assertEquals(retina ? 2 : 1, frames.get(frame).density());
        }
        Files.delete(directory.resolve("sliderb3.png"));
        assertEquals(3, new SkinAssetResolver(directory).resolveSliderBall().size());
        Files.delete(directory.resolve("sliderb0.png"));
        Files.delete(directory.resolve("sliderb0@2x.png"));
        assertEquals(directory.resolve("sliderb.png"), new SkinAssetResolver(directory).resolveSliderBall().getFirst().path());
    }

    @Test
    void staticBallAndMissingBallResolveSafely() throws IOException {
        var resolver = new SkinAssetResolver(directory);
        assertTrue(resolver.resolveSliderBall().isEmpty());
        assertTrue(new SkinAssetResolver(null).resolveSliderBall().isEmpty());
        Files.createFile(directory.resolve("sliderb2.png"));
        assertTrue(resolver.resolveSliderBall().isEmpty());
        Files.createFile(directory.resolve("sliderb.png"));
        assertEquals(directory.resolve("sliderb.png"), resolver.resolveSliderBall().getFirst().path());
        Files.createFile(directory.resolve("sliderb@2x.png"));
        assertEquals(2, resolver.resolveSliderBall().getFirst().density());
    }

    @Test
    void prefersHighResolutionAndPreservesDensity() throws IOException {
        Files.createFile(directory.resolve("hitcircle.png"));
        Path highResolution = Files.createFile(directory.resolve("hitcircle@2x.png"));

        var asset = new SkinAssetResolver(directory).resolve("hitcircle").orElseThrow();

        assertEquals(highResolution, asset.path());
        assertEquals(2, asset.density());
        assertEquals(128, asset.logicalSize(256));
    }

    @Test
    void fallsBackToStandardResolution() throws IOException {
        Path standard = Files.createFile(directory.resolve("approachcircle.png"));

        var asset = new SkinAssetResolver(directory).resolve("approachcircle").orElseThrow();

        assertEquals(standard, asset.path());
        assertEquals(1, asset.density());
        assertEquals(128, asset.logicalSize(128));
    }

    @Test
    void missingImagesAndDirectoriesAreOptional() throws IOException {
        SkinAssetResolver resolver = new SkinAssetResolver(directory);
        assertTrue(resolver.resolve("hitcircleoverlay").isEmpty());
        assertTrue(new SkinAssetResolver(directory.resolve("missing")).resolve("hitcircle").isEmpty());
        Files.createDirectory(directory.resolve("hitcircle@2x.png"));
        assertTrue(resolver.resolve("hitcircle").isEmpty());
    }

    @Test
    void unspecifiedSkinAllowsVectorFallback() {
        assertTrue(new SkinAssetResolver(null).resolve("hitcircle").isEmpty());
    }

    @Test
    void resolvesEachComponentIndependently() throws IOException {
        Files.createFile(directory.resolve("hitcircle@2x.png"));
        Files.createFile(directory.resolve("hitcircleoverlay.png"));
        SkinAssetResolver resolver = new SkinAssetResolver(directory);
        assertEquals(2, resolver.resolve("hitcircle").orElseThrow().density());
        assertEquals(1, resolver.resolve("hitcircleoverlay").orElseThrow().density());
        assertTrue(resolver.resolve("approachcircle").isEmpty());
    }

    @Test
    void resolvesCustomPrefixWithPerDigitDensityAndHighResolutionPriority() throws IOException {
        Files.writeString(directory.resolve("skin.ini"), "[Fonts]\nHitCirclePrefix: Score_Numbers\n");
        for (int digit = 0; digit < 10; digit++) Files.createFile(directory.resolve("Score_Numbers-" + digit + ".png"));
        Path highResolution = Files.createFile(directory.resolve("Score_Numbers-1@2x.png"));
        var digits = new SkinAssetResolver(directory).resolveHitCircleDigits(SkinConfiguration.read(directory)).orElseThrow();
        assertEquals(10, digits.size());
        assertEquals(highResolution, digits.get(1).path());
        assertEquals(2, digits.get(1).density());
        assertEquals(directory.resolve("Score_Numbers-0.png"), digits.get(0).path());
        assertEquals(1, digits.get(0).density());
    }

    @Test
    void fallsBackAsWholeFontForEveryMissingDigit() throws IOException {
        Files.createFile(directory.resolve("skin.ini"));
        for (int digit = 0; digit < 10; digit++) Files.createFile(directory.resolve("default-" + digit + ".png"));
        var configuration = SkinConfiguration.read(directory);
        var resolver = new SkinAssetResolver(directory);
        assertTrue(resolver.resolveHitCircleDigits(configuration).isPresent());
        for (int digit = 0; digit < 10; digit++) {
            Path file = directory.resolve("default-" + digit + ".png");
            Files.delete(file);
            assertTrue(resolver.resolveHitCircleDigits(configuration).isEmpty());
            Files.createFile(file);
        }
        Files.delete(directory.resolve("skin.ini"));
        assertTrue(resolver.resolveHitCircleDigits(SkinConfiguration.read(directory)).isEmpty());
        assertTrue(new SkinAssetResolver(null).resolveHitCircleDigits(configuration).isEmpty());
    }

    @Test
    void customPrefixNeverMixesWithDefaultDigitsAndRejectsPaths() throws IOException {
        for (int digit = 0; digit < 10; digit++) Files.createFile(directory.resolve("default-" + digit + ".png"));
        var resolver = new SkinAssetResolver(directory);
        for (String prefix : new String[]{"score", "../default", "/default", "C:\\default"}) {
            var configuration = new SkinConfiguration(new SkinConfiguration.Fonts(prefix, -2), true);
            assertTrue(resolver.resolveHitCircleDigits(configuration).isEmpty());
        }
    }
}
