package dev.osujava.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import dev.osujava.beatmap.TimingPoint;
import dev.osujava.ui.theme.UiLayout;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class MainMenuTest {
    private static TimingPoint point(double time, double length, boolean red) { return new TimingPoint(time,length,4,0,0,100,red,0); }
    private static final MenuAudioAnalysis SILENT = new DeterministicMenuAudioFallback();
    private static final MenuBeatTiming.Beat BEAT = MenuBeatTiming.at(List.of(), 0, false);
    private static void advance(MainMenuModel model, double ms) { model.advance(ms, BEAT, SILENT); }

    @Test void informationFrameFadesInTwoHundredMsAndReversesContinuously() {
        var model = new MainMenuModel(); assertEquals(0, model.frameEmphasis());
        model.toggle(); advance(model, 100); assertEquals(.5f, model.frameEmphasis());
        model.toggle(); assertEquals(.5f, model.frameEmphasis());
        advance(model, 100); assertEquals(.25f, model.frameEmphasis());
        advance(model, 100); assertEquals(0, model.frameEmphasis());
        model.toggle(); advance(model, 200); assertEquals(1, model.frameEmphasis());
        assertEquals(MainMenuState.OPENING, model.state());
        for (var state : MainMenuState.values()) {
            var snapshot = new MainMenuModel(); snapshot.captureState(state, 100);
            assertEquals(switch (state) { case CLOSED -> 0; case OPEN -> 1; default -> .5f; }, snapshot.frameEmphasis());
        }
    }
    @Test void informationFrameRetainsLegibilityAndNegativeSpaceAtAllCaptureSizes() {
        for (int[] size : new int[][]{{1024,768},{1280,720},{1366,768},{1920,1080},{2560,1440},{600,800}}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var frame = MainMenuFrame.layout(ui); var menu = MainMenuLayout.from(ui);
            assertTrue(frame.topHeight() * ui.scale() >= 52);
            assertTrue(frame.bottomHeight() * ui.scale() >= 20);
            assertEquals(81 * Math.max(.95f,ui.scale()),frame.topHeight() * ui.scale(),.001);
            assertTrue(frame.trackWidth() * ui.scale() > 185);
            assertTrue(frame.centerX() > frame.pad() + frame.leftWidth());
            assertTrue(frame.trackX() > frame.centerX() + frame.centerWidth());
            assertTrue(MainMenuFrame.controlX(frame, 0) >= frame.trackX());
            assertTrue(MainMenuFrame.controlY(frame) >= frame.height() - frame.topHeight());
            // The larger stable cookie fits between the fixed HUD bands, including hover.
            float cookieRadius = menu.radius() * 1.15f;
            assertTrue(menu.cy() + cookieRadius < frame.height() - frame.topHeight());
            assertTrue(menu.cy() - cookieRadius > frame.bottomHeight());
        }
        assertEquals("00:00:00",MainMenuFrame.uptime(-1));
        assertEquals("01:00:00",MainMenuFrame.uptime(3600));
        assertEquals("100:01:01",MainMenuFrame.uptime(360061));
        assertEquals("99:59:59",MainMenuFrame.uptime(359999));
        assertFalse(MainMenuFrame.version().isBlank());
    }
    @Test void desktopChromeRetainsObservedStableAnchorsAcrossResolutions() {
        for (int[] size : new int[][]{{1280,720},{1366,768},{1920,1080},{2560,1440}}) {
            var ui = UiLayout.fromPixels(size[0],size[1]);
            var frame = MainMenuFrame.layout(ui);
            assertFalse(frame.compact());
            // Stable's 480-high canvas: general information at x=210; both bands 54 high.
            assertEquals(size[1] * 210f / 480, frame.centerX() * ui.scale(), .01);
            assertEquals(size[1] * 54f / 480, frame.topHeight() * ui.scale(), .01);
            assertEquals(frame.topHeight(), frame.bottomHeight());
            assertTrue(MainMenuFrame.avatarSize(frame) + 2 * frame.pad() < frame.topHeight());
        }
    }

    @Test void centerInformationFitsLargeLibraryAndLongSessionWithoutTruncation() {
        var metrics = new java.awt.font.FontRenderContext(null, true, true);
        for (int[] size : new int[][]{{1280,720},{1366,768},{1920,1080},{2560,1440},{600,800}}) {
            var frame = MainMenuFrame.layout(UiLayout.fromPixels(size[0], size[1]));
            // Match the smooth font's oversampled measurement, including its raster padding.
            var font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, Math.round(17 * MainMenuFrame.informationScale(frame) * 2));
            for (String line : List.of("12345 beatmaps available", "Session runtime  99:59:59", "Local time  23:59"))
                assertTrue(font.getStringBounds(line, metrics).getWidth() + 4 < frame.centerWidth() * 2,
                        () -> "Information would be truncated at " + size[0] + ": " + line);
        }
    }

    @Test void frameBpmUsesImportedTimingAndHidesUnknownValues() {
        assertEquals("",MainMenuFrame.bpm(List.of(),0));
        assertEquals("",MainMenuFrame.bpm(List.of(point(Double.NaN,500,true),point(0,-50,false)),0));
        var timing = List.of(point(0,500,true),point(2000,250,true));
        assertEquals("120 BPM",MainMenuFrame.bpm(timing,0));
        assertEquals("240 BPM",MainMenuFrame.bpm(timing,2000));
    }

    @Test void timingUsesCurrentUninheritedPointAndChangesBpmWithoutInheritedInterference() {
        var points = List.of(point(2000,250,true),point(100,500,true),point(1000,-50,false));
        assertEquals(500, MenuBeatTiming.at(points,0,true).lengthMs());
        assertEquals(500, MenuBeatTiming.at(points,1999,true).lengthMs());
        var beat = MenuBeatTiming.at(points,2125,true);
        assertEquals(250,beat.lengthMs()); assertEquals(.5,beat.phase()); assertEquals(2000,beat.originMs());
        assertEquals(1,MenuBeatTiming.at(points,2250,true).index());
        assertEquals(.5,MenuBeatTiming.at(points,350,true).phase());
        assertEquals(.9,MenuBeatTiming.at(points,50,true).phase(),.00001); // Before first point uses floor, not truncation.
        assertEquals(500,MenuBeatTiming.at(List.of(point(0,-20,true)),0,true).lengthMs());
        assertEquals(500,MenuBeatTiming.at(List.of(point(Double.NaN,200,true)),0,true).lengthMs());
    }
    @Test void onlyMissingAudioUsesSixtyBpmFallbackAndIgnoresMapTiming() {
        var beat = MenuBeatTiming.at(List.of(point(0,250,true)),1250,false);
        assertEquals(1000,beat.lengthMs()); assertEquals(.25,beat.phase());
        assertEquals(500,MenuBeatTiming.at(List.of(),1250,true).lengthMs());
    }
    @Test void stateMachineOpensClosesAndReversesWithoutJumping() {
        var m = new MainMenuModel(); assertEquals(MainMenuState.CLOSED,m.state()); assertEquals(0,m.reveal());
        m.toggle(); assertEquals(MainMenuState.OPENING,m.state()); assertEquals(0,m.reveal());
        advance(m,100); float scale=m.transitionScale(), reveal=m.reveal();
        m.toggle(); assertEquals(MainMenuState.CLOSING,m.state()); assertEquals(scale,m.transitionScale()); assertEquals(reveal,m.reveal());
        advance(m,300); assertEquals(MainMenuState.CLOSED,m.state()); assertEquals(1,m.transitionScale());
        m.toggle(); advance(m,200); assertEquals(.65f,m.transitionScale()); assertEquals(MainMenuState.OPENING,m.state());
        advance(m,180); assertEquals(MainMenuState.OPEN,m.state());
        m.toggle(); advance(m,300); assertEquals(MainMenuState.CLOSED,m.state()); assertEquals(0,m.reveal());
    }
    @Test void keysAndClicksNavigateOnceAndEscapeClosesBeforeExiting() {
        for (int key : new int[]{Input.Keys.P,Input.Keys.ENTER,Input.Keys.SPACE,Input.Keys.ESCAPE}) {
            var m=new MainMenuModel(); int[] calls=new int[2]; var input=new MainMenuInput(m,()->calls[0]++,()->calls[1]++);
            assertTrue(input.keyDown(key)); input.keyDown(Input.Keys.P); advance(m,199); assertEquals(0,calls[0]+calls[1]);
            advance(m,1); assertEquals(key==Input.Keys.ESCAPE?1:0,calls[1]); assertEquals(1,calls[0]+calls[1]);
        }
        var m=new MainMenuModel(); int[] calls=new int[2]; var input=new MainMenuInput(m,()->calls[0]++,()->calls[1]++);
        input.click(false,0); assertFalse(m.pending());
        input.click(true,-1); advance(m,380); assertEquals(MainMenuState.OPEN,m.state());
        input.keyDown(Input.Keys.ESCAPE); assertEquals(MainMenuState.CLOSING,m.state());
        input.keyDown(Input.Keys.ESCAPE); advance(m,300); assertEquals(0,calls[1]);
        input.keyDown(Input.Keys.ESCAPE); advance(m,200); assertEquals(1,calls[1]);
        for (int button=0;button<2;button++) {
            var open=new MainMenuModel(); open.toggle(); advance(open,380); int[] actions=new int[2];
            var pointer=new MainMenuInput(open,()->actions[0]++,()->actions[1]++);
            pointer.click(false,button); advance(open,100); assertTrue(open.explosion(button)>0);
            advance(open,100); assertEquals(1,actions[button]);
        }
    }
    @Test void visiblePolygonAndLogoHitboxesFollowAnimationAtAllSizesAndDensities() {
        for (int[] size:new int[][]{{1024,768},{1280,720},{1366,768},{1920,1080},{2560,1440},{600,800}}) {
            var layout=MainMenuLayout.from(UiLayout.fromPixels(size[0],size[1]));
            assertEquals(layout.width()/2,layout.cx()); assertEquals(layout.height()/2,layout.cy());
            assertEquals(layout,MainMenuLayout.from(UiLayout.fromPixels(size[0]*2,size[1]*2)));
            for(double time:new double[]{0,20,100,200,380}) {
                var m=new MainMenuModel(); m.toggle(); advance(m,time);
                assertTrue(layout.logoHit(layout.cx()+layout.radius()*m.scale()-.1f,layout.cy(),m.scale()));
                assertFalse(layout.logoHit(layout.cx()+layout.radius()*m.scale()+.1f,layout.cy(),m.scale()));
                assertEquals(-1,layout.buttonAt(layout.cx(),layout.cy(),m));
                for(int b=0;b<2;b++) {
                    float x=layout.outer(b,layout.cy(),m.reveal(),0,0), direction=layout.direction(b);
                    assertEquals(-1,layout.buttonAt(x+direction,layout.cy(),m));
                    if(m.reveal()>=.8f) assertEquals(b,layout.buttonAt(x-direction,layout.cy(),m));
                    else assertEquals(-1,layout.buttonAt(x-direction,layout.cy(),m));
                    assertTrue(Math.abs(x-layout.cx())<layout.width()/2);
                    assertTrue(layout.extent(1,1.38f,0)<layout.width()/2-10); // Elastic overshoot fits too.
                }
            }
            var closed=new MainMenuModel(); assertEquals(-1,layout.buttonAt(layout.cx()+250,layout.cy(),closed));
            closed.toggle();advance(closed,380);closed.toggle();assertEquals(-1,layout.buttonAt(layout.cx()+250,layout.cy(),closed));
        }
    }
    @Test void streamStartsOnEnterFadesStopsDisposesAndReenterCreatesFreshStream() {
        int[] created={0}, stopped={0}, disposed={0}, played={0}; float[] volume={0};
        var audio=new MenuAmbientAudio(Path.of("fixture.mp3"),path->{
            created[0]++; boolean[] dead={false};
            return (Music)Proxy.newProxyInstance(Music.class.getClassLoader(),new Class[]{Music.class},(proxy,method,args)->{
                assertFalse(dead[0],"Disposed Music accessed: "+method.getName());
                return switch(method.getName()) {
                    case "play"->{played[0]++;yield null;} case "getPosition"->1.25f;
                    case "setVolume"->{volume[0]=(float)args[0];yield null;}
                    case "stop"->{stopped[0]++;yield null;}
                    case "dispose"->{dead[0]=true;disposed[0]++;yield null;}
                    default->null;
                };
            });
        });
        assertFalse(audio.available());audio.enter();audio.enter();assertEquals(1,created[0]);assertEquals(1,played[0]);
        assertTrue(audio.available());assertEquals(1250,audio.positionMs());
        audio.advance(100,.5f);assertEquals(.325f,volume[0]);audio.advance(100,1);assertEquals(0,volume[0]);
        audio.close();audio.close();audio.advance(100,0);audio.positionMs();assertEquals(1,stopped[0]);assertEquals(1,disposed[0]);
        audio.enter();assertEquals(2,created[0]);audio.close();assertEquals(2,disposed[0]);
        var missing=new MenuAmbientAudio(null,path->{fail();return null;});missing.enter();missing.advance(250,0);
        assertEquals(250,missing.positionMs());assertFalse(missing.available());missing.close();
    }
    @Test void failedOpenPlayAndPositionReleaseSafelyWithoutCrashing() {
        var failed=new MenuAmbientAudio(Path.of("bad.ogg"),p->{throw new IllegalArgumentException();});
        failed.enter();assertFalse(failed.available());failed.advance(120,0);assertEquals(120,failed.positionMs());failed.close();
        for(String failure:List.of("play","getPosition","setVolume")) {
            int[] disposal={0};
            var audio=new MenuAmbientAudio(Path.of("bad.mp3"),p->(Music)Proxy.newProxyInstance(Music.class.getClassLoader(),new Class[]{Music.class},(proxy,method,args)->{
                if(method.getName().equals(failure))throw new IllegalStateException();
                if(method.getName().equals("dispose"))disposal[0]++;
                return null;
            }));
            audio.enter();audio.positionMs();audio.advance(100,1);audio.close();assertEquals(1,disposal[0]);
        }
    }
}
