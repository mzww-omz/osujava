package dev.osujava.skin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Windows-authored skin names on case-sensitive filesystems. Exact names always win. */
final class SkinFiles {
    private SkinFiles() { }

    static Path find(Path directory, String relative) {
        if (directory == null) return null;
        Path current = directory;
        for (String component : relative.split("/")) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")
                    || component.indexOf('\\') >= 0 || component.indexOf(':') >= 0) return null;
            Path exact = current.resolve(component);
            if (Files.exists(exact)) { current = exact; continue; }
            try (var children = Files.list(current)) {
                current = children.filter(p -> p.getFileName().toString().equalsIgnoreCase(component))
                        .min(Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
            } catch (IOException e) { return null; }
            if (current == null) return null;
        }
        return Files.isRegularFile(current) ? current : null;
    }
}
