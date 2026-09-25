package dev.osujava.lwjgl3;

import dev.osujava.ui.BeatmapFileChooser;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Path;

public final class DesktopFileChooser implements BeatmapFileChooser {
    @Override
    public void chooseFile(java.util.function.Consumer<Path> onSelected) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Import beatmap (.osz / .osu)");
            chooser.setFileFilter(new FileNameExtensionFilter("osu! beatmaps (*.osz, *.osu)", "osz", "osu"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                onSelected.accept(chooser.getSelectedFile().toPath());
            }
        });
    }
}
