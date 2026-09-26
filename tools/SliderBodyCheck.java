import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.osujava.*;
import dev.osujava.beatmap.*;
import dev.osujava.gameplay.*;
import dev.osujava.ruleset.osu.*;
import dev.osujava.ruleset.osu.render.*;
import dev.osujava.skin.*;
import dev.osujava.ui.*;
import java.nio.file.*;
import java.util.*;

/** Optional real-OpenGL regression harness; see docs/slider-body-parity.md for commands. */
public class SliderBodyCheck extends OsuJavaGame {
    static final Path ROOT=Path.of("/private/tmp/osujava-slider-body-check");
    static final Color ACCENT=new Color(.2f,.4f,.8f,.03f);
    final Matrix4 projection=new Matrix4().setToOrtho2D(0,0,1024,768);
    final PlayfieldViewport viewport=PlayfieldViewport.fit(1024,768);
    SliderBodyRenderer body;
    FrameBuffer target;
    long clock;
    public SliderBodyCheck(){super(callback->{},null);}
    public static void main(String[] args){
        Lwjgl3ApplicationConfiguration.useGlfwAsync();
        var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1024,768);
        config.setInitialVisible(false);config.setTitle("Slider Body fixed clock QA");
        new Lwjgl3Application(new SliderBodyCheck(),config);
    }
    static List<BeatmapPoint> points(double...xy){
        var list=new ArrayList<BeatmapPoint>();
        for(int i=0;i<xy.length;i+=2) list.add(new BeatmapPoint(xy[i],xy[i+1]));return list;
    }
    @Override public void create(){
        try{
            super.create();batch().setProjectionMatrix(projection);shapes().setProjectionMatrix(projection);
            target=new FrameBuffer(Pixmap.Format.RGBA8888,1024,768,false);body=new SliderBodyRenderer();
            var straight=points(70,192,440,192);
            var split=points(70,192,150,192,220,192,310,192,440,192);
            var duplicate=points(70,192,440,192,70,192,70,192,440,192);
            // Test logical projection / doubled framebuffer (HiDPI), then normal target reuse.
            target.dispose();target=new FrameBuffer(Pixmap.Format.RGBA8888,2048,1536,false);
            var hidpi=direct("body-hidpi",straight,1,Color.WHITE,ACCENT,1);
            if(hidpi.getPixel(1600,768)==hidpi.getPixel(100,768))throw new AssertionError("HiDPI bounds clipped the body");
            hidpi.dispose();target.dispose();target=new FrameBuffer(Pixmap.Format.RGBA8888,1024,768,false);
            var single=direct("body-straight",straight,1,Color.WHITE,ACCENT,1);
            var segmented=direct("body-segmented",split,1,Color.WHITE,ACCENT,1);
            var overlap=direct("body-backtracking",duplicate,1,Color.WHITE,ACCENT,1);
            compare(single,segmented,1,"straight vs segmented");compare(single,overlap,1,"straight vs backtracking");
            single.dispose();segmented.dispose();overlap.dispose();
            var partial=direct("body-snake-half",split,.5,Color.WHITE,ACCENT,.5f);
            var endpoint=direct("body-snake-reference",points(70,192,255,192),1,Color.WHITE,ACCENT,.5f);
            compare(partial,endpoint,1,"partial snake round cap");partial.dispose();endpoint.dispose();
            var zero=direct("body-snake-zero",split,0,Color.WHITE,ACCENT,1);
            var empty=direct("body-empty",List.of(),1,Color.WHITE,ACCENT,1);
            compare(zero,empty,0,"zero snake");zero.dispose();empty.dispose();
            var bend=direct("body-right-angle",points(100,270,300,270,300,100),1,Color.WHITE,ACCENT,1);bend.dispose();
            var acute=direct("body-acute",points(100,270,380,270,125,245),1,Color.WHITE,ACCENT,1);acute.dispose();
            var shortBody=direct("body-short",points(250,192,250.01,192),1,Color.WHITE,ACCENT,1);shortBody.dispose();
            for(var entry:Map.of("default","", "border","SliderBorder: 255,80,160", "track","SliderTrackOverride: 30,220,80").entrySet()){
                Path skin=ROOT.resolve(entry.getKey());java.nio.file.Files.createDirectories(skin);
                java.nio.file.Files.writeString(skin.resolve("skin.ini"),"[General]\nVersion: 2.7\n[Colours]\n"+entry.getValue()+"\n");
                trySkin(entry.getKey(),skin);
            }
            trySkin("no-skin",null);
            if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("PASS: all fixed-clock/HiDPI captures; segmented/backtracking/partial/zero GPU pixel regressions; GL_NO_ERROR");
            body.dispose();target.dispose();System.exit(0);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
    Pixmap direct(String name,List<BeatmapPoint> points,double snake,Color border,Color source,float alpha){
        var geometry=new SliderBodyGeometry(points);
        target.begin();Gdx.gl.glClearColor(.1f,.1f,.1f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if(!points.isEmpty()){
            var mesh=SliderBodyRenderer.mesh(geometry);
            var track=LegacySliderColour.track(SkinConfiguration.Colours.defaults(),source);
            body.draw(mesh,geometry,snake,32,border,track,alpha,viewport,projection,batch());mesh.dispose();
        }
        var image=ScreenUtils.getFrameBufferPixmap(0,0,target.getWidth(),target.getHeight());
        PixmapIO.writePNG(Gdx.files.absolute(ROOT.resolve(name+".png").toString()),image,-1,true);
        target.end();System.out.println("Captured "+name);return image;
    }
    void compare(Pixmap a,Pixmap b,int tolerance,String name){
        int max=0,bad=0;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){
            int av=a.getPixel(x,y),bv=b.getPixel(x,y);
            for(int shift:new int[]{8,16,24}){int d=Math.abs(((av>>>shift)&255)-((bv>>>shift)&255));max=Math.max(max,d);if(d>tolerance)bad++;}
        }
        if(bad>0)throw new AssertionError(name+" changed "+bad+" channels, max="+max);
        System.out.println("PASS "+name+" max RGB delta="+max);
    }
    void trySkin(String name,Path skin){
        var assets=skin==null?null:new OsuSkinAssets(skin);
        var renderer=new GameplayRenderer(this,GameplayVisualConfig.defaults(),assets);
        for(String shape:List.of("straight","curved","sharp","short","long","combo")){
            var controls=switch(shape){
                case "curved"->points(80,260,250,20,420,260);
                case "sharp"->points(80,270,400,270,100,230);
                case "short"->points(250,192,251,192);
                case "long"->points(50,90,450,90,450,290,50,290);
                default->points(80,192,420,192);
            };
            var type=shape.equals("curved")?SliderData.CurveType.BEZIER:SliderData.CurveType.LINEAR;
            var sd=new SliderData(List.of(new SliderData.Segment(type,0,controls)),0,0);
            var object=new HitObject(controls.getFirst().x(),controls.getFirst().y(),1000,HitObject.Type.SLIDER,6,0,sd);
            var objects=new ArrayList<HitObject>();
            if(shape.equals("combo"))objects.add(new HitObject(30,350,0,HitObject.Type.CIRCLE,5,0));
            objects.add(object);
            var map=new BeatmapDifficulty("Body check","QA","QA",shape,0,"","",
                new DifficultySettings(5,5,5,5,1.4,1),List.of(new TimingPoint(0,500,4,0,0,100,true,0)),objects,null,null);
            for(long time:new long[]{0,990}){
                clock=time;
                var session=new OsuGameplaySession(map,()->clock,new JudgementWindows(50,100,150));
                var state=session.update();
                var set=new BeatmapSet("qa",map.title(),map.artist(),map.creator(),null,null,List.of(map),List.of());
                target.begin();renderer.render(set,map,state,viewport,null,name+" "+shape);
                var image=ScreenUtils.getFrameBufferPixmap(0,0,1024,768);
                PixmapIO.writePNG(Gdx.files.absolute(ROOT.resolve(name+"-"+shape+"-"+time+".png").toString()),image,-1,true);
                image.dispose();target.end();System.out.println("Captured "+name+" "+shape+" "+time);
            }
        }
        renderer.dispose();if(assets!=null)assets.dispose();
    }
}
