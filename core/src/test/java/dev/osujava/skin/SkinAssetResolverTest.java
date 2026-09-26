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
}
