package dev.osujava.difficulty;

import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.HitObject;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Pure NM calculation, independently implemented against the pinned 20220902 public reference.
 * v6+ circles/spinners and verified v8+ Linear sliders are accepted.
 * See docs/songselect-backend-difficulty-20261003.md for numerical scope and provenance. */
public final class StandardDifficultyCalculator {
    public static final String ALGORITHM_VERSION = "osu-java-nm-20220902-2";
    public static final String PREPROCESS_VERSION = "linear-slider-f32-v8-1";
    public static final int MAX_OBJECTS = 20_000;
    private static final int MAX_STACK_COMPARISONS = 2_000_000;
    private static final long MAX_SPAN_MS = 6 * 60 * 60 * 1000L;
    private static final double RATING_SCALE = .0675;

    public DifficultyResult calculate(BeatmapDifficulty chart) { return inspect(chart,false).result(); }
    /** Diagnostics are allocated only for tests/reference inspection, never by the production worker. */
    public Inspection inspect(BeatmapDifficulty chart) { return inspect(chart,true); }
    public record ObjectFacts(double strainTime, double distance, double angle, double aim, double speed, double rhythm, double travelDistance, double travelTime, double minimumJumpDistance, double minimumJumpTime) { }
    public record Inspection(DifficultyResult result, List<Integer> heights, List<ObjectFacts> objects,
                             List<Double> aimPeaks, List<Double> speedPeaks) { }
    private static Inspection rejected(DifficultyResult result) { return new Inspection(result,List.of(),List.of(),List.of(),List.of()); }

