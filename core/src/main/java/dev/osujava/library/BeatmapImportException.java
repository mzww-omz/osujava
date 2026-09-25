package dev.osujava.library;

public final class BeatmapImportException extends Exception {
    public BeatmapImportException(String message) {
        super(message);
    }

    public BeatmapImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
