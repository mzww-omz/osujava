package dev.osujava.ui;

import com.badlogic.gdx.audio.Music;
import dev.osujava.beatmap.*;
import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MainMenuMusicTest {
    private static final class Stream {
        int plays, pauses, stops, disposals, positionReads;
        boolean dead;
        float position;
        String failure = "";
        final Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(),new Class[]{Music.class},(proxy,method,args) -> {
            assertFalse(dead,"Disposed stream accessed: " + method.getName());
            if (method.getName().equals(failure)) throw new IllegalStateException("broken stream");
            return switch (method.getName()) {
                case "play" -> { plays++; yield null; }
                case "pause" -> { pauses++; yield null; }
                case "stop" -> { stops++; yield null; }
                case "dispose" -> { disposals++; dead = true; yield null; }
                case "getPosition" -> { positionReads++; yield position; }
                default -> null;
            };
        });
        MenuAmbientAudio audio(Path path) { return new MenuAmbientAudio(path,p -> music); }
    }
    private static BeatmapSet set(String name, Path audio) {
        var difficulty = new BeatmapDifficulty(name,"Artist " + name,"Mapper","Normal",0,"","",
                new DifficultySettings(5,5,5,5,1.4,1),List.of(),List.of(),null,null);
        return new BeatmapSet(name,name,difficulty.artist(),"Mapper",audio,null,List.of(difficulty),List.of());
    }
    @Test void pauseFreezesTrackClockAndResumeKeepsSameStreamAndPosition() {
        var stream = new Stream(); var audio = stream.audio(Path.of("track.ogg"));
        audio.enter(); stream.position = 1.25f; audio.togglePause();
        assertTrue(audio.available()); assertTrue(audio.paused()); assertEquals(1,stream.pauses);
        int reads = stream.positionReads;
        stream.position = 9; audio.advance(500,0); assertEquals(1250,audio.positionMs());
        assertEquals(reads,stream.positionReads); assertEquals(0,stream.disposals);
        stream.position = 1.25f; audio.togglePause();
        assertFalse(audio.paused()); assertEquals(2,stream.plays); assertEquals(1250,audio.positionMs());
        stream.position = 1.5f; assertEquals(1500,audio.positionMs());
        audio.close(); audio.close(); assertEquals(1,stream.disposals); assertEquals(1,stream.stops);
        audio.togglePause(); assertFalse(audio.paused());
    }
    @Test void pauseAndResumeFailuresReleaseStreamAndLeaveControlsUnavailable() {
        for (String failure : List.of("pause","play")) {
            var stream = new Stream(); var audio = stream.audio(Path.of("track.ogg")); audio.enter();
            if (failure.equals("play")) audio.togglePause();
            stream.failure = failure; audio.togglePause();
            assertFalse(audio.available()); assertFalse(audio.paused());
            audio.close(); assertEquals(1,stream.disposals);
        }
    }
    @Test void previousNextWrapInLibraryOrderDisposeOldAudioAndRetainPausedState() {
        var a = set("A",Path.of("a.ogg")); var b = set("B",Path.of("b.ogg")); var c = set("C",Path.of("c.ogg"));
        var streams = new ArrayList<Stream>(); var paths = new ArrayList<Path>();
        var player = new MenuMusicPlayer(List.of(a,b,c),b,path -> {
            var stream = new Stream(); streams.add(stream); paths.add(path); return stream.audio(path);
        });
        player.enter(); assertEquals("B",player.current().difficulty().title());
        assertTrue(player.skip(1)); assertEquals("C",player.current().difficulty().title());
        assertEquals(1,streams.getFirst().disposals); assertEquals(1,streams.get(1).plays);
        player.togglePause(); assertTrue(player.paused());
        assertTrue(player.skip(1)); assertEquals("A",player.current().difficulty().title());
        assertEquals(1,streams.get(1).disposals); assertEquals(0,streams.get(2).plays);
        assertTrue(player.paused()); assertEquals(0,player.positionMs());
        player.togglePause(); assertEquals(1,streams.get(2).plays); assertFalse(player.paused());
        assertTrue(player.skip(-1)); assertEquals("C",player.current().difficulty().title());
        assertEquals(List.of(Path.of("b.ogg"),Path.of("c.ogg"),Path.of("a.ogg"),Path.of("c.ogg")),paths);
        player.close(); player.close();
        for (var stream : streams) assertEquals(1,stream.disposals);
    }
    @Test void emptySingleAndBrokenTrackQueuesRemainSafe() {
        var empty = new MenuMusicPlayer(List.of(),null,MenuAmbientAudio::new);
        empty.enter(); assertNull(empty.current()); assertFalse(empty.skip(1)); empty.togglePause(); empty.close();
        var a = set("A",null); var single = new MenuMusicPlayer(List.of(a),a,MenuAmbientAudio::new);
        single.enter(); assertFalse(single.canSkip()); assertFalse(single.skip(-1)); assertFalse(single.available()); single.close();
        var b = set("B",Path.of("bad.ogg"));
        var broken = new MenuMusicPlayer(List.of(a,b),b,path -> new MenuAmbientAudio(path,p -> { throw new IllegalArgumentException(); }));
        broken.enter(); assertFalse(broken.available()); assertTrue(broken.canSkip());
        broken.skip(1); assertEquals("A",broken.current().difficulty().title());
        assertFalse(broken.available()); broken.togglePause(); assertFalse(broken.paused()); broken.close();
    }
    @Test void controlsShareDrawHitBoundsAndRespectDisabledAndOutgoingStates() {
        var active = new MainMenuFrame.Info(2,0,"18:20","",true,false,true);
        var paused = new MainMenuFrame.Info(2,0,"18:20","",false,true,true);
        var unavailable = new MainMenuFrame.Info(2,0,"18:20","",false,false,true);
        var single = new MainMenuFrame.Info(1,0,"18:20","",true,false,false);
        for (int[] size : new int[][]{{1024,768},{1280,720},{1920,1080},{600,800}}) {
            var m = MainMenuFrame.layout(UiLayout.fromPixels(size[0],size[1]));
            assertTrue(m.trackWidth() > 98 * m.unit());
            for (int control = 0; control < 3; control++) {
                float x = MainMenuFrame.controlX(m,control) + 13 * m.unit(), y = MainMenuFrame.controlY(m) + 13 * m.unit();
                assertEquals(control,MainMenuFrame.controlAt(m,x,y,active,false));
                assertEquals(control,MainMenuFrame.controlAt(m,x,y,paused,false));
                assertEquals(-1,MainMenuFrame.controlAt(m,x,y,active,true));
                assertEquals(control == 1 ? -1 : control,MainMenuFrame.controlAt(m,x,y,unavailable,false));
                assertEquals(control == 1 ? 1 : -1,MainMenuFrame.controlAt(m,x,y,single,false));
                assertEquals(-1,MainMenuFrame.controlAt(m,x,y - 27 * m.unit(),active,false));
            }
        }
    }
}
