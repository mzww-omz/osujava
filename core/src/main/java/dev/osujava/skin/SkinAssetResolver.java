package dev.osujava.skin;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    private final String bundledRoot;
    private SkinConfiguration bundledConfiguration;
    public static final String DEFAULT_RESOURCE_ROOT = "skins/default";

    public SkinAssetResolver(Path directory) {
        this(directory, null);
    }

    /** Local providers, custom first. A present (including transparent) file wins. */
    public SkinAssetResolver(Path directory, Path fallbackDirectory) {
        this(directory, fallbackDirectory, null);
    }

    /** Resource root is skin-relative, usable both from IDE classpaths and packaged JARs. */
    public SkinAssetResolver(Path directory, Path fallbackDirectory, String bundledRoot) {
        this.directory = directory;
        this.fallbackDirectory = fallbackDirectory;
        if (bundledRoot != null) validateName(bundledRoot);
        this.bundledRoot = bundledRoot;
    }

    public static SkinAssetResolver withBundledDefault(Path directory, Path fallbackDirectory) {
        return new SkinAssetResolver(directory, fallbackDirectory, DEFAULT_RESOURCE_ROOT);
    }

    /** Configuration belongs to the selected skin, even when its images fall back.
     * No ini means latest; an existing ini without Version means 1.0 (official skin.ini specification). */
    public SkinConfiguration readSelectedConfiguration() throws IOException {
        for (Path source : new Path[]{directory, fallbackDirectory}) {
            if (source == null) continue;
            try {
                SkinConfiguration config = SkinConfiguration.read(source);
                return config.hasIni() ? config : SkinConfiguration.withoutIni();
            } catch (IOException ignored) { return SkinConfiguration.withoutIni(); }
        }
        return bundledConfiguration();
    }

    /** Existing gameplay policy retained until its font/judgement fallback migration is verified. */
    public SkinConfiguration readConfiguration() throws IOException {
        for (Path source : new Path[]{directory, fallbackDirectory}) {
            try {
                SkinConfiguration config = SkinConfiguration.read(source);
                if (config.hasIni()) return config;
            } catch (IOException ignored) { }
        }
        return bundledConfiguration();
    }

    private SkinConfiguration bundledConfiguration() {
        if (bundledConfiguration != null) return bundledConfiguration;
        bundledConfiguration = SkinConfiguration.defaults();
        if (bundledRoot != null) {
            try (InputStream input = SkinAssetResolver.class.getResourceAsStream("/" + bundledRoot + "/skin.ini")) {
                if (input != null) bundledConfiguration = SkinConfiguration.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
            } catch (IOException ignored) { /* A missing/unreadable default ini never prevents asset lookup. */ }
        }
        return bundledConfiguration;
    }

    public Optional<AssetFile> resolve(String name) {
        return resolve(name, file -> true);
    }

    Optional<AssetFile> resolve(String name, Predicate<AssetFile> loadable) {
        validateName(name);
        for (Provider provider : Provider.values()) {
            var file = resolveIn(provider, name, loadable);
            if (file.isPresent()) return file;
        }
        return Optional.empty();
    }

    /** Provider-local lookup for choosing a custom circle family before considering fallback skins. */
    Optional<AssetFile> resolveCustom(String name, Predicate<AssetFile> loadable) {
        validateName(name);
        return resolveIn(Provider.CUSTOM, name, loadable);
    }

    /** Skin samples use unindexed legacy names; indexed beatmap samples stay at the audio caller. */
    public Optional<AssetFile> resolveSound(String name) {
        return resolveSound(name, file -> true);
    }

    public Optional<AssetFile> resolveSound(String name, Predicate<AssetFile> loadable) {
        validateName(name);
        return resolveNamedSound(name.replaceFirst("[0-9]+$", ""), loadable);
    }

    /** Interface sample numbers (key-press-1 etc.) are names, not beatmap sample indices. */
    public Optional<AssetFile> resolveNamedSound(String basename, Predicate<AssetFile> loadable) {
        validateName(basename);
        for (Provider provider : Provider.values()) {
            for (String extension : List.of(".wav", ".ogg", ".mp3")) {
                var file = resolveFile(provider, basename + extension, 1);
                if (file.isPresent() && loadable.test(file.get())) return file;
            }
        }
        return Optional.empty();
    }

    private Optional<AssetFile> resolveFile(Provider provider, String name, int density) {
        if (provider == Provider.BUNDLED) {
            if (bundledRoot == null) return Optional.empty();
            String resource = bundledRoot + "/" + name;
            return SkinAssetResolver.class.getResource("/" + resource) == null ? Optional.empty()
                    : Optional.of(new AssetFile(Path.of(resource), density, true, resource));
        }
        Path source = provider == Provider.CUSTOM ? directory : fallbackDirectory;
        if (source == null || !Files.isRegularFile(source.resolve(name))) return Optional.empty();
        return Optional.of(new AssetFile(source.resolve(name), density, provider != Provider.CUSTOM));
    }

    private static void validateName(String name) {
        // Font prefixes may include skin-relative directories (e.g. Assets/score/score).
        // Validate each component; absolute paths, dot segments, empty segments and Windows paths remain invalid.
        if (!name.matches("[\\p{L}\\p{N}_ -]+(?:/[\\p{L}\\p{N}_ -]+)*"))
            throw new IllegalArgumentException("Expected a skin-relative image basename");
    }

    public boolean isFallback(AssetFile file) {
        return file.fallback();
    }

    private enum Provider { CUSTOM, FALLBACK, BUNDLED }

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

    /** Static UI consumers use frame zero of an animation, preserving provider-local animation/static priority. */
    Optional<AssetFile> resolveAnimationFirstFrame(String name, Predicate<AssetFile> loadable) {
        validateName(name);
        for (Provider provider : Provider.values()) {
            var first = resolveIn(provider, name + "-0", loadable);
            if (first.isPresent()) return first;
            var single = resolveIn(provider, name, loadable);
            if (single.isPresent()) return single;
        }
        return Optional.empty();
    }

    /** Runtime lookup uses successful texture loads, matching lazer's GetTexture rather than file existence. */
    List<AssetFile> resolveAnimation(String name, Predicate<AssetFile> loadable) {
        return resolveAnimation(name, "-", loadable);
    }

    List<AssetFile> resolveAnimation(String name, int maximumFrames, Predicate<AssetFile> loadable) {
        return resolveAnimation(name, "-", maximumFrames, loadable);
    }

    private List<AssetFile> resolveAnimation(String name, String separator, Predicate<AssetFile> loadable) {
        return resolveAnimation(name, separator, Integer.MAX_VALUE, loadable);
    }

    private List<AssetFile> resolveAnimation(String name, String separator, int maximumFrames, Predicate<AssetFile> loadable) {
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
            for (int index = 1; index < maximumFrames; index++) {
                var frame = resolveIn(provider, name + separator + index, loadable);
                if (frame.isEmpty()) break;
                frames.add(frame.get());
            }
            return List.copyOf(frames);
        }
        return List.of();
    }

    private Optional<AssetFile> resolveIn(Provider provider, String name, Predicate<AssetFile> loadable) {
        for (int density : new int[]{2, 1}) {
            var file = resolveFile(provider, name + (density == 2 ? "@2x" : "") + ".png", density);
            if (file.isPresent() && loadable.test(file.get())) return file;
        }
        return Optional.empty();
    }

    /** An incomplete font, absent ini, or unsafe prefix always falls back as a whole. */
    public Optional<List<AssetFile>> resolveHitCircleDigits(SkinConfiguration configuration) {
        return resolveHitCircleDigits(configuration, file -> true);
    }

    Optional<List<AssetFile>> resolveHitCircleDigits(SkinConfiguration configuration, Predicate<AssetFile> loadable) {
        if (!configuration.hasIni()) return Optional.empty();
        List<AssetFile> digits = new ArrayList<>(10);
        try {
            for (int digit = 0; digit < 10; digit++) {
                var file = resolveFont(configuration.fonts().hitCirclePrefix(), String.valueOf(digit), bundledConfiguration().fonts().hitCirclePrefix(), loadable);
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
        return resolveHudGlyph(prefix, character, file -> true);
    }

    Optional<AssetFile> resolveHudGlyph(String prefix, char character, Predicate<AssetFile> loadable) {
        return resolveHudGlyph(prefix, character, false, loadable);
    }

    Optional<AssetFile> resolveHudGlyph(String prefix, char character, boolean combo, Predicate<AssetFile> loadable) {
        String suffix = switch (character) {
            case '.' -> "dot";
            case ',' -> "comma";
            case '%' -> "percent";
            default -> String.valueOf(character);
        };
        var fonts = bundledConfiguration().fonts();
        try { return resolveFont(prefix, suffix, combo ? fonts.comboPrefix() : fonts.scorePrefix(), loadable); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    private Optional<AssetFile> resolveFont(String prefix, String suffix, String bundledPrefix, Predicate<AssetFile> loadable) {
        // Preserve custom prefixes and per-glyph priority; bundled fonts use their own standard names.
        return resolve(prefix + "-" + suffix, loadable)
                .or(() -> {
                    validateName(bundledPrefix + "-" + suffix);
                    return resolveIn(Provider.BUNDLED, bundledPrefix + "-" + suffix, loadable);
                });
    }

    public record AssetFile(Path path, int density, boolean fallback, String classpathResource) {
        public AssetFile(Path path, int density, boolean fallback) { this(path, density, fallback, null); }
        public AssetFile(Path path, int density) { this(path, density, false); }
        public FileHandle handle() {
            return classpathResource == null ? Gdx.files.absolute(path.toAbsolutePath().toString())
                    : Gdx.files.classpath(classpathResource);
        }
        public InputStream openStream() throws IOException {
            if (classpathResource == null) return Files.newInputStream(path);
            InputStream input = SkinAssetResolver.class.getResourceAsStream("/" + classpathResource);
            if (input == null) throw new IOException("Missing skin resource: " + classpathResource);
            return input;
        }
        public float logicalSize(int pixels) {
            return (float) pixels / density;
        }
    }
}
