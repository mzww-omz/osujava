package dev.osujava.ui;

import com.badlogic.gdx.audio.Music;
import dev.osujava.audio.AudioVolumes;
import dev.osujava.beatmap.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectPreviewTest {
    private static class Stream {
        float position, volume;
        int plays, stops, disposals, seeks;
        boolean broken;
        final Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class[]{Music.class},
                (proxy, method, args) -> {
                    assertEquals(0, disposals, "access after disposal");
                    if (broken && method.getName().equals("getPosition")) throw new IllegalStateException();
                    return switch (method.getName()) {
                        case "play" -> { plays++; yield null; }
                        case "stop" -> { stops++; yield null; }
                        case "dispose" -> { disposals++; yield null; }
                        case "setPosition" -> { position = (float) args[0]; seeks++; yield null; }
                        case "getPosition" -> position;
                        case "setVolume" -> { volume = (float) args[0]; yield null; }
                        default -> null;
                    };
                });
    }
    @Test void seeksOnceContinuesSharedAudioAndDisposesOnSelectionOrExit() {
        var streams = new ArrayList<Stream>();
        var volumes = new AudioVolumes();
        var preview = new SongSelectPreview(path -> { var s = new Stream(); streams.add(s); return s.music; }, volumes);
        preview.select(Path.of("a.ogg"), 12345);
        var first = streams.getFirst();
        assertEquals(12.345f, first.position, .0001f);
        first.position = 14;
        preview.select(Path.of("a.ogg"), 4000);
        preview.advance(.25f);
        assertEquals(1, streams.size()); assertEquals(1, first.seeks);
        assertEquals(14000, preview.positionMs()); assertEquals(.75f, first.volume);
        volumes.set(AudioVolumes.Channel.MUSIC, .5f); assertEquals(.375f, first.volume);
        preview.select(Path.of("b.ogg"), -1);
        assertEquals(1, first.disposals); assertEquals(1, first.stops);
        assertEquals(0, streams.getLast().seeks);
        preview.close(); preview.close();
        volumes.set(AudioVolumes.Channel.MUSIC, 1);
        assertEquals(1, streams.getLast().disposals);
        assertFalse(preview.available());
        preview.select(Path.of("b.ogg"), 500);
        assertEquals(3, streams.size()); preview.close();
    }
    @Test void brokenAudioFailsOncePerSelectionAndRemainsNavigable() {
        var stream = new Stream(); int[] loads = {0};
        var preview = new SongSelectPreview(path -> { loads[0]++; return stream.music; }, new AudioVolumes());
        preview.select(Path.of("broken.ogg"), 0); stream.broken = true;
        preview.advance(0); preview.select(Path.of("broken.ogg"), 0);
        assertEquals(1, loads[0]); assertFalse(preview.available()); assertEquals(1, stream.disposals);
        preview.select(null, -1); preview.close();
    }
    @Test void beatUsesTimingOffsetAndBpmChangesAndIgnoresInheritedPoints() {
        var difficulty = new BeatmapDifficulty("", "", "", "", 0, "", "", null, List.of(
                new TimingPoint(100, 500, 4, 1, 0, 100, true, 0),
                new TimingPoint(500, -50, 4, 1, 0, 100, false, 0),
                new TimingPoint(1100, 250, 4, 1, 0, 100, true, 0)), List.of(), null, null);
        assertEquals(1, SongSelectDecorations.beat(.1, difficulty), 1e-6);
        assertEquals(0, SongSelectDecorations.beat(.35, difficulty), 1e-6);
        assertEquals(1, SongSelectDecorations.beat(.6, difficulty), 1e-6);
        assertEquals(1, SongSelectDecorations.beat(1.35, difficulty), 1e-6);
        assertEquals(0, SongSelectDecorations.beat(1.475, difficulty), 1e-6);
    }
}
