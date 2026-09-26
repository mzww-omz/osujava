package dev.osujava.skin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** File resolution only; no graphics context is required. */
public final class SkinAssetResolver {
    private final Path directory;

    public SkinAssetResolver(Path directory) {
        this.directory = directory;
    }

    public Optional<AssetFile> resolve(String name) {
        if (directory == null) return Optional.empty();
        if (!name.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Expected a skin image basename");
        Path highResolution = directory.resolve(name + "@2x.png");
        if (Files.isRegularFile(highResolution)) return Optional.of(new AssetFile(highResolution, 2));
        Path standard = directory.resolve(name + ".png");
        return Files.isRegularFile(standard) ? Optional.of(new AssetFile(standard, 1)) : Optional.empty();
    }

    public record AssetFile(Path path, int density) {
        public float logicalSize(int pixels) {
            return (float) pixels / density;
        }
    }
}
