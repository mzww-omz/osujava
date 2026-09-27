package dev.osujava.ui;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Screen-local queue in Library order: one representative difficulty per set, wrapping at either end. */
final class MenuMusicPlayer implements AutoCloseable {
    record Track(BeatmapSet set, BeatmapDifficulty difficulty) {
        Path audioPath() { return difficulty.audioPath() != null ? difficulty.audioPath() : set.audioPath(); }
        String title() { return difficulty.artist() + " — " + difficulty.title(); }
    }
    private final List<Track> tracks = new ArrayList<>();
    private final Function<Path, MenuAmbientAudio> factory;
    private int index = -1;
    private MenuAmbientAudio audio;
    MenuMusicPlayer(List<BeatmapSet> sets, BeatmapSet initial, Function<Path, MenuAmbientAudio> factory) {
        this.factory = factory;
        for (var set : sets) {
            // Retain initial metadata even when supplied separately from the Library snapshot.
            var track = track(initial != null && initial.id().equals(set.id()) ? initial : set);
            if (initial != null && initial.id().equals(set.id())) index = tracks.size();
            tracks.add(track);
        }
        if (initial != null && index < 0) {
            index = tracks.size(); tracks.add(track(initial));
        }
        audio = factory.apply(current() == null ? null : current().audioPath());
    }
    private static Track track(BeatmapSet set) {
        var difficulty = set.difficulties().stream()
                .filter(d -> d.backgroundPath() != null && Files.isRegularFile(d.backgroundPath()))
                .findFirst().orElse(set.difficulties().getFirst());
        return new Track(set,difficulty);
    }
    Track current() { return index < 0 ? null : tracks.get(index); }
    boolean canSkip() { return tracks.size() > 1 || index < 0 && !tracks.isEmpty(); }
    boolean skip(int direction) {
        if (!canSkip()) return false;
        boolean paused = audio.paused();
        audio.close();
        index = index < 0 ? (direction < 0 ? tracks.size() - 1 : 0) : Math.floorMod(index + direction,tracks.size());
        audio = factory.apply(current().audioPath()); audio.enter(paused);
        return true;
    }
    void enter() { audio.enter(); }
    void togglePause() { audio.togglePause(); }
    boolean available() { return audio.available(); }
    boolean paused() { return audio.paused(); }
    double positionMs() { return audio.positionMs(); }
    void advance(double ms, float fade) { audio.advance(ms,fade); }
    @Override public void close() { audio.close(); }
}