    private Inspection inspect(BeatmapDifficulty chart, boolean diagnostics) {
        if (chart.mode()!=0) return rejected(DifficultyResult.unsupported("Only osu!standard NM is supported"));
        if (chart.settings().formatVersion()<6) return rejected(DifficultyResult.unsupported("Pre-v6 stacking is not verified"));
        var settings=chart.settings();
        for(double value : new double[]{settings.circleSize(),settings.approachRate(),settings.overallDifficulty(),settings.stackLeniency()})
            if(!Double.isFinite(value)) return rejected(DifficultyResult.failed("Non-finite difficulty setting"));
        if(settings.circleSize()<0 || settings.circleSize()>10 || settings.approachRate()<0 || settings.approachRate()>10
                || settings.overallDifficulty()<0 || settings.overallDifficulty()>10 || settings.stackLeniency()<0 || settings.stackLeniency()>1)
            return rejected(DifficultyResult.unsupported("Difficulty settings outside verified NM range"));
        var source=chart.hitObjects(); int count=source.size();
        if(count>MAX_OBJECTS) return rejected(DifficultyResult.unsupported("Object limit exceeded"));
        for(int i=0;i<count;i++) {
            var object=source.get(i);
            if(object.type()!=HitObject.Type.CIRCLE && object.type()!=HitObject.Type.SPINNER && object.type()!=HitObject.Type.SLIDER)
                return rejected(DifficultyResult.unsupported("Other object preprocessing is not verified"));
            if(!Double.isFinite(object.x()) || !Double.isFinite(object.y()) || Math.abs(object.x())>100_000 || Math.abs(object.y())>100_000
                    || Math.abs((double)object.timeMs())>Integer.MAX_VALUE || !Double.isFinite(object.endTimeMs()))
                return rejected(DifficultyResult.failed("Invalid hit object geometry or time"));
            if(i>0 && object.timeMs()<source.get(i-1).timeMs()) return rejected(DifficultyResult.failed("Hit objects are not time ordered"));
        }
        if(count>1 && source.getLast().timeMs()-source.getFirst().timeMs()>MAX_SPAN_MS)
            return rejected(DifficultyResult.unsupported("Duration limit exceeded"));
        checkCancelled();
        float cs=(float)settings.circleSize(), ar=(float)settings.approachRate();
        float scale=(1f-.7f*(cs-5)/5)/2;
        float radius=64*scale, distanceScale=50/radius;
        if(radius<30) distanceScale*=1+Math.min(30-radius,5)/50;
        float preempt=(float)(ar>5 ? 1200-150*(ar-5) : 1800-120*ar);
        // Public reference uses a float product for the stack threshold and float positions.
        float threshold=preempt*(float)settings.stackLeniency();
        LinearSliderPreprocessing.Slider[] sliders;
        try { sliders=LinearSliderPreprocessing.prepare(chart); }
        catch(LinearSliderPreprocessing.Unsupported e) { return rejected(DifficultyResult.unsupported(e.getMessage())); }
        int[] heights=new int[count]; float[] x=new float[count], y=new float[count];
        for(int i=0;i<count;i++) { x[i]=(float)source.get(i).x(); y[i]=(float)source.get(i).y(); }
        float[] endX=x.clone(),endY=y.clone();double[] endTime=new double[count];
        for(int i=0;i<count;i++) {
            endTime[i]=source.get(i).endTimeMs();
            if(sliders[i]!=null) {endX[i]+=sliders[i].endRelative.x();endY[i]+=sliders[i].endRelative.y();endTime[i]=sliders[i].end;}
        }
        int comparisons=0;
        for(int i=count-1;i>0;i--) {
            if(heights[i]!=0 || spinner(source,i)) continue;
            boolean circle=sliders[i]==null;int anchor=i;
            for(int j=i-1;j>=0;j--) {
                if((++comparisons&255)==0) checkCancelled();
                if(comparisons>MAX_STACK_COMPARISONS) return rejected(DifficultyResult.unsupported("Stacking work limit exceeded"));
                if(spinner(source,j)) continue;
                if(source.get(anchor).timeMs()-(circle?endTime[j]:source.get(j).timeMs())>threshold) break;
                if(circle && sliders[j]!=null && length(endX[j]-x[anchor],endY[j]-y[anchor])<3) {
                    int offset=heights[anchor]-heights[j]+1;
                    for(int k=j+1;k<=i;k++) {
                        if((++comparisons&255)==0) checkCancelled();
                        if(comparisons>MAX_STACK_COMPARISONS) return rejected(DifficultyResult.unsupported("Stacking work limit exceeded"));
                        if(length(endX[j]-x[k],endY[j]-y[k])<3) heights[k]-=offset;
                    }
                    break;
                }
                if(length((circle?x[j]:endX[j])-x[anchor],(circle?y[j]:endY[j])-y[anchor])<3) { heights[j]=heights[anchor]+1; anchor=j; }
            }
        }
        for(int i=0;i<count;i++) { float offset=heights[i]*scale*-6.4f; x[i]+=offset; y[i]+=offset; }
        float[] cursorX=x.clone(),cursorY=y.clone();
        for(int i=0;i<count;i++) if(sliders[i]!=null) {
            checkCancelled();sliders[i].cursor(heights[i]*scale*-6.4f,radius);
            cursorX[i]=sliders[i].lazy.x();cursorY[i]=sliders[i].lazy.y();
        }
        // All difficulties are float settings in the reference beatmap model.
        double window=2*(80-6*(float)settings.overallDifficulty());
        var objects=new Note[Math.max(0,count-1)];
        for(int i=1;i<count;i++) {
            int index=i-1; var n=new Note(index,source.get(i).timeMs(),source.get(i).timeMs()-source.get(i-1).timeMs(),spinner(source,i),spinner(source,i)?0:window);
            if(sliders[i]!=null) {
                n.slider=true;n.travelDistance=sliders[i].lazyDistance*(float)Math.pow(1+sliders[i].repeats/2.5,1/2.5);
                n.travelTime=Math.max(25,sliders[i].lazyTime);
            }
            if(!spinner(source,i) && !spinner(source,i-1)) {
                n.distance=length(x[i]*distanceScale-cursorX[i-1]*distanceScale,y[i]*distanceScale-cursorY[i-1]*distanceScale);
                n.minimumJump=n.distance;n.minimumTime=n.strainTime;
                if(sliders[i-1]!=null) {
                    n.minimumTime=Math.max(25,n.strainTime-Math.max(25,sliders[i-1].lazyTime));
                    var tail=sliders[i-1].nestedPosition(sliders[i-1].endRelative,heights[i-1]*scale*-6.4f);
                    float tailJump=length(tail.x()-x[i],tail.y()-y[i])*distanceScale;
                    n.minimumJump=Math.max(0,Math.min(n.distance-(LinearSliderPreprocessing.MAX_SLIDER_RADIUS-LinearSliderPreprocessing.ASSUMED_SLIDER_RADIUS),tailJump-LinearSliderPreprocessing.MAX_SLIDER_RADIUS));
                }
                if(i>1 && !spinner(source,i-2)) {
                    float ax=cursorX[i-2]-x[i-1],ay=cursorY[i-2]-y[i-1],bx=x[i]-cursorX[i-1],by=y[i]-cursorY[i-1];
                    float dot=ax*bx+ay*by, determinant=ax*by-ay*bx;
                    n.angle=Math.abs(Math.atan2(determinant,dot));
                }
            }
            objects[index]=n;
        }
        var aimSkill=new Sections(.15,23.55,10,1.06,false);
        var speedSkill=new Sections(.3,1375,5,1.04,true);
        var facts=diagnostics ? new ArrayList<ObjectFacts>(objects.length) : null;
        for(int i=0;i<objects.length;i++) {
            if((i&255)==0) checkCancelled();
            Note n=objects[i]; double aim=aim(objects,i),speed=speed(objects,i),rhythm=rhythm(objects,i);
            aimSkill.add(n,aim,1); speedSkill.add(n,speed,rhythm);
            if(diagnostics) facts.add(new ObjectFacts(n.strainTime,n.distance,Double.isNaN(n.angle)?-1:n.angle,aim,speed,rhythm,n.travelDistance,n.travelTime,n.minimumJump,n.minimumTime));
        }
        double aim=Math.sqrt(aimSkill.difficulty())*RATING_SCALE, speed=Math.sqrt(speedSkill.difficulty())*RATING_SCALE;
        double combined=Math.pow(Math.pow(performance(aim),1.1)+Math.pow(performance(speed),1.1),1/1.1);
        double stars=count==0 ? 0 : combined>.00001 ? Math.cbrt(1.14)*.027*(Math.cbrt(100000/Math.pow(2,1/1.1)*combined)+4) : 0;
        if(!Double.isFinite(stars) || !Double.isFinite(aim) || !Double.isFinite(speed))
            return rejected(DifficultyResult.failed("Non-finite strain result"));
        var result=new DifficultyResult(DifficultyResult.Status.SUCCESS,stars,aim,speed,"");
        return diagnostics ? new Inspection(result,Arrays.stream(heights).boxed().toList(),List.copyOf(facts),aimSkill.peaks(),speedSkill.peaks()) : rejected(result);
    }
    private static boolean spinner(List<HitObject> objects,int index) { return objects.get(index).type()==HitObject.Type.SPINNER; }
    private static void checkCancelled() { if(Thread.currentThread().isInterrupted()) throw new CancellationException(); }
    private static float length(float x,float y) { return (float)Math.sqrt(x*x+y*y); }
    private static double performance(double rating) { return Math.pow(5*Math.max(1,rating/RATING_SCALE)-4,3)/100000; }
    private static double square(double x) { return x*x; }
    private static double clamp(double value,double low,double high) { return Math.max(low,Math.min(high,value)); }
    private static double wide(double angle) { return square(Math.sin(.75*(clamp(angle,Math.PI/6,Math.PI*5/6)-Math.PI/6))); }
    private static final class Note {
        final int index; final double time,delta,strainTime,window; final boolean spinner;
        boolean slider;
        double distance,angle=Double.NaN,travelDistance,travelTime,minimumJump,minimumTime;
        Note(int index,double time,double delta,boolean spinner,double window) {
            this.index=index;this.time=time;this.delta=delta;this.spinner=spinner;this.window=window;strainTime=Math.max(25,delta);
        }
    }
    private static double aim(Note[] notes,int index) {
        Note n=notes[index];
        if(n.spinner || index<=1 || notes[index-1].spinner) return 0;
        Note p=notes[index-1],pp=notes[index-2];
        double v=n.distance/n.strainTime,previous=p.distance/p.strainTime,wideBonus=0,acuteBonus=0,change=0;
        if(p.slider) v=Math.max(v,p.travelDistance/p.travelTime+n.minimumJump/n.minimumTime);
        if(pp.slider) previous=Math.max(previous,pp.travelDistance/pp.travelTime+p.minimumJump/p.minimumTime);
        double strain=v;
        if(Math.max(n.strainTime,p.strainTime)<1.25*Math.min(n.strainTime,p.strainTime)
                && !Double.isNaN(n.angle) && !Double.isNaN(p.angle) && !Double.isNaN(pp.angle)) {
            double angleVelocity=Math.min(v,previous);
            double w=wide(n.angle),a=1-w;
            if(n.strainTime<=100)
                acuteBonus=a*(1-wide(p.angle))*Math.min(angleVelocity,125/n.strainTime)
                        *square(Math.sin(Math.PI/2*Math.min(1,(100-n.strainTime)/25)))
                        *square(Math.sin(Math.PI/2*(clamp(n.distance,50,100)-50)/50));
            wideBonus=w*angleVelocity*(1-Math.min(w,Math.pow(wide(p.angle),3)));
            acuteBonus*=.5+.5*(1-Math.min(acuteBonus,Math.pow(1-wide(pp.angle),3)));
        }
        if(Math.max(v,previous)!=0) {
            previous=(p.distance+pp.travelDistance)/p.strainTime;
            v=(n.distance+p.travelDistance)/n.strainTime;
            double difference=Math.abs(previous-v);
            change=Math.min(125/Math.min(n.strainTime,p.strainTime),difference)
                    *square(Math.sin(Math.PI/2*difference/Math.max(previous,v)))
                    *square(Math.min(n.strainTime,p.strainTime)/Math.max(n.strainTime,p.strainTime));
        }
        return strain+Math.max(1.95*acuteBonus,1.5*wideBonus+.75*change)+(p.slider?1.35*p.travelDistance/p.travelTime:0);
    }
    private static double speed(Note[] notes,int index) {
        Note n=notes[index]; if(n.spinner) return 0;
        double doubletap=1;
        if(index+1<notes.length) {
            double delta=Math.max(1,n.delta),next=Math.max(1,notes[index+1].delta);
            double ratio=delta/Math.max(delta,Math.abs(next-delta));
            doubletap=Math.pow(ratio,1-square(Math.min(1,delta/n.window)));
        }
        double time=n.strainTime/clamp(n.strainTime/n.window/.93,.92,1);
        double bonus=time<75 ? 1+.75*square((75-time)/40) : 1;
        return bonus*(1+Math.pow(Math.min(125,n.minimumJump+(index>0?notes[index-1].travelDistance:0))/125,3.5))*doubletap/time;
    }
    private static double rhythm(Note[] notes,int index) {
        Note n=notes[index]; if(n.spinner) return 0;
        int count=Math.min(index,32),start=0;
        while(start<count-2 && n.time-notes[index-start-1].time<5000) start++;
        double total=0,entryRatio=0; int island=1,previousIsland=0; boolean counting=false;
        for(int back=start;back>0;back--) {
            Note c=notes[index-back],p=notes[index-back-1],pp=notes[index-back-2];
            double decay=Math.min((double)(count-back)/count,(5000-(n.time-c.time))/5000);
            double current=c.strainTime,previous=p.strainTime;
            double ratio=1+6*Math.min(.5,square(Math.sin(Math.PI/(Math.min(previous,current)/Math.max(previous,current)))));
            double penalty=Math.min(1,Math.max(0,Math.abs(previous-current)-c.window*.3)/(c.window*.3));
            double effective=penalty*ratio;
            boolean changed=previous>1.25*current || previous*1.25<current;
            if(counting && !changed) island=Math.min(7,island+1);
            else if(counting) {
                if(c.slider) effective*=.125;
                if(p.slider) effective*=.25;
                if(previousIsland==island) effective*=.25;
                if(previousIsland%2==island%2) effective*=.5;
                if(pp.strainTime>previous+10 && previous>current+10) effective*=.125;
                total+=Math.sqrt(effective*entryRatio)*decay*Math.sqrt(4+island)/2*Math.sqrt(4+previousIsland)/2;
                entryRatio=effective;previousIsland=island;island=1;
                if(previous*1.25<current) counting=false;
            } else if(previous>1.25*current) { counting=true;entryRatio=effective;island=1; }
        }
        return Math.sqrt(4+total*.75)/2;
    }
    private static final class Sections {
        private final double decay,multiplier,finalMultiplier;
        private final int reduce; private final boolean tapping;
        private final List<Double> complete=new ArrayList<>();
        private double end,peak,strain,rhythm=1,previousTime;
        Sections(double decay,double multiplier,int reduce,double finalMultiplier,boolean tapping) {
            this.decay=decay;this.multiplier=multiplier;this.reduce=reduce;this.finalMultiplier=finalMultiplier;this.tapping=tapping;
        }
        void add(Note n,double value,double rhythm) {
            if(n.index==0) end=Math.ceil(n.time/400)*400;
            while(n.time>end) {
                complete.add(peak);
                peak=strain*this.rhythm*Math.pow(decay,(end-previousTime)/1000);end+=400;
            }
            strain=strain*Math.pow(decay,(tapping?n.strainTime:n.delta)/1000)+value*multiplier;
            this.rhythm=rhythm;peak=Math.max(peak,strain*rhythm);previousTime=n.time;
        }
        List<Double> peaks() { var values=new ArrayList<>(complete);values.add(peak);return List.copyOf(values); }
        double difficulty() {
            var values=new ArrayList<Double>();for(double p:complete) if(p>0) values.add(p);if(peak>0) values.add(peak);
            values.sort(Comparator.reverseOrder());
            for(int i=0;i<Math.min(reduce,values.size());i++) {
                // The pinned reference deliberately uses float division for peak reduction.
                double amount=Math.log10(1+9*(double)((float)i/reduce));
                values.set(i,values.get(i)*(.75+.25*amount));
            }
            values.sort(Comparator.reverseOrder());double sum=0,weight=1;
            for(double p:values) { sum+=p*weight;weight*=.9; }
            return sum*finalMultiplier;
        }
    }
}
