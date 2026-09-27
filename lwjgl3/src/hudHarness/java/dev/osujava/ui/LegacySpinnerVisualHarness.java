package dev.osujava.ui;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.*;
import dev.osujava.gameplay.*;
import dev.osujava.ruleset.osu.OsuGameplaySession;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.SkinImporter;
import static dev.osujava.skin.OsuSkinAssets.Image.*;
import static dev.osujava.ruleset.osu.render.LegacySpinnerAnimation.*;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Actual GameplayRenderer, procedural transparent fixtures, absolute clock snapshots and input replay. */
public final class LegacySpinnerVisualHarness extends ApplicationAdapter {
    private record Scenario(String name, Path skin, double progress, long now, long completed, int width, int height, boolean replay) { }
    private final List<Scenario> scenes = new ArrayList<>();
    private record Check(int metreHeight, int density, boolean rpm, boolean old, Path fallback) { }
    private final Map<Path, Check> checks = new HashMap<>();
    private final Map<String, Long> metreCounts = new HashMap<>();
    private final Map<Path, Float> slideTops = new HashMap<>();
    private static final int[] BARS = {0xffe6194b,0xff3cb44b,0xffffe119,0xff4363d8,0xfff58231,
            0xff911eb4,0xff46f0f0,0xfff032e6,0xffbcf60c,0xfffabebe};
    private static final int RPM = 0xfff125b1;
    private static final int TOP = 0xff177d97, BOTTOM = 0xff9d6e13, LEFT = 0xff195d2d, RIGHT = 0xff7149a1;
    private final Path output;
    private SpriteBatch batch; private ShapeRenderer shapes; private BitmapFont font;
    private OsuJavaGame game; private OsuSkinAssets assets; private GameplayRenderer renderer;
    private int index; private boolean resizing; private final List<String> results = new ArrayList<>();
    private LegacySpinnerVisualHarness(Path output) { this.output = output; }
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config = new Lwjgl3ApplicationConfiguration(); config.setTitle("Legacy Spinner fixed clock captures");
        config.setWindowedMode(1024, 768); config.setForegroundFPS(30);
        new Lwjgl3Application(new LegacySpinnerVisualHarness(Path.of(args[0])), config);
    }
    @Override public void create() {
        batch = new SpriteBatch(); shapes = new ShapeRenderer(); font = new BitmapFont();
        game = new OsuJavaGame(null) {
            @Override public BitmapFont font() { return font; }
            @Override public SpriteBatch batch() { return batch; }
            @Override public ShapeRenderer shapes() { return shapes; }
        };
        try {
            Files.createDirectories(output);
            Path oldBlink = fixture("old-blink", true, true, false, 1, false);
            Path oldNoBlink = fixture("old-no-blink", true, true, true, 1, false);
            Path modern = fixture("new-middle2", false, true, false, 1, false);
            Path noMiddle = fixture("new-no-middle2", false, false, false, 1, false);
            Path dense = fixture("new-2x", false, true, false, 2, false);
            Path partial = fixture("old-root-only", true, false, false, 1, true);
            for (Path skin : List.of(oldBlink, oldNoBlink, modern, noMiddle)) {
                String prefix = skin.getFileName().toString();
                for (double p : new double[]{0, .25, .5, .9, 1}) add(prefix + "-p" + (int)(p * 100), skin, p, 3000, p == 1 ? 2900 : Long.MIN_VALUE);
                for (long t : new long[]{400, 600, 800, 900, 1000, 2999, 3000, 3120, 3240, 3320, 3400, 4600, 4800, 4975, 5000, 5120, 5240})
                    add(prefix + "-t" + t, skin, t < 3000 ? .9 : 1, t, 3000);
                add(prefix + "-bonus-flash", skin, 1, 3600, 3000);
                add(prefix + "-maximum-bonus", skin, 1, 4200, 3000);
                scenes.add(new Scenario(prefix + "-input-replay", skin, 0, 3800, Long.MIN_VALUE, 1024, 768, true));
            }
            Path both = fixture("old-and-new-roots", true, true, true, 1, false);
            image(both, "spinner-top", 512, 512, 1, 0xff00ffff, "MUST NOT DRAW");
            add("background-wins", both, .5, 3000, Long.MIN_VALUE);
            Path newPartial = fixture("new-root-only", false, false, false, 1, true);
            add("new-missing-subpieces", newPartial, .5, 3000, Long.MIN_VALUE);
            add("density-2x", dense, .5, 3000, Long.MIN_VALUE);
            add("missing-subpieces", partial, .5, 3000, Long.MIN_VALUE);
            add("fallback-vector", null, .5, 3000, Long.MIN_VALUE);
            scenes.add(new Scenario("wide-16-9", modern, .5, 3000, Long.MIN_VALUE, 1280, 720, false));
            scenes.add(new Scenario("tall-aspect", oldBlink, .25, 3000, Long.MIN_VALUE, 600, 800, false));
            // Dedicated opaque palette pixels on transparent bodies, with native 1024-wide metre.
            for (int[] size : new int[][]{{692,1},{692,2},{400,1},{900,1}}) {
                Path qa = pixelFixture("qa-metre-" + size[0] + "-" + size[1], size[0], size[1], true, null);
                for (double p : new double[]{0,.1,.2,.5,.9,1}) add(qa.getFileName() + "-p" + (int)(p*100), qa, p, 3000, Long.MIN_VALUE);
                for (long t : new long[]{600,700,800,1000}) add(qa.getFileName() + "-slide-" + t, qa, .5, t, Long.MIN_VALUE);
                scenes.add(new Scenario(qa.getFileName()+"-session",qa,0,3800,Long.MIN_VALUE,1024,768,true));
                scenes.add(new Scenario(qa.getFileName()+"-wide",qa,.5,3000,Long.MIN_VALUE,1280,720,false));
                scenes.add(new Scenario(qa.getFileName()+"-tall",qa,.5,3000,Long.MIN_VALUE,600,800,false));
            }
            Path provider = pixelFixture("qa-provider",692,2,true,null);
            Path missing = pixelFixture("qa-missing",692,1,false,null);
            add("qa-missing-rpm",missing,.5,3000,Long.MIN_VALUE);
            Path supplemented = pixelFixture("qa-supplemented",692,1,false,provider);
            Files.delete(supplemented.resolve("spinner-metre.png"));
            checks.put(supplemented,new Check(692,2,true,true,provider));
            add("qa-fallback-rpm",supplemented,.5,3000,Long.MIN_VALUE);
            Path broken = pixelFixture("qa-broken",692,1,true,null);
            Files.writeString(broken.resolve("spinner-metre.png"),"broken PNG");
            checks.put(broken,new Check(0,1,true,true,null));
            add("qa-broken-metre",broken,.5,3000,Long.MIN_VALUE);
            Path brokenRpm = pixelFixture("qa-broken-rpm",692,1,true,null);
            Files.writeString(brokenRpm.resolve("spinner-rpm.png"),"broken PNG");
            checks.put(brokenRpm,new Check(692,1,false,true,null));
            add("qa-broken-rpm",brokenRpm,.5,3000,Long.MIN_VALUE);
            // Explicitly import the reported real skin; gameplay still reads only the local directory.
            String reportedArchive = System.getProperty("osujava.spinnerReportedSkinArchive");
            if (reportedArchive != null) {
                Path realArchive = Path.of(reportedArchive);
                Path real = new SkinImporter(output.resolve("real-skins")).importFile(realArchive);
                checks.put(real,new Check(0,2,false,false,null));
                scenes.add(new Scenario("real-default-session",real,0,3800,Long.MIN_VALUE,1024,768,true));
                Path supplementedReal = new SkinImporter(output.resolve("real-skins-fallback")).importFile(realArchive);
                checks.put(supplementedReal,new Check(0,2,true,false,provider));
                scenes.add(new Scenario("real-default-fallback-session",supplementedReal,0,3800,Long.MIN_VALUE,1024,768,true));
            }
            select();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private void add(String name, Path skin, double progress, long now, long complete) {
        scenes.add(new Scenario(name, skin, progress, now, complete, 1024, 768, false));
    }
    private Path fixture(String name, boolean old, boolean middle2, boolean noBlink, int density, boolean partial) throws Exception {
        Path path = output.resolve("fixtures/" + name); Files.createDirectories(path);
        Files.writeString(path.resolve("skin.ini"), "[General]\nSpinnerNoBlink: " + (noBlink ? 1 : 0) + "\n[Colours]\nSpinnerBackground: 100,130,170\n");
        if (old) image(path, "spinner-background", 1024, 768, density, 0x88707070, "BACKGROUND");
        else image(path, "spinner-top", 512, 512, density, 0xbbe8df81, "TOP");
        if (partial) return path;
        if (old) {
            image(path, "spinner-circle", 512, 512, density, 0xaaf0f0f0, "CIRCLE");
            image(path, "spinner-metre", 100, 692, density, 0xdd67e090, "METRE");
        } else {
            image(path, "spinner-bottom", 550, 550, density, 0x999ccfe0, "BOTTOM");
            image(path, "spinner-glow", 600, 600, density, 0x447fffff, "GLOW");
            if (middle2) image(path, "spinner-middle2", 210, 210, density, 0xbb9cd894, "MIDDLE2");
            image(path, "spinner-middle", 110, 110, density, 0xffeeeeee, "MIDDLE");
        }
        image(path, "spinner-approachcircle", 512, 512, density, 0xbbffffff, "APPROACH");
        image(path, "spinner-spin", 250, 70, density, 0xffeeeeee, "SPIN!");
        image(path, "spinner-clear", 250, 70, density, 0xffeeeeee, "CLEAR!");
        image(path, "spinner-rpm", 280, 50, density, 0xaa333366, "RPM");
        for (int n = 0; n < 10; n++) image(path, "score-" + n, n == 1 ? 16 : 24, 32, density, 0xffeeeeee, "" + n);
        return path;
    }
    private Path pixelFixture(String name, int h, int density, boolean rpm, Path fallback) throws Exception {
        Path path = output.resolve("fixtures/"+name); Files.createDirectories(path);
        Files.writeString(path.resolve("skin.ini"),"[General]\nSpinnerNoBlink: 1\n");
        ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),"png",path.resolve("spinner-background.png").toFile());
        BufferedImage metre = new BufferedImage(1024*density,h*density,BufferedImage.TYPE_INT_ARGB);
        var g = metre.createGraphics(); g.scale(density,density);
        for (int n=0;n<10;n++) {
            g.setColor(new java.awt.Color(BARS[n],true));
            int y=n*h/10, bottom=(n+1)*h/10;
            g.fillRect(0,y,80,bottom-y); g.fillRect(944,y,80,bottom-y);
        }
        g.setColor(new java.awt.Color(TOP,true));g.fillRect(0,0,1024,6);
        g.setColor(new java.awt.Color(BOTTOM,true));g.fillRect(0,h-6,1024,6);
        g.setColor(new java.awt.Color(LEFT,true));g.fillRect(0,6,6,h-12);
        g.setColor(new java.awt.Color(RIGHT,true));g.fillRect(1018,6,6,h-12);
        g.dispose();ImageIO.write(metre,"png",path.resolve("spinner-metre"+(density==2?"@2x":"")+".png").toFile());
        if (rpm) {
            var frame = new BufferedImage(280*density,50*density,BufferedImage.TYPE_INT_ARGB);
            var fg=frame.createGraphics();fg.setColor(new java.awt.Color(RPM,true));fg.fillRect(0,0,frame.getWidth(),frame.getHeight());fg.dispose();
            ImageIO.write(frame,"png",path.resolve("spinner-rpm"+(density==2?"@2x":"")+".png").toFile());
        }
        for(int n=0;n<10;n++) image(path,"score-"+n,n==1?16:24,32,density,0xffeeeeee,""+n);
        checks.put(path,new Check(h,density,rpm || fallback!=null,true,fallback));return path;
    }
    private void image(Path path, String name, int w, int h, int density, int argb, String label) throws Exception {
        BufferedImage image = new BufferedImage(w * density, h * density, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); g.scale(density, density); g.setColor(new java.awt.Color(argb, true));
        g.setStroke(new BasicStroke(8));
        if (name.equals("spinner-metre")) {
            for (int n = 0; n < 10; n++) g.fillRect(4, n * 69 + 5, 90, 60);
        } else if (w == h) {
            g.drawOval(8, 8, w - 16, h - 16); g.drawLine(w / 2, h / 2, w - 15, h / 2);
        } else if (name.equals("spinner-background")) g.fillRect(0, 0, w, h);
        else if (name.equals("spinner-rpm")) g.fillRoundRect(0, 0, w, h, 12, 12);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.min(28, h - 4)));
        g.drawString(label, Math.max(0, (w - g.getFontMetrics().stringWidth(label)) / 2), h / 2 + 10);
        g.dispose(); ImageIO.write(image, "png", path.resolve(name + (density == 2 ? "@2x" : "") + ".png").toFile());
    }
    private void select() {
        if (renderer != null) renderer.dispose(); if (assets != null) assets.dispose();
        var scene = scenes.get(index); assets = new OsuSkinAssets(scene.skin, checks.containsKey(scene.skin) ? checks.get(scene.skin).fallback : null); renderer = new GameplayRenderer(game, GameplayVisualConfig.defaults(), assets);
        resizing = Gdx.graphics.getWidth() != scene.width || Gdx.graphics.getHeight() != scene.height;
        if (resizing) Gdx.graphics.setWindowedMode(scene.width, scene.height);
    }
    @Override public void render() {
        var scene = scenes.get(index);
        if (resizing) { resizing = false; return; }
        var state = snapshot(scene);
        draw(state); byte[] first = capture(scene.name + ".png");
        // Change time and progress, then seek/replay the exact snapshot. Real replay uses fresh Session/Input APIs.
        draw(new GameplayState(900, List.of(), List.of(), List.of(), state.score(), false));
        draw(snapshot(scene)); byte[] again = capture(null);
        if (!Arrays.equals(first, again)) throw new AssertionError("Pixel mismatch after seek/input replay: " + scene.name);
        results.add(scene.name + " pixels=identical logical=" + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
                + " framebuffer=" + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight());
        if (checks.containsKey(scene.skin)) assertPieces(scene, state, first);
        if (++index == scenes.size()) {
            try { Files.write(output.resolve("results.txt"), results); } catch (Exception e) { throw new RuntimeException(e); }
            Gdx.app.exit(); return;
        }
        select();
    }
    private GameplayState snapshot(Scenario scene) {
        if (scene.replay) {
            long[] time = {1000}; GameClock clock = () -> time[0];
            var hit = new HitObject(256, 192, 1000, HitObject.Type.SPINNER, 8, 0, null, new SpinnerData(5000));
            var diff = new BeatmapDifficulty("Spinner", "Harness", "fixture", "fixed", 0, "", "",
                    new DifficultySettings(5, 5, 0, 9, 1.4, 1), List.of(), List.of(hit), null, null);
            var session = new OsuGameplaySession(diff, clock, new JudgementWindows(50, 100, 150));
            session.press(GameInputAction.LEFT, 336, 192);
            for (int n = 1; n <= 112; n++) {
                time[0] = 1000 + 25 * n; double angle = n * Math.PI / 2;
                session.pointerMoved(256 + 80 * Math.cos(angle), 192 + 80 * Math.sin(angle)); session.update();
            }
            var state = session.state();
            if (state.spinners().size()!=1 || state.spinners().getFirst().progress()<=0 || state.spinners().getFirst().spinsPerMinute()<=0)
                throw new AssertionError("Real Gameplay spinner progress/SPM missing");
            return state;
        }
        var events = List.of(new SpinnerVisual.SpinEvent(3600, 50, false, true), new SpinnerVisual.SpinEvent(4200, 100, true, true));
        var s = new SpinnerVisual(256, 192, 140, scene.progress, (scene.now - 1000) * .19, 3600 * scene.progress,
                (int)(10 * scene.progress), 10, 1000, 5000, true, scene.now >= 5000 ? Judgement.HIT300 : null,
                600, 345.999, scene.completed, 100, 0, events);
        var score = new ScoreState(0, 0, 0, 0, 0, 0, 0, 1);
        return new GameplayState(scene.now, List.of(), List.of(), List.of(s), score, false);
    }
    private void draw(GameplayState state) {
        int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        var projection = new Matrix4().setToOrtho2D(0, 0, width, height);
        batch.setProjectionMatrix(projection); shapes.setProjectionMatrix(projection);
        renderer.render(null, null, state, PlayfieldViewport.fit(width, height), null, "");
    }
    private void assertPieces(Scenario scene, GameplayState state, byte[] fullFrame) {
        Check check = checks.get(scene.skin); SpinnerVisual spinner = state.spinners().getFirst();
        boolean broken = scene.name.equals("qa-broken-metre");
        if (assets.diagnostic(SPINNER_METRE).status() != (broken ? OsuSkinAssets.LoadStatus.FAILED
                : check.old ? OsuSkinAssets.LoadStatus.LOADED : check.fallback==null ? OsuSkinAssets.LoadStatus.MISSING : OsuSkinAssets.LoadStatus.LOADED))
            throw new AssertionError("Metre diagnostic: " + scene.name);
        if (assets.diagnostic(SPINNER_RPM).status() != (scene.name.equals("qa-broken-rpm") ? OsuSkinAssets.LoadStatus.FAILED : check.rpm ? OsuSkinAssets.LoadStatus.LOADED : OsuSkinAssets.LoadStatus.MISSING))
            throw new AssertionError("RPM diagnostic: " + scene.name);
        if (assets.spinnerStyle() != (check.old ? Style.OLD : Style.NEW)) throw new AssertionError("Provider style changed");
        if (check.old && check.metreHeight>0 && assets.get(SPINNER_METRE).density()!=check.density)throw new AssertionError("Metre density");
        if (check.rpm && assets.get(SPINNER_RPM).density()!=(check.fallback!=null ? 2 : check.density))throw new AssertionError("RPM density");
        if (check.rpm && (assets.get(SPINNER_RPM).logicalWidth()!=280 || assets.get(SPINNER_RPM).logicalHeight()!=50))
            throw new AssertionError("RPM native logical size stretched");
        // Isolate pieces using the exact same real-session snapshot and renderer as GameplayRenderer.
        Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();new GameplaySpinnerRenderer(assets).draw(batch,spinner,state.currentTimeMs(),scene.width,scene.height);batch.end();
        Pixmap pixels=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        try {
            var c=LegacySpinnerCoordinates.fit(scene.width,scene.height);
            float dx=(float)pixels.getWidth()/scene.width, dy=(float)pixels.getHeight()/scene.height;
            float alpha=(float)wholeAlpha(spinner,state.currentTimeMs());
            long metreCount=0,rpmCount=0;int rpmMinX=pixels.getWidth(),rpmMaxX=-1,rpmMinY=pixels.getHeight(),rpmMaxY=-1;
            int[] bandCounts=new int[10];
            for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++) {
                int pixel=pixels.getPixel(x,y);
                for(int n=0;n<10;n++) if(matches(pixel,BARS[n],1)){metreCount++;bandCounts[n]++;break;}
                if(alpha>0 && matches(pixel,RPM,alpha)) {rpmCount++;rpmMinX=Math.min(rpmMinX,x);rpmMaxX=Math.max(rpmMaxX,x);rpmMinY=Math.min(rpmMinY,y);rpmMaxY=Math.max(rpmMaxY,y);}
            }
            if(check.old && check.metreHeight>0 && state.currentTimeMs()>=1000) {
                double cut=METRE_HEIGHT-metreBars(spinner.progress(),true,state.currentTimeMs(),spinner.beatmapIndex())/10.0*METRE_HEIGHT;
                for(int n=0;n<10;n++) {
                    double textureY=(n+.5)*check.metreHeight/10;
                    assertSample(pixels,c,dx,dy,40,textureY,textureY*SPRITE_SCALE>=cut ? BARS[n] : 0xff000000,scene.name+" band "+n);
                }
                for(double[] marker : new double[][]{{120,3,TOP},{120,check.metreHeight-3,BOTTOM},{3,check.metreHeight*.65,LEFT},{1021,check.metreHeight*.65,RIGHT}})
                    assertSample(pixels,c,dx,dy,marker[0],marker[1],marker[1]*SPRITE_SCALE>=cut ? (int)marker[2] : 0xff000000,scene.name+" marker");
                long fullMetre = countPalette(fullFrame, BARS, 1);
                if(metreCount>0 && fullMetre==0)throw new AssertionError("Full GameplayRenderer lost metre");
                if(check.metreHeight==692 && scene.progress==0 && !scene.replay && fullMetre!=0)throw new AssertionError("0% full framebuffer has metre pixels");
                if(check.metreHeight==692 && !scene.replay && scene.width==1024) {
                    if(scene.progress==0 && metreCount!=0)throw new AssertionError("0% has metre pixels");
                    String key=scene.skin.toString();
                    if(scene.progress==.5)metreCounts.put(key,metreCount);
                    if(scene.progress==1) {
                        Long half=metreCounts.get(key);
                        if(half==null || metreCount<=half || Math.abs((double)half/metreCount-.5)>.03)throw new AssertionError("50/100% pixel coverage");
                        for(int count:bandCounts)if(count==0)throw new AssertionError("100% missing a band");
                    }
                }
            } else if(check.old && check.metreHeight==0 && metreCount!=0)throw new AssertionError("Missing/broken metre drew pixels");
            double offset=spmOffset(spinner,state.currentTimeMs());
            float expectedTop=c.y(445+offset)*dy;
            boolean visible=check.rpm && alpha>0 && expectedTop>1;
            if(visible) {
                if(rpmCount==0)throw new AssertionError("Only SPM digits; no RPM frame: "+scene.name);
                near(rpmMinX,c.x(233)*dx,2,"RPM left");near(rpmMaxX,(c.x(233)+c.length(280*SPRITE_SCALE))*dx-1,2,"RPM right");
                near(rpmMaxY,Math.min(pixels.getHeight(),expectedTop)-1,2,"RPM top");
                near(rpmMinY,Math.max(0,expectedTop-c.length(50*SPRITE_SCALE)*dy),2,"RPM bottom");
                if(scene.name.contains("-slide-")) {
                    Float previous=slideTops.put(scene.skin,expectedTop);
                    if(previous!=null && expectedTop<=previous)throw new AssertionError("RPM slide did not move upwards on screen");
                }
                if(state.currentTimeMs()>=1000) {
                    int digits=0;
                    for(int y=Math.max(0,(int)((c.y(448+offset)-c.length(32*SPRITE_SCALE*.9))*dy));y<Math.min(pixels.getHeight(),(int)(c.y(448+offset)*dy));y++)
                        for(int x=(int)(c.x(340)*dx);x<(int)(c.x(400)*dx);x++)if(check.old ? matches(pixels.getPixel(x,y),0xffeeeeee,alpha) : !matches(pixels.getPixel(x,y),RPM,alpha))digits++;
                    if(digits==0)throw new AssertionError("RPM frame has no simultaneous SPM digits");
                    long fullRpm=countPalette(fullFrame,new int[]{RPM},alpha);
                    if(fullRpm==0)throw new AssertionError("Full GameplayRenderer lost RPM frame");
                }
            } else if(rpmCount!=0)throw new AssertionError("Hidden/missing RPM drew pixels");
            if(check.old && !check.rpm && state.currentTimeMs()>=1000) {
                int digits=0;
                for(int y=(int)((c.y(448)-c.length(32*SPRITE_SCALE*.9))*dy);y<(int)(c.y(448)*dy);y++)
                    for(int x=(int)(c.x(340)*dx);x<(int)(c.x(400)*dx);x++)if(matches(pixels.getPixel(x,y),0xffeeeeee,alpha))digits++;
                if(digits==0)throw new AssertionError("Missing/broken RPM must preserve SPM digits");
            }
            PixmapIO.writePNG(Gdx.files.absolute(output.resolve(scene.name+"-pieces.png").toString()),pixels,-1,true);
            results.add("ASSERT "+scene.name+" metrePixels="+metreCount+" rpmPixels="+rpmCount+" progress="+spinner.progress()+" spm="+spinner.spinsPerMinute()
                    +" offset="+offset+" metre="+assets.diagnostic(SPINNER_METRE)+" rpm="+assets.diagnostic(SPINNER_RPM));
        } finally {pixels.dispose();}
    }
    private static long countPalette(byte[] frame,int[] palette,float alpha) {
        long count=0;
        for(int i=0;i<frame.length;i+=4) {
            int rgba=(frame[i]&255)<<24|(frame[i+1]&255)<<16|(frame[i+2]&255)<<8|(frame[i+3]&255);
            for(int color:palette)if(matches(rgba,color,alpha)){count++;break;}
        }
        return count;
    }
    private static void assertSample(Pixmap pixels, LegacySpinnerCoordinates c, float dx, float dy, double textureX, double textureY,int argb,String label) {
        int x=(int)(c.x(textureX*SPRITE_SCALE)*dx),y=(int)(c.y(TOP_OFFSET+textureY*SPRITE_SCALE)*dy);
        if(x<0||y<0||x>=pixels.getWidth()||y>=pixels.getHeight())return; // native tall textures clip at the screen
        if(!matches(pixels.getPixel(x,y),argb,1))throw new AssertionError(label+" expected="+Integer.toHexString(argb)+" actual="+Integer.toHexString(pixels.getPixel(x,y)));
    }
    private static boolean matches(int rgba,int argb,float alpha) {
        for(int n=0;n<3;n++)if(Math.abs(((rgba>>>(24-8*n))&255)-((argb>>>(16-8*n))&255)*alpha)>2)return false;
        return true;
    }
    private static void near(float actual,float expected,float tolerance,String label) {
        if(Math.abs(actual-expected)>tolerance)throw new AssertionError(label+" actual="+actual+" expected="+expected);
    }
    private byte[] capture(String name) {
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            byte[] pixels = new byte[pixmap.getPixels().remaining()]; pixmap.getPixels().duplicate().get(pixels);
            if (name != null) PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name).toString()), pixmap, -1, true);
            return pixels;
        } finally { pixmap.dispose(); }
    }
    @Override public void dispose() { renderer.dispose(); assets.dispose(); batch.dispose(); shapes.dispose(); font.dispose(); }
}
