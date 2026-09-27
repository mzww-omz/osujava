package dev.osujava.skin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** File resolution only; no graphics context is required. */
public final class SkinAssetResolver {
    private final Path directory;
    private final Path fallbackDirectory;

    public SkinAssetResolver(Path directory) {
        this(directory, null);
    }

    /** Local providers, custom first. A present (including transparent) file wins. */
    public SkinAssetResolver(Path directory, Path fallbackDirectory) {
        this.directory = directory;
        this.fallbackDirectory = fallbackDirectory;
    }

    public Optional<AssetFile> resolve(String name) {
        validateName(name);
        return resolveIn(directory, name, false).or(() -> resolveIn(fallbackDirectory, name, true));
    }

    private static void validateName(String name) {
        // Font prefixes may include skin-relative directories (e.g. Assets/score/score).
        // Validate each component; absolute paths, dot segments, empty segments and Windows paths remain invalid.
        if (!name.matches("[\\p{L}\\p{N}_ -]+(?:/[\\p{L}\\p{N}_ -]+)*"))
            throw new IllegalArgumentException("Expected a skin-relative image basename");
    }

    private Optional<AssetFile> resolveIn(Path directory, String name, boolean fallback) {
        return resolveIn(directory, name, fallback, file -> true);
    }

    public boolean isFallback(AssetFile file) {
        return file.fallback();
    }

    private enum Provider { CUSTOM, FALLBACK }

    /** Legacy GetTextures("sliderb", animatable=true, separator=""). */
    public List<AssetFile> resolveSliderBall() {
        return resolveSliderBall(file -> true);
    }

    List<AssetFile> resolveSliderBall(Predicate<AssetFile> loadable) {
        return resolveAnimation("sliderb", "", loadable);
    }

    /** Select one provider by frame zero or static presence, then stop at the first missing frame. */
    public List<AssetFile> resolveAnimation(String name) {
        return resolveAnimation(name, file -> true);
    }

    /** Runtime lookup uses successful texture loads, matching lazer's GetTexture rather than file existence. */
    List<AssetFile> resolveAnimation(String name, Predicate<AssetFile> loadable) {
        return resolveAnimation(name, "-", loadable);
    }

    private List<AssetFile> resolveAnimation(String name, String separator, Predicate<AssetFile> loadable) {
        // Validate even when neither provider exists, just as resolve() does.
        validateName(name);
        for (Provider provider : Provider.values()) {
            var first = resolveIn(provider, name + separator + "0", loadable);
            if (first.isEmpty()) {
                var single = resolveIn(provider, name, loadable);
                if (single.isPresent()) return List.of(single.get());
                continue;
            }
            List<AssetFile> frames = new ArrayList<>();
            frames.add(first.get());
            for (int index = 1; ; index++) {
                var frame = resolveIn(provider, name + separator + index, loadable);
                if (frame.isEmpty()) break;
                frames.add(frame.get());
            }
            return List.copyOf(frames);
        }
        return List.of();
    }

    private Optional<AssetFile> resolveIn(Provider provider, String name, Predicate<AssetFile> loadable) {
        Path source = provider == Provider.CUSTOM ? directory : fallbackDirectory;
        return resolveIn(source, name, provider == Provider.FALLBACK, loadable);
    }

    private Optional<AssetFile> resolveIn(Path source, String name, boolean fallback, Predicate<AssetFile> loadable) {
        if (source == null) return Optional.empty();
        AssetFile highResolution = new AssetFile(source.resolve(name + "@2x.png"), 2, fallback);
        if (Files.isRegularFile(highResolution.path()) && loadable.test(highResolution))
            return Optional.of(highResolution);
        AssetFile standard = new AssetFile(source.resolve(name + ".png"), 1, fallback);
        return Files.isRegularFile(standard.path()) && loadable.test(standard) ? Optional.of(standard) : Optional.empty();
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

    public record AssetFile(Path path, int density, boolean fallback) {
        public AssetFile(Path path, int density) { this(path, density, false); }
        public float logicalSize(int pixels) {
            return (float) pixels / density;
        }
    }
}
