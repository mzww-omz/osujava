package dev.osujava.skin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** File resolution only; no graphics context is required. */
public final class SkinAssetResolver {
    private final Path directory;

    public SkinAssetResolver(Path directory) {
        this.directory = directory;
    }

    public Optional<AssetFile> resolve(String name) {
        if (directory == null) return Optional.empty();
        if (!name.matches("[\\p{L}\\p{N}_ -]+")) throw new IllegalArgumentException("Expected a skin image basename");
        Path highResolution = directory.resolve(name + "@2x.png");
        if (Files.isRegularFile(highResolution)) return Optional.of(new AssetFile(highResolution, 2));
        Path standard = directory.resolve(name + ".png");
        return Files.isRegularFile(standard) ? Optional.of(new AssetFile(standard, 1)) : Optional.empty();
    }

    /** Legacy GetTextures("sliderb", animatable=true, separator=""): animation wins. */
    public List<AssetFile> resolveSliderBall() {
        List<AssetFile> frames = new ArrayList<>();
        for (int index = 0; ; index++) {
            var frame = resolve("sliderb" + index);
            if (frame.isEmpty()) break;
            frames.add(frame.get());
        }
        if (!frames.isEmpty()) return List.copyOf(frames);
        return resolve("sliderb").map(List::of).orElseGet(List::of);
    }

    /** Legacy GetAnimation: zero-based contiguous frames win over a static PNG. */
    public List<AssetFile> resolveAnimation(String name) {
        List<AssetFile> frames = new ArrayList<>();
        for (int index = 0; ; index++) {
            var frame = resolve(name + "-" + index);
            if (frame.isEmpty()) break;
            frames.add(frame.get());
        }
        if (!frames.isEmpty()) return List.copyOf(frames);
        return resolve(name).map(List::of).orElseGet(List::of);
    }

    /** An incomplete font, absent ini, or unsafe prefix always falls back as a whole. */
    public Optional<List<AssetFile>> resolveHitCircleDigits(SkinConfiguration configuration) {
        if (!configuration.hasIni()) return Optional.empty();
        List<AssetFile> digits = new ArrayList<>(10);
        try {
            for (int digit = 0; digit < 10; digit++) {
                var file = resolve(configuration.fonts().hitCirclePrefix() + "-" + digit);
                if (file.isEmpty()) return Optional.empty();
                digits.add(file.get());
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(digits));
    }

    /** LegacySpriteText lookup names; HUD fonts do not require skin.ini to exist. */
    public Optional<AssetFile> resolveHudGlyph(String prefix, char character) {
        String suffix = switch (character) {
            case '.' -> "dot";
            case ',' -> "comma";
            case '%' -> "percent";
            default -> String.valueOf(character);
        };
        try { return resolve(prefix + "-" + suffix); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    public record AssetFile(Path path, int density) {
        public float logicalSize(int pixels) {
            return (float) pixels / density;
        }
    }
}
