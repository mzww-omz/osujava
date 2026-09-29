package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import dev.osujava.gameplay.*;
import dev.osujava.score.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in real OpenGL ranking capture with synthetic local data; never imports a user's scores. */
public final class ResultsVisualHarness extends OsuJavaGame {
    private final Path output;
    private Path selectedSkin;
    private BeatmapSet set;
    private int fixture, frame;
    private long revision;
    private static final String[] CASES={"legacy-live","modern-live","saved-unknown","failed","partial-animation","accuracy-tooltip"};
    private ResultsVisualHarness(Path output) { super(null,null); this.output=output; }
    @Override public Path skinDirectory(){return selectedSkin;}
    public static void main(String[] args) {
        var config=new Lwjgl3ApplicationConfiguration();
        config.setTitle("osu!java local ranking verification");
        config.setWindowedMode(Integer.parseInt(args[1]),Integer.parseInt(args[2]));
        config.setForegroundFPS(60); config.disableAudio(true);
        new Lwjgl3Application(new ResultsVisualHarness(Path.of(args[0])),config);
    }
    @Override public void create() {
        super.create();
        try {
            Files.createDirectories(output.resolve("modern"));
            Files.writeString(output.resolve("modern/skin.ini"),"[General]\nVersion:2\n[Fonts]\nScoreOverlap:3\n");
            // Failed decode must fall back, independently of the selected layout version.
            Files.writeString(output.resolve("modern/ranking-panel@2x.png"),"invalid PNG fixture");
            var map=new BeatmapFileParser().parse("osu file format v14\n[Metadata]\nVersion:Local fixture\n[HitObjects]\n100,100,1000,1,0", "results.osu").difficulty();
            set=new BeatmapSet("results-visual-fixture","Results fixture","Local artist","Local creator",null,null,List.of(map),List.of());
            revision=localScores().revision();
            next();
        } catch(Exception e){throw new IllegalStateException(e);}
    }
    private void next() {
        selectedSkin=fixture==1 ? output.resolve("modern") : null;
        var score=new ScoreState(12345678,432,432,321,12,3,1,.9741);
        var details=new ScoreDetails(ScoreDetails.SCORE_V1,"","",24,3,432,fixture!=3,fixture!=3,
                List.of(new ScoreDetails.HealthPoint(0,1),new ScoreDetails.HealthPoint(1000,.85f),
                        new ScoreDetails.HealthPoint(2000,.4f),new ScoreDetails.HealthPoint(3000,.9f)),List.of(-10,0,10),null);
        var result=new ResultsSnapshot(score,fixture==2 ? ScoreDetails.LEGACY : details,1700000000000L,
                GameplayRunMode.MANUAL,fixture==2);
        navigate(new ResultsScreen(this,set,set.difficulties().getFirst(),result));
        if(fixture!=4) {
            Gdx.input.getInputProcessor().keyDown(Input.Keys.ENTER);
            Gdx.input.getInputProcessor().keyDown(Input.Keys.SPACE);
            if(!(getScreen() instanceof ResultsScreen)) throw new AssertionError("Skip navigated away");
        }
        Gdx.input.setCursorPosition(fixture==5 ? Gdx.graphics.getWidth()*260/854 : 0,
                fixture==5 ? Gdx.graphics.getHeight()*340/480 : 0);
        frame=0;
    }
    @Override public void render() {
        getScreen().render(fixture==4 ? .3f : 1/60f);
        if(++frame<3)return;
        Pixmap pixels=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        try { PixmapIO.writePNG(Gdx.files.absolute(output.resolve(CASES[fixture]+".png").toString()),pixels,-1,true); }
        finally {pixels.dispose();}
        if(localScores().revision()!=revision)throw new AssertionError("Viewing results saved a play");
        if(++fixture==CASES.length){System.out.println("RESULTS VISUAL CHECK: six cases rendered; skip stays on results; no writes");Gdx.app.exit();}
        else next();
    }
}
