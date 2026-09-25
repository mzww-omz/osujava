package dev.osujava.lwjgl3;

import dev.osujava.ui.BeatmapFileChooser;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.EventQueue;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.Locale;

public final class DesktopFileChooser implements BeatmapFileChooser {
    @Override
    public void chooseFile(java.util.function.Consumer<Path> onSelected) {
        EventQueue.invokeLater(() -> {
            FileDialog dialog = new FileDialog((Frame) null, "Import beatmap (.osz / .osu)", FileDialog.LOAD);
            dialog.setDirectory(System.getProperty("user.home"));
            dialog.setFilenameFilter(osuBeatmapFilter());
            try {
                dialog.setVisible(true);
                String fileName = dialog.getFile();
                String directory = dialog.getDirectory();
                if (fileName != null && directory != null) {
                    onSelected.accept(Path.of(directory, fileName));
                }
            } finally {
                dialog.dispose();
            }
        });
    }

    private FilenameFilter osuBeatmapFilter() {
        return (directory, name) -> {
            String lowerCaseName = name.toLowerCase(Locale.ROOT);
            return lowerCaseName.endsWith(".osz") || lowerCaseName.endsWith(".osu");
        };
    }
}
