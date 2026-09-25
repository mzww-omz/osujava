package dev.osujava.ui;

import java.nio.file.Path;
import java.util.function.Consumer;

@FunctionalInterface
public interface BeatmapFileChooser {
    void chooseFile(Consumer<Path> onSelected);
}
