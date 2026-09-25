package dev.osujava.beatmap.parse;

public final class BeatmapParseException extends Exception {
    public BeatmapParseException(String message) {
        super(message);
    }

    public BeatmapParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
